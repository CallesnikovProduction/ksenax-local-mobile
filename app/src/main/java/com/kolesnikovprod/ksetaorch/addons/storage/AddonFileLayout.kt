package com.kolesnikovprod.ksetaorch.addons.storage

import dev.openksenax.addons.contract.AddonId
import java.io.File
import java.util.Locale

/**
 * Единственный владелец on-disk layout addon-артефактов OKx.
 *
 * Временные файлы живут в `addons/temp`, а подтверждённые Android
 * PackageManager установки получают каталог по последнему сегменту package:
 * `addons/<package-leaf>`.
 *
 * @since 0.3
 */
internal class AddonFileLayout(
    val rootDirectory: File,
) {

    val temporaryDirectory: File
        get() = rootDirectory.resolve(TEMPORARY_DIRECTORY_NAME)

    fun temporaryApk(
        addonId: AddonId,
        versionCode: Long,
    ): File {
        require(versionCode > 0L)
        return temporaryDirectory.resolve(
            "${addonId.safeFileSegment()}-$versionCode.apk",
        )
    }

    fun temporaryBanner(
        addonId: AddonId,
        sha256: String,
    ): File {
        return temporaryDirectory.resolve(
            "${addonId.safeFileSegment()}-${sha256.fileSha256()}.banner",
        )
    }

    fun packageDirectory(packageName: String): File {
        return rootDirectory.resolve(packageLeaf(packageName))
    }

    fun installedApk(packageName: String): File {
        return packageDirectory(packageName).resolve(INSTALLED_APK_FILE_NAME)
    }

    fun installedBanner(
        packageName: String,
        sha256: String,
    ): File {
        return packageDirectory(packageName).resolve(
            "banner-${sha256.fileSha256()}.asset",
        )
    }

    fun installedMetadata(packageName: String): File {
        return packageDirectory(packageName).resolve(METADATA_FILE_NAME)
    }

    /**
     * Проверяет принадлежность download-артефакта конкретному аддону.
     *
     * Кроме актуального `addons/temp` принимает корень `addons`: до появления
     * выделенной temp-директории APK сохранялись туда напрямую. Другие
     * package-каталоги и файлы соседних аддонов не совпадают.
     *
     * @since 0.3
     */
    fun isDownloadArtifactFor(
        addonId: AddonId,
        file: File,
    ): Boolean {
        val root = rootDirectory.canonicalFile
        val temporaryRoot = temporaryDirectory.canonicalFile
        val candidate = file.canonicalFile
        val candidateParent = candidate.parentFile ?: return false
        return (
            candidateParent == root ||
                candidateParent == temporaryRoot
            ) &&
            candidate.name.startsWith(
                "${addonId.safeFileSegment()}-",
            )
    }

    fun packageLeaf(packageName: String): String {
        require(packageName.isNotBlank()) {
            "packageName must not be blank"
        }
        val leaf = packageName.substringAfterLast('.')
        require(PACKAGE_LEAF_REGEX.matches(leaf)) {
            "Unsafe package leaf: $leaf"
        }
        return leaf
    }

    fun isOwnedPath(file: File): Boolean {
        val root = rootDirectory.canonicalFile
        val candidate = file.canonicalFile
        return candidate == root ||
            candidate.path.startsWith(root.path + File.separator)
    }

    private fun AddonId.safeFileSegment(): String {
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
        val PACKAGE_LEAF_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
        private val SHA_256_REGEX = Regex("^[A-F0-9]{64}$")
        private val NON_FILE_NAME = Regex("[^A-Za-z0-9._-]")
    }
}
