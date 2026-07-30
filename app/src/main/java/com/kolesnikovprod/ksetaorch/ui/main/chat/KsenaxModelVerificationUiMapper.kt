package com.kolesnikovprod.ksetaorch.ui.main.chat

import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationStatus
import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState

private const val DefaultVerificationSupportingText =
    "проверка файлов и локального runtime"

/**
 * Адаптирует общий model-gate чатов к самостоятельному UI-контракту
 * validation-overlay.
 *
 * `null` означает, что overlay не должен отображаться.
 *
 * @since 0.3
 */
internal fun KsenaxBasicModelGateState.toModelVerificationUiState():
    KsenaxModelVerificationUiState? = when (this) {
    KsenaxBasicModelGateState.CheckingPresence ->
        KsenaxModelVerificationUiState(
            presence = KsenaxModelVerificationStatus.Active,
            integrity = KsenaxModelVerificationStatus.Pending,
            reachability = KsenaxModelVerificationStatus.Pending,
            supportingText = DefaultVerificationSupportingText,
        )

    KsenaxBasicModelGateState.CheckingIntegrity ->
        KsenaxModelVerificationUiState(
            presence = KsenaxModelVerificationStatus.Success,
            integrity = KsenaxModelVerificationStatus.Active,
            reachability = KsenaxModelVerificationStatus.Pending,
            supportingText = DefaultVerificationSupportingText,
        )

    KsenaxBasicModelGateState.PreparingModel ->
        KsenaxModelVerificationUiState(
            presence = KsenaxModelVerificationStatus.Success,
            integrity = KsenaxModelVerificationStatus.Success,
            reachability = KsenaxModelVerificationStatus.Active,
            supportingText = DefaultVerificationSupportingText,
        )

    KsenaxBasicModelGateState.ModelPrepared ->
        KsenaxModelVerificationUiState(
            presence = KsenaxModelVerificationStatus.Success,
            integrity = KsenaxModelVerificationStatus.Success,
            reachability = KsenaxModelVerificationStatus.Success,
            supportingText = "модель доступна локальному runtime",
        )

    is KsenaxBasicModelGateState.Failure -> when (stage) {
        KsenaxBasicModelFailureStage.Presence ->
            KsenaxModelVerificationUiState(
                presence = KsenaxModelVerificationStatus.Failure,
                integrity = KsenaxModelVerificationStatus.Pending,
                reachability = KsenaxModelVerificationStatus.Pending,
                supportingText = message,
            )

        KsenaxBasicModelFailureStage.Integrity ->
            KsenaxModelVerificationUiState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Failure,
                reachability = KsenaxModelVerificationStatus.Pending,
                supportingText = message,
            )

        KsenaxBasicModelFailureStage.Preparation ->
            KsenaxModelVerificationUiState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Success,
                reachability = KsenaxModelVerificationStatus.Failure,
                supportingText = message,
            )
    }

    KsenaxBasicModelGateState.Idle,
    KsenaxBasicModelGateState.Ready -> null
}
