package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID

internal const val KSENAX_DOWNLOAD_STALL_THRESHOLD_MILLIS = 120_000L

/**
 * Отслеживает отсутствие движения байтов одной активной download-задачи.
 *
 * Tracker не запускает таймер и не опрашивает DownloadManager. Он потребляет
 * уже приходящие во ViewModel install snapshots и использует монотонное время,
 * поэтому не зависит от перевода системных часов.
 *
 * @since 0.3
 */
internal class KsenaxDownloadStallTracker(
    private val thresholdMillis: Long =
        KSENAX_DOWNLOAD_STALL_THRESHOLD_MILLIS,
    private val nowMillis: () -> Long = {
        System.nanoTime() / NANOSECONDS_PER_MILLISECOND
    },
) {
    private var trackedTargetKey: String? = null
    private var trackedDownloadId = NO_DOWNLOAD_ID
    private var lastDownloadedBytes = 0L
    private var lastProgressAtMillis = 0L

    init {
        require(thresholdMillis > 0L) {
            "Download stall threshold must be positive."
        }
    }

    fun update(
        targetKey: String,
        snapshot: KsenaxInstallSnapshot,
    ): Boolean {
        require(targetKey.isNotBlank()) {
            "Download stall target key must not be blank."
        }

        if (
            !snapshot.isDownloading ||
            snapshot.currentDownloadId == NO_DOWNLOAD_ID
        ) {
            reset()
            return false
        }

        val now = nowMillis()
        val downloadedBytes =
            snapshot.transferMetrics.downloadedBytes.coerceAtLeast(0L)
        val isNewDownload =
            trackedTargetKey != targetKey ||
                trackedDownloadId != snapshot.currentDownloadId

        if (isNewDownload || downloadedBytes != lastDownloadedBytes) {
            trackedTargetKey = targetKey
            trackedDownloadId = snapshot.currentDownloadId
            lastDownloadedBytes = downloadedBytes
            lastProgressAtMillis = now
            return false
        }

        return now - lastProgressAtMillis >= thresholdMillis
    }

    fun reset() {
        trackedTargetKey = null
        trackedDownloadId = NO_DOWNLOAD_ID
        lastDownloadedBytes = 0L
        lastProgressAtMillis = 0L
    }

    private companion object {
        const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
    }
}
