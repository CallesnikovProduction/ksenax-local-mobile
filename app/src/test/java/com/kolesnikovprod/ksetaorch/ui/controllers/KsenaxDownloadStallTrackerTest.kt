package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxDownloadStallTrackerTest {

    @Test
    fun reportsStallAfterTwoMinutesWithoutByteProgress() {
        var now = 0L
        val tracker = KsenaxDownloadStallTracker(
            thresholdMillis = 120_000L,
            nowMillis = { now },
        )

        assertFalse(tracker.update("gemma", snapshot(bytes = 10L)))
        now = 119_999L
        assertFalse(tracker.update("gemma", snapshot(bytes = 10L)))
        now = 120_000L
        assertTrue(tracker.update("gemma", snapshot(bytes = 10L)))
    }

    @Test
    fun byteProgressRestartsTheStallWindow() {
        var now = 0L
        val tracker = KsenaxDownloadStallTracker(
            thresholdMillis = 120_000L,
            nowMillis = { now },
        )

        tracker.update("gemma", snapshot(bytes = 10L))
        now = 100_000L
        assertFalse(tracker.update("gemma", snapshot(bytes = 20L)))
        now = 219_999L
        assertFalse(tracker.update("gemma", snapshot(bytes = 20L)))
        now = 220_000L
        assertTrue(tracker.update("gemma", snapshot(bytes = 20L)))
    }

    @Test
    fun completedDownloadClearsTrackedWindow() {
        var now = 0L
        val tracker = KsenaxDownloadStallTracker(
            thresholdMillis = 120_000L,
            nowMillis = { now },
        )

        tracker.update("gemma", snapshot(bytes = 10L))
        now = 120_000L
        assertFalse(
            tracker.update(
                targetKey = "gemma",
                snapshot = KsenaxInstallSnapshot(isDownloading = false),
            ),
        )
        assertFalse(tracker.update("gemma", snapshot(bytes = 10L)))
    }

    private fun snapshot(bytes: Long): KsenaxInstallSnapshot {
        return KsenaxInstallSnapshot(
            currentDownloadId = 42L,
            isDownloading = true,
            transferMetrics = KsenaxDownloadTransferMetrics(
                downloadedBytes = bytes,
            ),
        )
    }
}
