package com.kolesnikovprod.ksetaorch.addons.download

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.AddonManifestContract
import dev.openksenax.addons.contract.HostCapabilityId
import dev.openksenax.addons.contract.management.AddonManagementContract
import com.kolesnikovprod.ksetaorch.addons.discovery.addonSigningCertificateSha256
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCatalogMetadata
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Проверяет file integrity, package identity, signer и manifest скачанного APK.
 *
 * @since 0.3
 */
internal class AndroidAddonApkVerifier(
    private val packageManager: PackageManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun verify(
        addon: RegisteredAddon,
        downloaded: AddonDownloadResult.Downloaded,
        onProgress: (AddonInstallProgress) -> Unit,
    ): AddonArtifactPreparationResult = withContext(ioDispatcher) {
        val published = addon.catalogMetadata
            ?: return@withContext failure(
                AddonArtifactFailure.MANIFEST_MISMATCH,
                "Catalog metadata is unavailable",
            )
        val artifact = published.installArtifact
        val file = downloaded.file

        try {
            onProgress(
                AddonInstallProgress(AddonInstallStage.VERIFYING_FILE),
            )
            if (!file.isFile || file.length() <= 0L) {
                return@withContext failure(
                    AddonArtifactFailure.APK_UNREADABLE,
                    "Downloaded APK file is unavailable",
                )
            }
            val actualSize = file.length()
            if (
                artifact.sizeBytes != null &&
                actualSize != artifact.sizeBytes
            ) {
                return@withContext failure(
                    AddonArtifactFailure.FILE_SIZE_MISMATCH,
                    "APK size does not match registry",
                )
            }
            val actualSha256 = file.sha256()
            if (actualSha256 != artifact.apkSha256.normalizeSha256()) {
                return@withContext failure(
                    AddonArtifactFailure.SHA256_MISMATCH,
                    "APK SHA-256 does not match registry",
                )
            }

            onProgress(
                AddonInstallProgress(AddonInstallStage.VERIFYING_APK),
            )
            val packageInfo = readArchive(file)
                ?: return@withContext failure(
                    AddonArtifactFailure.APK_UNREADABLE,
                    "Android PackageManager cannot parse the APK",
                )
            if (packageInfo.packageName != published.packageName) {
                return@withContext failure(
                    AddonArtifactFailure.PACKAGE_MISMATCH,
                    "APK package name does not match registry",
                )
            }
            if (
                PackageInfoCompat.getLongVersionCode(packageInfo) !=
                artifact.versionCode
            ) {
                return@withContext failure(
                    AddonArtifactFailure.VERSION_MISMATCH,
                    "APK versionCode does not match registry",
                )
            }
            val expectedSigner =
                artifact.signingCertificateSha256.normalizeSha256()
            val actualSigners = packageInfo
                .addonSigningCertificateSha256()
                .mapTo(mutableSetOf(), String::normalizeSha256)
            if (expectedSigner !in actualSigners) {
                return@withContext failure(
                    AddonArtifactFailure.SIGNATURE_MISMATCH,
                    "APK signing certificate does not match registry",
                )
            }

            val manifestIssue = verifyManagementService(
                packageInfo = packageInfo,
                addonId = addon.addonId,
                published = published,
            )
            if (manifestIssue != null) {
                return@withContext failure(
                    AddonArtifactFailure.MANIFEST_MISMATCH,
                    manifestIssue,
                )
            }

            onProgress(
                AddonInstallProgress(AddonInstallStage.READY_FOR_INSTALLER),
            )
            AddonArtifactPreparationResult.Ready(
                VerifiedAddonApk(
                    addonId = addon.addonId,
                    packageName = published.packageName,
                    versionCode = artifact.versionCode,
                    sha256 = actualSha256,
                    signingCertificateSha256 = expectedSigner,
                    sizeBytes = actualSize,
                    file = file,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            failure(
                AddonArtifactFailure.APK_UNREADABLE,
                error.message,
            )
        }
    }

    private fun readArchive(file: File): PackageInfo? {
        val flags = PackageManager.GET_SERVICES or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNING_CERTIFICATES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }
    }

    private fun verifyManagementService(
        packageInfo: PackageInfo,
        addonId: AddonId,
        published: AddonCatalogMetadata,
    ): String? {
        val candidates = packageInfo.services
            .orEmpty()
            .filter { service ->
                service.metaData?.readString(
                    AddonManifestContract.META_ADDON_ID,
                ) == addonId.value
            }
        if (candidates.size != 1) {
            return "APK must declare exactly one add-on management service"
        }
        val service = candidates.single()
        if (!service.exported) return "Add-on service must be exported"
        if (service.permission != AddonManagementContract.BIND_PERMISSION) {
            return "Add-on service uses an invalid bind permission"
        }
        return service.verifyMetadata(published)
    }

    private fun ServiceInfo.verifyMetadata(
        published: AddonCatalogMetadata,
    ): String? {
        val metadata = metaData
            ?: return "Add-on service metadata is missing"
        if (
            metadata.readPositiveInt(
                AddonManifestContract.META_PROTOCOL_VERSION,
            ) != published.protocolVersion
        ) return "Manifest protocol version does not match registry"
        if (
            metadata.readPositiveInt(
                AddonManifestContract.META_MANAGEMENT_API_VERSION,
            ) != published.managementApiVersion
        ) return "Manifest management API does not match registry"
        if (
            metadata.readPositiveInt(
                AddonManifestContract.META_MINIMUM_HOST_API,
            ) != published.minimumHostApi
        ) return "Manifest minimum host API does not match registry"
        val executionModel = metadata.readString(
            AddonManifestContract.META_EXECUTION_MODEL,
        )?.toAddonExecutionModelOrNull()
        if (executionModel != published.executionModel) {
            return "Manifest execution model does not match registry"
        }
        val capabilities = metadata.readString(
            AddonManifestContract.META_REQUIRED_CAPABILITIES,
        ).orEmpty().toHostCapabilityIdsOrNull()
            ?: return "Manifest capabilities contain an invalid identifier"
        if (capabilities != published.requiredHostCapabilities) {
            return "Manifest capabilities do not match registry"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun Bundle.readString(key: String): String? =
        get(key)?.toString()?.trim()?.takeIf(String::isNotEmpty)

    @Suppress("DEPRECATION")
    private fun Bundle.readPositiveInt(key: String): Int? {
        return when (val value = get(key)) {
            is Int -> value
            is Long -> value.toInt().takeIf {
                value in 1L..Int.MAX_VALUE.toLong()
            }
            is String -> value.toIntOrNull()
            else -> null
        }?.takeIf { it > 0 }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }

    private fun failure(
        reason: AddonArtifactFailure,
        message: String?,
    ) = AddonArtifactPreparationResult.Failed(reason, message)
}

/**
 * Декодирует execution model из Android manifest независимо от locale
 * устройства. Неизвестное wire-значение остаётся невалидным.
 *
 * @since 0.4
 */
internal fun String.toAddonExecutionModelOrNull(): AddonExecutionModel? {
    return runCatching {
        AddonExecutionModel.valueOf(trim().uppercase(Locale.ROOT))
    }.getOrNull()
}

/**
 * Строго декодирует список capability IDs из Android manifest.
 *
 * Пустая строка означает пустой набор. Если хотя бы один непустой элемент
 * нарушает контракт [HostCapabilityId], весь список считается невалидным —
 * некорректная metadata не может быть молча ослаблена удалением элемента.
 *
 * @since 0.4
 */
internal fun String.toHostCapabilityIdsOrNull(): Set<HostCapabilityId>? {
    val values = split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    val capabilities = values.map { value ->
        runCatching { HostCapabilityId(value) }.getOrNull()
            ?: return null
    }
    return capabilities.toSet()
}

/**
 * Production-композиция downloader + verifier без повторной policy-логики.
 *
 * @since 0.3
 */
internal class DefaultAddonArtifactPreparer(
    private val downloader: KtorAddonApkDownloader,
    private val verifier: AndroidAddonApkVerifier,
) : AddonArtifactPreparer {

    override suspend fun prepare(
        addon: RegisteredAddon,
        onProgress: (AddonInstallProgress) -> Unit,
    ): AddonArtifactPreparationResult {
        val artifact = addon.catalogMetadata?.installArtifact
            ?: return AddonArtifactPreparationResult.Failed(
                AddonArtifactFailure.MANIFEST_MISMATCH,
                "Catalog entry is unavailable",
            )
        return when (
            val download = downloader.download(
                addonId = addon.addonId,
                versionCode = artifact.versionCode,
                rawUrl = artifact.apkUrl,
                expectedSizeBytes = artifact.sizeBytes,
                onProgress = onProgress,
            )
        ) {
            is AddonDownloadResult.Downloaded -> {
                try {
                    verifier.verify(addon, download, onProgress)
                        .also { result ->
                            if (
                                result is
                                AddonArtifactPreparationResult.Failed
                            ) {
                                download.file.delete()
                            }
                        }
                } catch (cancellation: CancellationException) {
                    download.file.delete()
                    throw cancellation
                }
            }

            is AddonDownloadResult.Failed ->
                AddonArtifactPreparationResult.Failed(
                    reason = download.reason,
                    message = download.message,
                )
        }
    }
}
