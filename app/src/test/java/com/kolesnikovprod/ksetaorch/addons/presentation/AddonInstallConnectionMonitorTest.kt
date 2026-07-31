package com.kolesnikovprod.ksetaorch.addons.presentation

import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallProgress
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallStage
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonInstallConnectionMonitorTest {

    @Test
    fun keepsNormalStateDuringInitialGracePeriod() {
        var nowMillis = 0L
        val monitor = AddonInstallConnectionMonitor(
            elapsedRealtimeMillis = { nowMillis },
        )
        monitor.update(
            AddonInstallProgress(
                stage = AddonInstallStage.DOWNLOADING,
                downloadedBytes = 1L,
                bytesPerSecond = 1L,
            ),
        )

        nowMillis = 24_999L

        assertEquals(
            AddonInstallConnectionQuality.NORMAL,
            monitor.currentQuality(),
        )
    }

    @Test
    fun marksMeasuredSlowConnectionAfterGracePeriod() {
        var nowMillis = 0L
        val monitor = AddonInstallConnectionMonitor(
            elapsedRealtimeMillis = { nowMillis },
        )

        nowMillis = 25_001L
        monitor.update(
            AddonInstallProgress(
                stage = AddonInstallStage.DOWNLOADING,
                downloadedBytes = 1_024L,
                bytesPerSecond = 32L * 1_024L,
            ),
        )

        assertEquals(
            AddonInstallConnectionQuality.WEAK,
            monitor.currentQuality(),
        )
    }

    @Test
    fun marksStalledDownloadAndRecoversWhenBytesResumeQuickly() {
        var nowMillis = 0L
        val monitor = AddonInstallConnectionMonitor(
            elapsedRealtimeMillis = { nowMillis },
        )
        monitor.update(
            AddonInstallProgress(
                stage = AddonInstallStage.DOWNLOADING,
                downloadedBytes = 1_024L,
                bytesPerSecond = 512L * 1_024L,
            ),
        )

        nowMillis = 25_001L
        assertEquals(
            AddonInstallConnectionQuality.WEAK,
            monitor.currentQuality(),
        )

        monitor.update(
            AddonInstallProgress(
                stage = AddonInstallStage.DOWNLOADING,
                downloadedBytes = 2_048L,
                bytesPerSecond = 512L * 1_024L,
            ),
        )

        assertEquals(
            AddonInstallConnectionQuality.NORMAL,
            monitor.currentQuality(),
        )
    }

    @Test
    fun neverMarksVerificationStageAsWeak() {
        var nowMillis = 30_000L
        val monitor = AddonInstallConnectionMonitor(
            elapsedRealtimeMillis = { nowMillis },
        )
        monitor.update(
            AddonInstallProgress(
                stage = AddonInstallStage.VERIFYING_APK,
            ),
        )

        nowMillis = 60_000L

        assertEquals(
            AddonInstallConnectionQuality.NORMAL,
            monitor.currentQuality(),
        )
    }
}
