package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.InstallCandidateFinalizer
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import kotlinx.coroutines.CancellationException

/**
 * Process-independent финализация завершённой загрузки модели.
 *
 * Worker запускается системным completion receiver-ом, заново собирает
 * target-specific use case и выполняет тот же [InstallCandidateFinalizer],
 * который использует UI-coordinator. Поэтому swipe-away не оставляет Vosk ZIP
 * нераспакованным, а `.litertlm` без integrity-проверки.
 *
 * @since 0.3
 */
internal class ModelInstallFinalizationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, INVALID_DOWNLOAD_ID)
        val installTargetId = inputData.getString(KEY_INSTALL_TARGET_ID)
            ?: return Result.failure()
        val installTarget = ModelInstallUseCaseFactory.targetById(installTargetId)
            ?: return Result.failure()

        if (downloadId == INVALID_DOWNLOAD_ID) return Result.failure()

        val installUseCase = ModelInstallUseCaseFactory.create(
            context = applicationContext,
            installTarget = installTarget,
        )

        if (installUseCase.getSavedDownloadId() != downloadId) {
            return Result.success()
        }

        return try {
            val downloadSnapshot = installUseCase.queryDownloadSnapshot(downloadId)

            when (downloadSnapshot?.state) {
                KsenaxDownloadState.SUCCESSFUL -> {
                    InstallCandidateFinalizer(installUseCase).finalize(
                        expectedDownloadId = downloadId,
                    )
                    Result.success()
                }

                KsenaxDownloadState.FAILED -> {
                    clearArtifactsIfStillOwned(
                        installUseCase = installUseCase,
                        expectedDownloadId = downloadId,
                    )
                    Result.success()
                }

                KsenaxDownloadState.PENDING,
                KsenaxDownloadState.RUNNING,
                KsenaxDownloadState.PAUSED,
                KsenaxDownloadState.UNKNOWN,
                null -> retryOrClear(
                    installUseCase = installUseCase,
                    expectedDownloadId = downloadId,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            retryOrClear(
                installUseCase = installUseCase,
                expectedDownloadId = downloadId,
            )
        }
    }

    private fun retryOrClear(
        installUseCase: KsenaxModelInstallUseCase,
        expectedDownloadId: Long,
    ): Result {
        if (installUseCase.getSavedDownloadId() != expectedDownloadId) {
            return Result.success()
        }

        if (runAttemptCount < MAX_TRANSIENT_RETRY_COUNT) {
            return Result.retry()
        }

        clearArtifactsIfStillOwned(
            installUseCase = installUseCase,
            expectedDownloadId = expectedDownloadId,
        )
        return Result.failure()
    }

    private fun clearArtifactsIfStillOwned(
        installUseCase: KsenaxModelInstallUseCase,
        expectedDownloadId: Long,
    ) {
        installUseCase.clearArtifactsIfOwnedBy(expectedDownloadId)
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_INSTALL_TARGET_ID = "install_target_id"

        private const val INVALID_DOWNLOAD_ID = -1L
        private const val MAX_TRANSIENT_RETRY_COUNT = 3
    }
}
