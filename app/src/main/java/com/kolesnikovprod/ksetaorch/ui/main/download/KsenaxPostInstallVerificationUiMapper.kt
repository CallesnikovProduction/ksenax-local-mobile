package com.kolesnikovprod.ksetaorch.ui.main.download

import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationStatus
import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxPostInstallVerificationFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxPostInstallVerificationState

private const val PostInstallSupportingText =
    "проверка файлов и локального runtime"

/**
 * Преобразует состояние post-install проверки в самостоятельный контракт
 * validation-overlay.
 *
 * @since 0.3
 */
internal fun KsenaxPostInstallVerificationState?.toVerificationUiState():
    KsenaxModelVerificationUiState? = when (this) {
    null -> null

    KsenaxPostInstallVerificationState.CheckingPresence ->
        verificationState(
            presence = KsenaxModelVerificationStatus.Active,
        )

    KsenaxPostInstallVerificationState.CheckingIntegrity ->
        verificationState(
            presence = KsenaxModelVerificationStatus.Success,
            integrity = KsenaxModelVerificationStatus.Active,
        )

    KsenaxPostInstallVerificationState.CheckingReachability ->
        verificationState(
            presence = KsenaxModelVerificationStatus.Success,
            integrity = KsenaxModelVerificationStatus.Success,
            reachability = KsenaxModelVerificationStatus.Active,
        )

    KsenaxPostInstallVerificationState.Success ->
        verificationState(
            presence = KsenaxModelVerificationStatus.Success,
            integrity = KsenaxModelVerificationStatus.Success,
            reachability = KsenaxModelVerificationStatus.Success,
            supportingText = "модель доступна локальному runtime",
        )

    is KsenaxPostInstallVerificationState.Failure -> when (stage) {
        KsenaxPostInstallVerificationFailureStage.Presence ->
            verificationState(
                presence = KsenaxModelVerificationStatus.Failure,
                supportingText = message,
            )

        KsenaxPostInstallVerificationFailureStage.Integrity ->
            verificationState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Failure,
                supportingText = message,
            )

        KsenaxPostInstallVerificationFailureStage.Reachability ->
            verificationState(
                presence = KsenaxModelVerificationStatus.Success,
                integrity = KsenaxModelVerificationStatus.Success,
                reachability = KsenaxModelVerificationStatus.Failure,
                supportingText = message,
            )
    }
}

private fun verificationState(
    presence: KsenaxModelVerificationStatus =
        KsenaxModelVerificationStatus.Pending,
    integrity: KsenaxModelVerificationStatus =
        KsenaxModelVerificationStatus.Pending,
    reachability: KsenaxModelVerificationStatus =
        KsenaxModelVerificationStatus.Pending,
    supportingText: String = PostInstallSupportingText,
): KsenaxModelVerificationUiState {
    return KsenaxModelVerificationUiState(
        presence = presence,
        integrity = integrity,
        reachability = reachability,
        supportingText = supportingText,
    )
}
