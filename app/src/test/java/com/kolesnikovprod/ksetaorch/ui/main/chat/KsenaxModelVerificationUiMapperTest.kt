package com.kolesnikovprod.ksetaorch.ui.main.chat

import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationStatus
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KsenaxModelVerificationUiMapperTest {

    @Test
    fun idleAndReadyDoNotCreateOverlayState() {
        assertNull(KsenaxBasicModelGateState.Idle.toModelVerificationUiState())
        assertNull(KsenaxBasicModelGateState.Ready.toModelVerificationUiState())
    }

    @Test
    fun presenceCheckActivatesOnlyFirstStage() {
        assertStageStatuses(
            state = KsenaxBasicModelGateState.CheckingPresence,
            expected = listOf(
                KsenaxModelVerificationStatus.Active,
                KsenaxModelVerificationStatus.Pending,
                KsenaxModelVerificationStatus.Pending,
            ),
        )
    }

    @Test
    fun integrityCheckMarksPresenceAsCompleted() {
        assertStageStatuses(
            state = KsenaxBasicModelGateState.CheckingIntegrity,
            expected = listOf(
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Active,
                KsenaxModelVerificationStatus.Pending,
            ),
        )
    }

    @Test
    fun runtimePreparationIsPresentedAsReachabilityCheck() {
        assertStageStatuses(
            state = KsenaxBasicModelGateState.PreparingModel,
            expected = listOf(
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Active,
            ),
        )
    }

    @Test
    fun preparedModelShowsFinalSuccessForEveryStage() {
        assertStageStatuses(
            state = KsenaxBasicModelGateState.ModelPrepared,
            expected = List(3) {
                KsenaxModelVerificationStatus.Success
            },
        )
    }

    @Test
    fun criteriaAndPipelineUseRequestedLabels() {
        val stages = KsenaxBasicModelGateState.CheckingPresence
            .toModelVerificationUiState()
            .let(::requireNotNull)
            .stages

        assertEquals(
            listOf("наличие", "целостность", "достижимость"),
            stages.map { stage -> stage.pipelineLabel },
        )
        assertEquals(
            listOf("файлы", "целостность", "доступность"),
            stages.map { stage -> stage.criterionLabel },
        )
    }

    @Test
    fun presenceFailureStopsAtFirstStage() {
        assertFailureStage(
            failureStage = KsenaxBasicModelFailureStage.Presence,
            expected = listOf(
                KsenaxModelVerificationStatus.Failure,
                KsenaxModelVerificationStatus.Pending,
                KsenaxModelVerificationStatus.Pending,
            ),
        )
    }

    @Test
    fun integrityFailureKeepsPresenceCompleted() {
        assertFailureStage(
            failureStage = KsenaxBasicModelFailureStage.Integrity,
            expected = listOf(
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Failure,
                KsenaxModelVerificationStatus.Pending,
            ),
        )
    }

    @Test
    fun preparationFailureIsAttachedToReachabilityStage() {
        val message = "Runtime недоступен"
        val presentation = requireNotNull(KsenaxBasicModelGateState.Failure(
            message = message,
            stage = KsenaxBasicModelFailureStage.Preparation,
        ).toModelVerificationUiState())

        assertEquals(
            listOf(
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Success,
                KsenaxModelVerificationStatus.Failure,
            ),
            presentation.stages.map { stage -> stage.status },
        )
        assertEquals(message, presentation.supportingText)
    }

    private fun assertFailureStage(
        failureStage: KsenaxBasicModelFailureStage,
        expected: List<KsenaxModelVerificationStatus>,
    ) {
        assertStageStatuses(
            state = KsenaxBasicModelGateState.Failure(
                message = "failure",
                stage = failureStage,
            ),
            expected = expected,
        )
    }

    private fun assertStageStatuses(
        state: KsenaxBasicModelGateState,
        expected: List<KsenaxModelVerificationStatus>,
    ) {
        assertEquals(
            expected,
            requireNotNull(state.toModelVerificationUiState())
                .stages
                .map { stage -> stage.status },
        )
    }
}
