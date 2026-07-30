package com.kolesnikovprod.ksetaorch.ui.main.download

import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationStatus
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxPostInstallVerificationState
import org.junit.Assert.assertEquals
import org.junit.Test

class KsenaxPostInstallVerificationUiMapperTest {

    @Test
    fun reachabilityStageKeepsCompletedFileChecks() {
        val state =
            KsenaxPostInstallVerificationState.CheckingReachability
                .toVerificationUiState()

        requireNotNull(state)
        assertEquals(KsenaxModelVerificationStatus.Success, state.presence)
        assertEquals(KsenaxModelVerificationStatus.Success, state.integrity)
        assertEquals(KsenaxModelVerificationStatus.Active, state.reachability)
    }

    @Test
    fun successMarksAllThreeStages() {
        val state =
            KsenaxPostInstallVerificationState.Success
                .toVerificationUiState()

        requireNotNull(state)
        assertEquals(
            listOf(
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Success,
            ),
            listOf(state.presence, state.integrity, state.reachability),
        )
    }
}
