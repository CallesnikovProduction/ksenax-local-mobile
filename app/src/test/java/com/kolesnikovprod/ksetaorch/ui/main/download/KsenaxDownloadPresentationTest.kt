package com.kolesnikovprod.ksetaorch.ui.main.download

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadWaitReason
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxModelDownloadOverlayState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxDownloadPresentationTest {

    @Test
    fun rawBytesAreFormattedWithRequestedLocale() {
        val text = formatTransferredBytes(
            metrics = KsenaxDownloadTransferMetrics(
                downloadedBytes = 6_100_000_000L,
                totalBytes = 10_000_000_000L,
            ),
            locale = Locale.forLanguageTag("ru-RU"),
        )

        assertEquals("6,1 ГБ / 10 ГБ", text)
    }

    @Test
    fun etaUsesMinuteAndSecondClock() {
        assertEquals(
            "ETA 4:20",
            formatDownloadEta(
                KsenaxDownloadTransferMetrics(
                    estimatedRemainingTimeSeconds = 260L,
                ),
            ),
        )
    }

    @Test
    fun verificationKeepsHundredPercentAndHidesTransferRate() {
        val presentation = KsenaxMainUiState(
            activeInstallOverlayTarget =
                KsenaxInstallOverlayTarget.Gemma4E2B,
            modelDownloadOverlayState =
                KsenaxModelDownloadOverlayState.Progress,
            gemmaInstallSnapshot = KsenaxInstallSnapshot(
                isDownloading = false,
                isValidating = true,
                downloadProgress = 1f,
                transferMetrics = KsenaxDownloadTransferMetrics(
                    downloadedBytes = 10_000_000_000L,
                    totalBytes = 10_000_000_000L,
                    averageSpeedBytesPerSecond = 12_400_000L,
                    estimatedRemainingTimeSeconds = 0L,
                ),
            ),
        ).activeDownloadPresentation()

        requireNotNull(presentation)
        assertEquals(
            KsenaxDownloadPresentationStage.Verification,
            presentation.stage,
        )
        assertEquals(100, presentation.percent)
        assertNull(presentation.speedText)
        assertNull(presentation.etaText)
        assertFalse(presentation.canCancel)
    }

    @Test
    fun pausedDownloadExplainsThatUnmeteredNetworkIsRequired() {
        val presentation = KsenaxMainUiState(
            activeInstallOverlayTarget =
                KsenaxInstallOverlayTarget.Gemma4E2B,
            modelDownloadOverlayState =
                KsenaxModelDownloadOverlayState.Progress,
            gemmaInstallSnapshot = KsenaxInstallSnapshot(
                currentDownloadId = 42L,
                isDownloading = true,
                downloadState = KsenaxDownloadState.PAUSED,
                downloadWaitReason =
                    KsenaxDownloadWaitReason.WAITING_FOR_UNMETERED_NETWORK,
                transferMetrics = KsenaxDownloadTransferMetrics(
                    downloadedBytes = 100L,
                    totalBytes = 200L,
                    averageSpeedBytesPerSecond = 12_400_000L,
                    estimatedRemainingTimeSeconds = 260L,
                ),
            ),
        ).activeDownloadPresentation()

        requireNotNull(presentation)
        assertEquals(
            "ожидание Wi-Fi или нелимитной сети",
            presentation.stageDescription,
        )
        assertNull(presentation.speedText)
        assertNull(presentation.etaText)
    }

    @Test
    fun runningDownloadUsesTransferDescription() {
        assertEquals(
            "скачивание файлов модели",
            downloadStageDescription(
                state = KsenaxDownloadState.RUNNING,
                waitReason = null,
            ),
        )
    }

    @Test
    fun completedDownloadDisablesActionsAndKeepsHundredPercent() {
        val presentation = KsenaxMainUiState(
            activeInstallOverlayTarget =
                KsenaxInstallOverlayTarget.Gemma4E2B,
            modelDownloadOverlayState =
                KsenaxModelDownloadOverlayState.Completed,
            gemmaInstallSnapshot = KsenaxInstallSnapshot(
                isInstalled = true,
                downloadProgress = 1f,
            ),
        ).activeDownloadPresentation()

        requireNotNull(presentation)
        assertEquals(KsenaxDownloadPresentationStage.Completed, presentation.stage)
        assertEquals(100, presentation.percent)
        assertFalse(presentation.canCancel)
    }

    @Test
    fun stalledHintIsShownOnlyDuringTransfer() {
        val presentation = KsenaxMainUiState(
            activeInstallOverlayTarget =
                KsenaxInstallOverlayTarget.Gemma4E2B,
            modelDownloadOverlayState =
                KsenaxModelDownloadOverlayState.Progress,
            isActiveDownloadStalled = true,
            gemmaInstallSnapshot = KsenaxInstallSnapshot(
                currentDownloadId = 42L,
                isDownloading = true,
                downloadState = KsenaxDownloadState.RUNNING,
            ),
        ).activeDownloadPresentation()

        requireNotNull(presentation)
        assertTrue(presentation.showSlowConnectionHint)
    }
}
