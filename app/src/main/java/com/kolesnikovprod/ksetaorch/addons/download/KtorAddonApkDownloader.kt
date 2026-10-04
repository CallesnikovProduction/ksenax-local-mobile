package com.kolesnikovprod.ksetaorch.addons.download

import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteFileFailure
import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteFileRequest
import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteFileResult
import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteUrlPolicy
import com.kolesnikovprod.ksetaorch.addons.remote.KtorAddonRemoteFileDownloader
import com.kolesnikovprod.ksetaorch.addons.storage.AddonFileLayout
import dev.openksenax.addons.contract.AddonId
import java.io.File

/**
 * APK-специализация общего потокового downloader.
 *
 * Класс выбирает имя установочного файла и преобразует transport errors
 * в ошибки APK-контура. Сетевой механизм принадлежит
 * [KtorAddonRemoteFileDownloader] и переиспользуется баннерами.
 *
 * @since 0.3
 */
internal class KtorAddonApkDownloader(
    private val fileDownloader: KtorAddonRemoteFileDownloader,
    private val fileLayout: AddonFileLayout,
    private val maximumApkBytes: Long = DEFAULT_MAXIMUM_APK_BYTES,
) {

    init {
        require(maximumApkBytes > 0L)
    }

    suspend fun download(
        addonId: AddonId,
        versionCode: Long,
        rawUrl: String,
        expectedSizeBytes: Long?,
        onProgress: (AddonInstallProgress) -> Unit,
    ): AddonDownloadResult {
        val destination = fileLayout.temporaryApk(
            addonId = addonId,
            versionCode = versionCode,
        )
        if (
            destination.isFile &&
            destination.length() > 0L &&
            (
                expectedSizeBytes == null ||
                    destination.length() == expectedSizeBytes
                )
        ) {
            onProgress(
                AddonInstallProgress(
                    stage = AddonInstallStage.DOWNLOADING,
                    downloadedBytes = destination.length(),
                    totalBytes =
                        expectedSizeBytes ?: destination.length(),
                ),
            )
            return AddonDownloadResult.Downloaded(
                file = destination,
                sizeBytes = destination.length(),
            )
        }
        val speedEstimator = AddonTransferSpeedEstimator()

        return when (
            val result = fileDownloader.download(
                request = AddonRemoteFileRequest(
                    rawUrl = rawUrl,
                    destination = destination,
                    maximumBytes = maximumApkBytes,
                    expectedSizeBytes = expectedSizeBytes,
                    acceptContentType = APK_CONTENT_TYPE,
                    requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS,
                    socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS,
                ),
                onProgress = { downloadedBytes, totalBytes ->
                    onProgress(
                        AddonInstallProgress(
                            stage = AddonInstallStage.DOWNLOADING,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            bytesPerSecond =
                                speedEstimator.update(downloadedBytes),
                        ),
                    )
                },
            )
        ) {
            is AddonRemoteFileResult.Downloaded ->
                AddonDownloadResult.Downloaded(
                    file = result.file,
                    sizeBytes = result.sizeBytes,
                )

            is AddonRemoteFileResult.Failed ->
                AddonDownloadResult.Failed(
                    reason = result.reason.toArtifactFailure(),
                    message = result.message,
                )
        }
    }

    private fun AddonRemoteFileFailure.toArtifactFailure():
            AddonArtifactFailure {
        return when (this) {
            AddonRemoteFileFailure.UNSAFE_URL ->
                AddonArtifactFailure.UNSAFE_DOWNLOAD_URL

            AddonRemoteFileFailure.SIZE_MISMATCH ->
                AddonArtifactFailure.FILE_SIZE_MISMATCH

            AddonRemoteFileFailure.NETWORK_UNAVAILABLE ->
                AddonArtifactFailure.NETWORK_UNAVAILABLE

            AddonRemoteFileFailure.TRANSFER_FAILED ->
                AddonArtifactFailure.DOWNLOAD_FAILED
        }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_APK_BYTES =
            512L * 1024L * 1024L
        const val DOWNLOAD_TIMEOUT_MILLIS =
            10L * 60L * 1_000L
        const val SOCKET_TIMEOUT_MILLIS = 45_000L
        const val APK_CONTENT_TYPE =
            "application/vnd.android.package-archive"
    }
}

/**
 * Совместимое имя policy для существующих APK-тестов.
 *
 * @since 0.3
 */
internal typealias AddonDownloadUrlPolicy = AddonRemoteUrlPolicy

/**
 * Результат транспортной стадии APK download до Android archive verification.
 *
 * @since 0.4
 */
internal sealed interface AddonDownloadResult {
    data class Downloaded(
        val file: File,
        val sizeBytes: Long,
    ) : AddonDownloadResult

    data class Failed(
        val reason: AddonArtifactFailure,
        val message: String?,
    ) : AddonDownloadResult
}
