package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

import org.junit.Assert.assertEquals
import org.junit.Test

class KsenaxModelVerificationUiStateTest {

    @Test
    fun activeValidationUsesInProcessFace() {
        assertEquals(
            KsenaxModelVerificationFaceState.InProcess,
            verificationState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Active,
                reachability = KsenaxModelVerificationStatus.Pending,
            ).faceState,
        )
    }

    @Test
    fun anyFailedStageUsesFailureFace() {
        assertEquals(
            KsenaxModelVerificationFaceState.Failure,
            verificationState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Failure,
                reachability = KsenaxModelVerificationStatus.Pending,
            ).faceState,
        )
    }

    @Test
    fun completedValidationUsesSuccessFace() {
        assertEquals(
            KsenaxModelVerificationFaceState.Success,
            verificationState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Success,
                reachability = KsenaxModelVerificationStatus.Success,
            ).faceState,
        )
    }

    private fun verificationState(
        presence: KsenaxModelVerificationStatus,
        integrity: KsenaxModelVerificationStatus,
        reachability: KsenaxModelVerificationStatus,
    ) = KsenaxModelVerificationUiState(
        presence = presence,
        integrity = integrity,
        reachability = reachability,
        supportingText = "",
    )
}
