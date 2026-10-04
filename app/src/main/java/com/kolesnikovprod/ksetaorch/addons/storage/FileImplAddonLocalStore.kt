package com.kolesnikovprod.ksetaorch.addons.storage

import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon
import com.kolesnikovprod.ksetaorch.addons.storage.contract.AddonLocalStore
import com.kolesnikovprod.ksetaorch.addons.storage.contract.InstalledAddonRecord
import dev.openksenax.addons.contract.AddonId
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Файловая реализация [AddonLocalStore] для app-specific storage OpenKsenax.
 *
 * Хранилище поддерживает локальную проекцию реально установленных аддонов,
 * сериализует [InstalledAddonRecord] в JSON, переносит проверенные временные
 * APK и banner-файлы в package-каталоги и удаляет устаревшие host-owned
 * артефакты.
 *
 * Все файловые операции выполняются на [ioDispatcher] и сериализуются через
 * внутренний [Mutex], поэтому один экземпляр store не выполняет несколько
 * reconciliation/read/remove операций одновременно.
 *
 * Финальные файлы создаются через промежуточные `.pending`-файлы с
 * последующей заменой destination, чтобы незавершённая запись не выглядела
 * как готовый артефакт.
 *
 * @property layout централизованная схема addon-файлов и проверка ownership.
 * @property json сериализатор локальных metadata.
 * @property currentTimeMillis источник времени для fallback timestamps
 * и очистки временных APK.
 * @property ioDispatcher dispatcher файловых операций.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal class FileAddonLocalStore(
    private val layout: AddonFileLayout,
    private val json  : Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
        prettyPrint       = true
    },
    private val currentTimeMillis: () -> Long          = System::currentTimeMillis,
    private val ioDispatcher     : CoroutineDispatcher = Dispatchers.IO,
) : AddonLocalStore {

    private val mutex = Mutex()

    /**
     * Синхронизирует локальное addon-хранилище с текущим состоянием registry.
     *
     * Создаёт необходимые директории, очищает незавершённые записи,
     * удаляет устаревшие временные APK, обновляет локальные metadata
     * установленных аддонов и при свежем discovery удаляет каталоги
     * отсутствующих packages.
     *
     * Все файловые операции выполняются на IO dispatcher.
     *
     * @param registryState актуальное состояние addon registry.
     * @return список локальных metadata-записей установленных аддонов.
     *
     * @since 0.3
     */
    override suspend fun reconcile(
        registryState: AddonRegistryState,
    ): List<InstalledAddonRecord> = withContext(ioDispatcher) {
        mutex.withLock {
            // создание базовых директорий
            layout.rootDirectory.mkdirs()
            layout.temporaryDirectory.mkdirs()

            clearInterruptedWrites()     // Очистка незавершённых записей (*.pending)
            clearExpiredTemporaryApks()  // Очистка временных старых APK

            val installedAddons = registryState.addons
                .filter { addon -> addon.installedMetadata != null }
            val duplicateLeaves = installedAddons
                .map { addon ->
                    requireNotNull(addon.installedMetadata).packageName
                }
                .groupBy(layout::packageLeaf)
                .filterValues { packages -> packages.size > 1 }
                .keys
            require(duplicateLeaves.isEmpty()) {
                "Installed addon storage collision: " +
                    duplicateLeaves.sorted().joinToString()
            }

            val records = installedAddons.map { addon ->
                reconcileInstalled(addon)
            }

            if (registryState.discoveryStatus == AddonRegistrySourceStatus.Fresh) {
                clearRemovedPackageDirectories(installedAddons)
            }

            records
        }
    }

    override suspend fun read(
        packageName: String,
    ): InstalledAddonRecord? = withContext(ioDispatcher) {
        mutex.withLock {
            readRecord(layout.installedMetadata(packageName))
                ?.takeIf { record -> record.packageName == packageName }
        }
    }

    override suspend fun remove(
        addonId: AddonId,
        packageName: String,
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            deleteOwnedRecursively(
                layout.packageDirectory(packageName),
            )
            listOf(
                layout.temporaryDirectory,
                layout.rootDirectory,
            )
                .flatMap { directory ->
                    directory.listFiles().orEmpty().asList()
                }
                .distinctBy(File::getCanonicalPath)
                .filter { file ->
                    layout.isDownloadArtifactFor(addonId, file)
                }
                .forEach { file ->
                    if (file.isDirectory) {
                        deleteOwnedRecursively(file)
                    } else {
                        deleteOwnedFile(file)
                    }
                }
        }
    }

    /**
     * Синхронизирует локальное представление одного установленного аддона.
     *
     * Метод использует состояние Android installation metadata как источник
     * фактов об установленном package и объединяет его с опубликованными
     * catalog metadata.
     *
     * Во время reconciliation:
     * - создаётся package-каталог;
     * - продвигается ранее скачанный APK из temporary storage;
     * - удаляется устаревшая локальная копия APK при внешнем обновлении;
     * - синхронизируется banner-артефакт;
     * - восстанавливаются timestamps;
     * - формируется и сохраняется локальная metadata-запись.
     *
     * Метод не устанавливает APK и не проверяет его подпись.
     * Он работает только после того, как registry уже подтвердил наличие
     * установленного Android package.
     *
     * @param addon зарегистрированный аддон с installation и catalog state.
     * @return актуальная локальная metadata-запись аддона.
     *
     * @since 0.3
     */
    private fun reconcileInstalled(
        addon: RegisteredAddon,
    ): InstalledAddonRecord {
        // Установленные метаданные
        val installedMetas = requireNotNull(addon.installedMetadata)
        val publishedCatalog = addon.catalogMetadata

        val packageDirectory = layout.packageDirectory(
            installedMetas.packageName, // создание директории аддона по его названию в пакете
        )
        packageDirectory.mkdirs()

        // Чтение старых метаданных
        val metadataFile = layout.installedMetadata(
            installedMetas.packageName,
        )
        val previous = readRecord(metadataFile) // читается старый metadata.json
            ?.takeIf { record ->
                // проверка на поломку
                record.addonId == addon.addonId.value &&
                    record.packageName == installedMetas.packageName
            }

        // Согласование APK
        val finalApk = layout.installedApk(installedMetas.packageName)
        val downloadedApk = layout.temporaryApk(
            addonId     = addon.addonId,
            versionCode = installedMetas.versionCode,
        )

        // Подтверждение от Android
        if (downloadedApk.isFile) {
            promote(downloadedApk, finalApk)
        } else if (
            previous != null &&
            previous.versionCode != installedMetas.versionCode
        ) {
            finalApk.delete()
        }

        // Опубликованный баннер (есть хэш)
        val publishedBannerSha256 = publishedCatalog?.bannerArtifact?.sha256
        // сохраняется предыдущий, если действительно существовал
        var bannerSha256 = previous?.bannerSha256
            ?.takeIf { previousSha256 ->
                layout.installedBanner(
                    packageName = installedMetas.packageName,
                    sha256      = previousSha256,
                ).isFile
            }

        // Ветка с новым баннером
        if (publishedBannerSha256 != null) {
            val finalBanner = layout.installedBanner(
                packageName = installedMetas.packageName,
                sha256      = publishedBannerSha256,
            )
            val temporaryBanner = layout.temporaryBanner(
                addonId = addon.addonId,
                sha256  = publishedBannerSha256,
            )
            // если финального нет, то записывается временный как финальный
            if (!finalBanner.isFile && temporaryBanner.isFile) {
                promote(temporaryBanner, finalBanner)
            }

            // очистка старого баннера
            if (finalBanner.isFile) {
                bannerSha256 = publishedBannerSha256
                clearObsoleteInstalledBanners(
                    packageDirectory = packageDirectory,
                    keep             = finalBanner,
                )
            }
        } else if (publishedCatalog != null) {
            // если баннера больше нет :(
            bannerSha256 = null
            clearObsoleteInstalledBanners(
                packageDirectory = packageDirectory,
                keep             = null,
            )
        }

        // Timestamp-восстановление
        val now = currentTimeMillis().coerceAtLeast(0L)

        /*
        * 1 - Android PackageManager
        * 2 - Старые метаданные
        * 3 - Текущее время
        * */
        val installedAt = installedMetas.firstInstallTimeEpochMillis
            .takeIf { value -> value > 0L }
            ?: previous?.installedAtEpochMillis
            ?: now
        val updatedAt = installedMetas.lastUpdateTimeEpochMillis
            .takeIf { value -> value > 0L }
            ?: previous?.lastUpdatedAtEpochMillis
            ?: installedAt

        val record = InstalledAddonRecord(
            addonId     = addon.addonId.value,
            packageName = installedMetas.packageName,
            displayName = publishedCatalog?.displayName // свежее?
                ?: previous?.displayName                // или предыдущее?
                ?: addon.displayName,                   // или fallback?
            shortDescription = publishedCatalog?.shortDescription
                ?: previous?.shortDescription.orEmpty(),
            fullDescription  = if (publishedCatalog != null) {
                publishedCatalog.fullDescription
            } else {
                previous?.fullDescription
            },
            versionCode              = installedMetas.versionCode,
            versionName              = installedMetas.versionName,
            installedAtEpochMillis   = installedAt,
            lastUpdatedAtEpochMillis = updatedAt,
            repositoryUrl            = publishedCatalog?.repositoryUrl
                ?: previous?.repositoryUrl.takeIf { publishedCatalog == null },
            requiredHostCapabilities = if (publishedCatalog != null) {
                (
                    // что требует каталог
                        publishedCatalog.requiredHostCapabilities +
                                // что разрешено
                        addon.grantedHostCapabilities
                    )
                    .map { capability -> capability.value }
                    .distinct()
                    .sorted()
            } else {
                previous?.requiredHostCapabilities
                    ?: addon.grantedHostCapabilities
                        .map { capability -> capability.value }
                        .sorted()
            },
            apkSha256 = publishedCatalog?.installArtifact
                ?.takeIf { artifact ->
                    artifact.versionCode == installedMetas.versionCode
                }
                ?.apkSha256
                ?: previous
                    ?.takeIf { record ->
                        publishedCatalog == null &&
                            record.versionCode == installedMetas.versionCode
                    }
                    ?.apkSha256,
            bannerSha256 = bannerSha256,
        )
        writeRecord(metadataFile, record) // сохранение метаданных
        return record
    }

    private fun clearRemovedPackageDirectories(
        installedAddons: List<RegisteredAddon>,
    ) {
        val occupiedLeaves = installedAddons
            .mapNotNull { addon -> addon.installedMetadata?.packageName }
            .groupBy(layout::packageLeaf)

        layout.rootDirectory.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .filter { directory ->
                directory.name !=
                    AddonFileLayout.TEMPORARY_DIRECTORY_NAME
            }
            .filter { directory ->
                directory.name !in occupiedLeaves.keys
            }
            .forEach(::deleteOwnedRecursively)
    }

    /**
     * Удаляет устаревшие banner-артефакты из каталога установленного аддона.
     *
     * В директории аддона может существовать несколько banner-файлов
     * после обновления metadata. Метод оставляет только файл [keep],
     * а остальные файлы, соответствующие формату banner storage,
     * удаляет.
     *
     * @param packageDirectory каталог установленного addon package.
     * @param keep актуальный banner-файл, который необходимо сохранить.
     *
     * @since 0.3
     */
    private fun clearObsoleteInstalledBanners(
        packageDirectory: File,
        keep            : File?,
    ) {
        packageDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                file != keep &&
                    file.name.startsWith(BANNER_PREFIX) &&
                    file.name.endsWith(BANNER_SUFFIX)
            }
            .forEach(File::delete)
    }

    private fun clearInterruptedWrites() {
        layout.rootDirectory.walkTopDown()
            .filter { file ->
                file.isFile && file.name.endsWith(PENDING_SUFFIX)
            }
            .forEach(File::delete)
    }

    private fun clearExpiredTemporaryApks() {
        val oldestAllowed =
            currentTimeMillis() - TEMPORARY_APK_RETENTION_MILLIS
        layout.temporaryDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.extension.equals("apk", ignoreCase = true) &&
                    file.lastModified() in 1L until oldestAllowed
            }
            .forEach(File::delete)
    }

    /**
     * Перемещает подготовленный addon-артефакт из временного расположения
     * в финальный host-owned путь.
     *
     * Метод использует промежуточный `.pending` файл:
     * ```
     * source
     *   ↓
     * destination.pending
     *   ↓
     * destination
     * ```
     * Это предотвращает появление частично записанного финального файла
     * при неожиданном завершении процесса во время копирования.
     *
     * Перед выполнением проверяется, что оба пути принадлежат addon storage.
     *
     * @param source исходный временный файл.
     * @param destination финальное расположение артефакта.
     *
     * @throws IllegalArgumentException если путь находится вне addon storage.
     * @throws IOException если финализация файла невозможна.
     *
     * @since 0.3
     */
    private fun promote(
        source: File,
        destination: File,
    ) {
        require(layout.isOwnedPath(source))
        require(layout.isOwnedPath(destination))
        // создание родительской директории, если нет
        destination.parentFile?.mkdirs()

        // безопасная запись в файл addon.apk.pending (защита от умирания приложения)
        val pending = File(destination.path + PENDING_SUFFIX)
        pending.delete() // очистка старого, если есть (при как раз умирании)

        source.inputStream().buffered().use { input ->
            // открывается поток выхода С ПЕРЕПИСЫВАНИЕМ ФАЙЛА, а не ДОБАВЛЕНИЕМ !
            FileOutputStream(pending, false).use { output ->
                // копирует до EOF
                input.copyTo(output)
                output.fd.sync() // сброс буфера файлсистемы реально в хранилище
            }
        }
        // если старый файл не удаляется, то ошибка
        if (destination.exists() && !destination.delete()) {
            pending.delete()
            throw IOException("Cannot replace ${destination.name}")
        }
        // переименование файла является атомарным, поэтому если не получилось -> ошибка
        if (!pending.renameTo(destination)) {
            pending.delete()
            throw IOException("Cannot finalize ${destination.name}")
        }

        // после успешной миграции -> теперь destination = source
        source.delete()
    }

    /**
     * Безопасно сохраняет локальную metadata-запись аддона.
     *
     * Запись выполняется через промежуточный `.pending` файл:
     * ```
     * metadata.json.pending
     *          ↓
     * metadata.json
     * ```
     * чтобы незавершённая запись не выглядела как валидная metadata.
     *
     * Перед записью проверяется принадлежность пути addon storage.
     *
     * @param destination итоговый путь metadata-файла.
     * @param record запись, которую необходимо сериализовать.
     *
     * @since 0.3
     */
    private fun writeRecord(
        destination: File,
        record: InstalledAddonRecord,
    ) {
        require(layout.isOwnedPath(destination))
        destination.parentFile?.mkdirs()

        val pending = File(destination.path + PENDING_SUFFIX)
        // сериализация объекта
        val bytes = json.encodeToString(record).toByteArray(Charsets.UTF_8)
        FileOutputStream(pending, false).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (destination.exists() && !destination.delete()) {
            pending.delete()
            throw IOException("Cannot replace addon metadata")
        }
        if (!pending.renameTo(destination)) {
            pending.delete()
            throw IOException("Cannot finalize addon metadata")
        }
    }

    /**
     * Читает локальную metadata-запись установленного аддона.
     *
     * Повреждённый или отсутствующий файл не считается критической ошибкой
     * и возвращает `null`.
     *
     * `CancellationException` пробрасывается дальше для корректной работы
     * coroutine cancellation.
     *
     * @param file путь к metadata JSON.
     * @return десериализованная запись или null.
     *
     * @since 0.3
     */
    private fun readRecord(
        file: File,
    ): InstalledAddonRecord? {
        if (!file.isFile) return null
        return try {
            json.decodeFromString<InstalledAddonRecord>(
                file.readText(Charsets.UTF_8),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteOwnedRecursively(directory: File) {
        if (!directory.exists()) return
        require(layout.isOwnedPath(directory))
        require(directory.canonicalFile != layout.rootDirectory.canonicalFile)
        if (!directory.deleteRecursively() && directory.exists()) {
            throw IOException("Cannot delete ${directory.name}")
        }
    }

    private fun deleteOwnedFile(file: File) {
        if (!file.exists()) return
        require(layout.isOwnedPath(file))
        if (!file.delete() && file.exists()) {
            throw IOException("Cannot delete ${file.name}")
        }
    }

    private companion object {
        const val BANNER_PREFIX = "banner-"
        const val BANNER_SUFFIX = ".asset"
        const val PENDING_SUFFIX = ".pending"
        const val TEMPORARY_APK_RETENTION_MILLIS =
            24L * 60L * 60L * 1_000L
    }
}
