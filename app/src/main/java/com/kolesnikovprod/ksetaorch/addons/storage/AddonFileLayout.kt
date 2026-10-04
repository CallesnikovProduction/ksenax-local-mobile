package com.kolesnikovprod.ksetaorch.addons.storage

import com.kolesnikovprod.ksetaorch.addons.identity.AddonIdentityPolicy
import dev.openksenax.addons.contract.AddonId
import java.io.File
import java.util.Locale

/**
 * Централизованно определяет on-disk layout addon-артефактов OpenKsenax.
 *
 * Класс отвечает только за построение и проверку host-owned filesystem
 * путей. Он не выполняет download, installation, serialization или
 * reconciliation самостоятельно.
 *
 * Временные артефакты размещаются в `addons/temp`, а подтверждённо
 * установленные Android packages получают отдельный каталог по безопасному
 * последнему сегменту package name:
 *
 * `addons/<package-leaf>/`
 *
 * Все имена файлов, зависящие от внешних идентификаторов или hashes,
 * нормализуются и валидируются перед использованием в пути.
 *
 * @property rootDirectory корневая директория addon storage.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal class AddonFileLayout(val rootDirectory: File) {

    /**
     * Директория временных addon-артефактов, ещё не переведённых
     * в installed projection.
     *
     * @since 0.3
     */
    val temporaryDirectory: File
        get() = rootDirectory.resolve(TEMPORARY_DIRECTORY_NAME)

    /**
     * Возвращает путь временного APK конкретной версии аддона.
     *
     * @param addonId логический идентификатор аддона.
     * @param versionCode ожидаемый Android version code.
     * @return путь внутри temporary storage.
     *
     * @since 0.3
     */
    fun temporaryApk(
        addonId: AddonId,
        versionCode: Long,
    ): File {
        require(versionCode > 0L)
        return temporaryDirectory.resolve(
            "${addonId.safeFileSegment()}-$versionCode.apk",
        )
    }

    /**
     * Возвращает путь временного banner-артефакта аддона.
     *
     * SHA-256 нормализуется и валидируется до включения в имя файла.
     *
     * @since 0.3
     */
    fun temporaryBanner(
        addonId: AddonId,
        sha256: String,
    ): File {
        return temporaryDirectory.resolve(
            "${addonId.safeFileSegment()}-${sha256.fileSha256()}.banner",
        )
    }

    /**
     * Использовать только последний сегмент, а не полный `packageName`.
     * Для
     * ```
     * com.example.noradar
     * ```
     *
     * даст:
     * ```
     * noradar
     * ```
     *
     * @since 0.3
     */
    fun packageDirectory(packageName: String): File {
        return rootDirectory.resolve(packageLeaf(packageName))
    }

    /**
     * Возвращает путь локальной копии APK установленного addon package.
     *
     * ```
     * addons/<package-leaf>/addon.apk
     * ```
     *
     * @since 0.3
     */
    fun installedApk(packageName: String): File {
        return packageDirectory(packageName).resolve(INSTALLED_APK_FILE_NAME)
    }

    /**
     * Возвращает путь локального файла с баннером, имея SHA-256 внутри названия.
     *
     * ```
     * addons/<leaf>/banner-<SHA256>.asset
     * ```
     *
     * @since 0.3
     */
    fun installedBanner(
        packageName: String,
        sha256: String,
    ): File {
        return packageDirectory(packageName).resolve(
            "banner-${sha256.fileSha256()}.asset",
        )
    }

    /**
     * Возвращает путь локального файла с метаданными.
     *
     * ```
     * addons/<leaf>/metadata.json
     * ```
     *
     * @since 0.3
     */
    fun installedMetadata(packageName: String): File {
        return packageDirectory(packageName).resolve(METADATA_FILE_NAME)
    }

    /**
     * Проверяет, может ли указанный файл считаться download-артефактом
     * конкретного аддона.
     *
     * Допускаются только файлы, расположенные непосредственно в текущей
     * temporary-директории или в legacy-корне addon storage и имеющие имя,
     * начинающееся с безопасного filesystem-сегмента [addonId].
     *
     * Проверка используется для ограниченного cleanup и не является
     * доказательством целостности или trust артефакта.
     *
     * @since 0.3
     */
    fun isDownloadArtifactFor(
        addonId: AddonId,
        file   : File,
    ): Boolean {
        val root            = rootDirectory.canonicalFile
        val temporaryRoot   = temporaryDirectory.canonicalFile
        val candidate       = file.canonicalFile
        val candidateParent = candidate.parentFile ?: return false
        return (
            candidateParent == root ||
                candidateParent == temporaryRoot
            ) &&
            candidate.name.startsWith(
                "${addonId.safeFileSegment()}-",
            )
    }

    /**
     * Преобразует Android package name в безопасный leaf-каталог
     * installed storage.
     *
     * Используется только последний сегмент package name. Полученное значение
     * обязано соответствовать ограниченному filesystem-safe формату.
     *
     * @since 0.3
     */
    fun packageLeaf(packageName: String): String {
        return requireNotNull(
            AddonIdentityPolicy.storagePackageLeafOrNull(packageName),
        ) {
            "Invalid or unsupported packageName: $packageName"
        }
    }

    /**
     * Проверяет, находится ли файл внутри корневого addon storage
     * или является самой корневой директорией.
     *
     * Сравнение выполняется по canonical path, чтобы избежать ложных
     * совпадений из-за `..` и других path-normalization особенностей.
     *
     * @since 0.3
     */
    fun isOwnedPath(file: File): Boolean {
        val root = rootDirectory.canonicalFile
        val candidate = file.canonicalFile
        return candidate == root ||
            candidate.path.startsWith(root.path + File.separator)
    }

    private fun AddonId.safeFileSegment(): String {
        require(AddonIdentityPolicy.isSupportedAddonId(this)) {
            "AddonId exceeds the host identity length limit"
        }
        return value.replace(NON_FILE_NAME, "_")
    }

    private fun String.fileSha256(): String {
        val normalized = replace(":", "")
            .trim()
            .uppercase(Locale.ROOT)
        require(SHA_256_REGEX.matches(normalized)) {
            "Invalid SHA-256"
        }
        return normalized
    }

    internal companion object {
        const val TEMPORARY_DIRECTORY_NAME = "temp"
        const val INSTALLED_APK_FILE_NAME = "addon.apk"
        const val METADATA_FILE_NAME = "metadata.json"
        private val SHA_256_REGEX = Regex("^[A-F0-9]{64}$")
        private val NON_FILE_NAME = Regex("[^A-Za-z0-9._-]")
    }
}
