package com.kolesnikovprod.ksetaorch.download.platform

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Получает системное событие завершения DownloadManager-задачи.
 *
 * Receiver не выполняет файловую работу в [onReceive]. Он только проверяет
 * action/id и передаёт финализацию в WorkManager, поэтому укладывается в
 * короткий lifecycle BroadcastReceiver даже при холодном старте процесса.
 *
 * @since 0.3
 */
internal class ModelDownloadCompletionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(
            DownloadManager.EXTRA_DOWNLOAD_ID,
            INVALID_DOWNLOAD_ID,
        )

        if (downloadId == INVALID_DOWNLOAD_ID) return

        BackgroundModelInstallScheduler(context)
            .enqueueFinalizationFor(downloadId)
    }

    private companion object {
        const val INVALID_DOWNLOAD_ID = -1L
    }
}
