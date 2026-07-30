package com.kolesnikovprod.ksetaorch.ui.viewmodels

/**
 * Состояние проверки модели, которая запускается сразу после её установки.
 *
 * Оно не содержит Compose-типов: преобразование в контракт validation-overlay
 * остаётся в presentation-контуре.
 *
 * @since 0.3
 */
sealed interface KsenaxPostInstallVerificationState {
    data object CheckingPresence : KsenaxPostInstallVerificationState
    data object CheckingIntegrity : KsenaxPostInstallVerificationState
    data object CheckingReachability : KsenaxPostInstallVerificationState
    data object Success : KsenaxPostInstallVerificationState

    data class Failure(
        val stage: KsenaxPostInstallVerificationFailureStage,
        val message: String,
    ) : KsenaxPostInstallVerificationState
}

/**
 * Этап, на котором остановилась post-install проверка.
 *
 * @since 0.3
 */
enum class KsenaxPostInstallVerificationFailureStage {
    Presence,
    Integrity,
    Reachability,
}
