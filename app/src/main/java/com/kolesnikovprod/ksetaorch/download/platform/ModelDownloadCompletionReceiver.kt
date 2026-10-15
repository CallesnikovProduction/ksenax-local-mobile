package com.kolesnikovprod.ksetaorch.download.platform

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        if (downloadId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BackgroundModelInstallScheduler(context.applicationContext)
                    .enqueueFinalizationFor(downloadId, awaitPersistence = true)
            } catch (_: Exception) {
                // The persisted submission watchdog remains the recovery owner.
                Log.w("ModelInstall", "Completion scheduling deferred to persisted watchdog")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object { const val INVALID_DOWNLOAD_ID = -1L }
}
