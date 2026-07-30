package com.kolesnikovprod.ksetaorch.ui.helpers

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class KsenaxInstallUiStateReducerTest {

    @Test
    fun unavailableEstimatesKeepPreviousValuesWithinSameDownload() {
        val previousSnapshot = activeSnapshot(
            downloadId = 42L,
            speedBytesPerSecond = 12_400_000L,
            etaSeconds = 260L,
        )
        val nextSnapshot = activeSnapshot(
            downloadId = 42L,
            speedBytesPerSecond = 0L,
            etaSeconds = null,
        )

        val nextState = KsenaxInstallUiStateReducer.withSnapshot(
            uiState = KsenaxMainUiState(
                gemmaInstallSnapshot = previousSnapshot,
            ),
            target = KsenaxInstallOverlayTarget.Gemma4E2B,
            snapshot = nextSnapshot,
        )

        assertEquals(
            12_400_000L,
            nextState.gemmaInstallSnapshot.transferMetrics
                .averageSpeedBytesPerSecond,
        )
        assertEquals(
            260L,
            nextState.gemmaInstallSnapshot.transferMetrics
                .estimatedRemainingTimeSeconds,
        )
    }

    @Test
    fun newDownloadDoesNotReusePreviousEstimates() {
        val previousSnapshot = activeSnapshot(
            downloadId = 42L,
            speedBytesPerSecond = 12_400_000L,
            etaSeconds = 260L,
        )
        val nextSnapshot = activeSnapshot(
            downloadId = 84L,
            speedBytesPerSecond = 0L,
            etaSeconds = null,
        )

        val nextState = KsenaxInstallUiStateReducer.withSnapshot(
            uiState = KsenaxMainUiState(
                gemmaInstallSnapshot = previousSnapshot,
            ),
            target = KsenaxInstallOverlayTarget.Gemma4E2B,
            snapshot = nextSnapshot,
        )

        assertEquals(
            0L,
            nextState.gemmaInstallSnapshot.transferMetrics
                .averageSpeedBytesPerSecond,
        )
        assertEquals(
            null,
            nextState.gemmaInstallSnapshot.transferMetrics
                .estimatedRemainingTimeSeconds,
        )
    }

    private fun activeSnapshot(
        downloadId: Long,
        speedBytesPerSecond: Long,
        etaSeconds: Long?,
    ): KsenaxInstallSnapshot {
        return KsenaxInstallSnapshot(
            currentDownloadId = downloadId,
            isDownloading = true,
            transferMetrics = KsenaxDownloadTransferMetrics(
                averageSpeedBytesPerSecond = speedBytesPerSecond,
                estimatedRemainingTimeSeconds = etaSeconds,
            ),
        )
    }
}
