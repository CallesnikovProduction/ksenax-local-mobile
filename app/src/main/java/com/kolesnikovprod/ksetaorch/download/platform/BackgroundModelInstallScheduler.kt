package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kolesnikovprod.ksetaorch.download.domain.DownloadIdPreferencesStore
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import java.util.concurrent.TimeUnit

/**
 * Ставит post-download финализацию в надёжную очередь WorkManager.
 *
 * Scheduler принимает только системный `downloadId`, затем сверяет его с
 * сохранёнными target-specific id. Неизвестный id игнорируется, поэтому
 * внешний broadcast не может заставить приложение обработать чужую загрузку.
 *
 * @since 0.3
 */
internal class BackgroundModelInstallScheduler(
    context: Context,
) {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueueRecoveryFor(target: KsenaxInstallTarget) {
        val request = OneTimeWorkRequestBuilder<ModelInstallFinalizationWorker>()
            .setInputData(workDataOf(ModelInstallFinalizationWorker.KEY_INSTALL_TARGET_ID to target.id))
            .build()
        workManager.enqueueUniqueWork("model-install-recovery:${target.id}", ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueFinalizationFor(downloadId: Long, awaitPersistence: Boolean = false): Boolean {
        return enqueue(downloadId, continuation = false, awaitPersistence = awaitPersistence)
    }

    fun enqueueNextCheck(downloadId: Long): Boolean = enqueue(downloadId, continuation = true, awaitPersistence = true)

    private fun enqueue(downloadId: Long, continuation: Boolean, awaitPersistence: Boolean = false): Boolean {
        if (downloadId <= 0L) return false
        val installTarget = findInstallTarget(downloadId) ?: return false
        val workRequest = OneTimeWorkRequestBuilder<ModelInstallFinalizationWorker>()
            .setInputData(
                workDataOf(
                    ModelInstallFinalizationWorker.KEY_DOWNLOAD_ID to downloadId,
                    ModelInstallFinalizationWorker.KEY_INSTALL_TARGET_ID to installTarget.id,
                )
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MINIMUM_RETRY_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(workTagFor(installTarget))
            .setInitialDelay(if (continuation) 30L else 0L, TimeUnit.SECONDS)
            .build()

        val operation = workManager.enqueueUniqueWork(
            uniqueWorkName(
                installTarget = installTarget,
                downloadId = downloadId,
            ),
            if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            workRequest,
        )
        if (awaitPersistence) operation.result.get(8L, TimeUnit.SECONDS)

        return true
    }

    private fun findInstallTarget(downloadId: Long): KsenaxInstallTarget? {
        return KsenaxInstallTarget.entries.firstOrNull { installTarget ->
            DownloadIdPreferencesStore(
                context = appContext,
                installTarget = installTarget,
            ).get() == downloadId
        }
    }

    private fun uniqueWorkName(
        installTarget: KsenaxInstallTarget,
        downloadId: Long,
    ): String {
        return "model-install-finalization:${installTarget.id}:$downloadId"
    }

    private fun workTagFor(installTarget: KsenaxInstallTarget): String {
        return "model-install-finalization:${installTarget.id}"
    }

    private companion object {
        const val MINIMUM_RETRY_DELAY_SECONDS = 10L
    }
}
