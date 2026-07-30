package com.kolesnikovprod.ksetaorch.addons.storage

import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon
import dev.openksenax.addons.contract.AddonId
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Порт локальной проекции файлов реально установленных addon APK.
 *
 * Наличие скачанного APK не является установкой. Реализация создаёт финальный
 * каталог только после того, как registry увидел package через PackageManager.
 *
 * @since 0.3
 */
internal interface AddonLocalStore {

    suspend fun reconcile(
        registryState: AddonRegistryState,
    ): List<InstalledAddonRecord>

    suspend fun read(
        packageName: String,
    ): InstalledAddonRecord?

    /**
     * Удаляет только host-owned файлы подтверждённо удалённого addon APK.
     *
     * @since 0.3
     */
    suspend fun remove(
        addonId: AddonId,
        packageName: String,
    )
}

/**
 * Сериализуемая локальная карточка установленного аддона.
 *
 * Это presentation/cache metadata, а не источник trust-решений.
 *
 * @since 0.3
 */
@Serializable
internal data class InstalledAddonRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val addonId: String,
    val packageName: String,
    val displayName: String,
    val shortDescription: String,
    val fullDescription: String?,
    val versionCode: Long,
    val versionName: String?,
    val installedAtEpochMillis: Long,
    val lastUpdatedAtEpochMillis: Long,
    val repositoryUrl: String?,
    val requiredHostCapabilities: List<String>,
    val apkSha256: String?,
    val bannerSha256: String?,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
        require(addonId.isNotBlank())
        require(packageName.isNotBlank())
        require(displayName.isNotBlank())
        require(versionCode > 0L)
        require(installedAtEpochMillis >= 0L)
        require(lastUpdatedAtEpochMillis >= 0L)
    }

    internal companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Файловая реализация app-specific addon storage.
 *
 * @since 0.3
 */
internal class FileAddonLocalStore(
    private val layout: AddonFileLayout,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
    private val currentTimeMillis: () -> Long =
        System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonLocalStore {

    private val mutex = Mutex()

    override suspend fun reconcile(
        registryState: AddonRegistryState,
    ): List<InstalledAddonRecord> = withContext(ioDispatcher) {
        mutex.withLock {
            layout.rootDirectory.mkdirs()
            layout.temporaryDirectory.mkdirs()
            clearInterruptedWrites()
            clearExpiredTemporaryApks()

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

            if (
                registryState.discoveryStatus ==
                AddonRegistrySourceStatus.Fresh
            ) {
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
                        require(layout.isOwnedPath(file))
                        file.delete()
                    }
                }
        }
    }

    private fun reconcileInstalled(
        addon: RegisteredAddon,
    ): InstalledAddonRecord {
        val installed = requireNotNull(addon.installedMetadata)
        val published = addon.catalogMetadata
        val packageDirectory = layout.packageDirectory(
            installed.packageName,
        )
        packageDirectory.mkdirs()

        val metadataFile = layout.installedMetadata(
            installed.packageName,
        )
        val previous = readRecord(metadataFile)
        val finalApk = layout.installedApk(installed.packageName)
        val downloadedApk = layout.temporaryApk(
            addonId = addon.addonId,
            versionCode = installed.versionCode,
        )

        if (downloadedApk.isFile) {
            promote(downloadedApk, finalApk)
        } else if (
            previous != null &&
            previous.versionCode != installed.versionCode
        ) {
            finalApk.delete()
        }

        val bannerSha256 = published?.bannerArtifact?.sha256
        if (bannerSha256 != null) {
            val finalBanner = layout.installedBanner(
                packageName = installed.packageName,
                sha256 = bannerSha256,
            )
            val temporaryBanner = layout.temporaryBanner(
                addonId = addon.addonId,
                sha256 = bannerSha256,
            )
            if (!finalBanner.isFile && temporaryBanner.isFile) {
                promote(temporaryBanner, finalBanner)
            }
            clearObsoleteInstalledBanners(
                packageDirectory = packageDirectory,
                keep = finalBanner.takeIf(File::isFile),
            )
        } else {
            clearObsoleteInstalledBanners(
                packageDirectory = packageDirectory,
                keep = null,
            )
        }

        val now = currentTimeMillis().coerceAtLeast(0L)
        val installedAt = installed.firstInstallTimeEpochMillis
            .takeIf { value -> value > 0L }
            ?: previous?.installedAtEpochMillis
            ?: now
        val updatedAt = installed.lastUpdateTimeEpochMillis
            .takeIf { value -> value > 0L }
            ?: previous?.lastUpdatedAtEpochMillis
            ?: installedAt

        val record = InstalledAddonRecord(
            addonId = addon.addonId.value,
            packageName = installed.packageName,
            displayName = addon.displayName,
            shortDescription = published?.shortDescription.orEmpty(),
            fullDescription = published?.fullDescription,
            versionCode = installed.versionCode,
            versionName = installed.versionName,
            installedAtEpochMillis = installedAt,
            lastUpdatedAtEpochMillis = updatedAt,
            repositoryUrl = published?.repositoryUrl,
            requiredHostCapabilities = (
                published?.requiredHostCapabilities.orEmpty() +
                    addon.grantedHostCapabilities
                )
                .map { capability -> capability.value }
                .distinct()
                .sorted(),
            apkSha256 = published?.installArtifact
                ?.takeIf { artifact ->
                    artifact.versionCode == installed.versionCode
                }
                ?.apkSha256,
            bannerSha256 = bannerSha256,
        )
        writeRecord(metadataFile, record)
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

    private fun clearObsoleteInstalledBanners(
        packageDirectory: File,
        keep: File?,
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

    private fun promote(source: File, destination: File) {
        require(layout.isOwnedPath(source))
        require(layout.isOwnedPath(destination))
        destination.parentFile?.mkdirs()

        val pending = File(destination.path + PENDING_SUFFIX)
        pending.delete()
        source.inputStream().buffered().use { input ->
            FileOutputStream(pending, false).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (destination.exists() && !destination.delete()) {
            pending.delete()
            error("Cannot replace ${destination.name}")
        }
        if (!pending.renameTo(destination)) {
            pending.delete()
            error("Cannot finalize ${destination.name}")
        }
        source.delete()
    }

    private fun writeRecord(
        destination: File,
        record: InstalledAddonRecord,
    ) {
        require(layout.isOwnedPath(destination))
        destination.parentFile?.mkdirs()
        val pending = File(destination.path + PENDING_SUFFIX)
        val bytes = json.encodeToString(record).toByteArray(Charsets.UTF_8)
        FileOutputStream(pending, false).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (destination.exists() && !destination.delete()) {
            pending.delete()
            error("Cannot replace addon metadata")
        }
        if (!pending.renameTo(destination)) {
            pending.delete()
            error("Cannot finalize addon metadata")
        }
    }

    private fun readRecord(file: File): InstalledAddonRecord? {
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
        directory.deleteRecursively()
    }

    private companion object {
        const val BANNER_PREFIX = "banner-"
        const val BANNER_SUFFIX = ".asset"
        const val PENDING_SUFFIX = ".pending"
        const val TEMPORARY_APK_RETENTION_MILLIS =
            24L * 60L * 60L * 1_000L
    }
}
