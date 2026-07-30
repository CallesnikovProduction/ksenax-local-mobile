package com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import kotlinx.coroutines.CancellationException

/**
 * Выполняет самостоятельную проверку модели сразу после download-handoff.
 *
 * Файловые стадии делегируются install use case, а runtime-достижимость —
 * переданному probe. Успех запоминается только в той foreground-сессии, в
 * которой проверка началась.
 *
 * @since 0.3
 */
class KsenaxPostInstallValidationController(
    private val installUseCase: KsenaxModelInstallUseCase,
    private val sessionRegistry: KsenaxModelVerificationSessionRegistry,
    private val reachabilityProbe: suspend () -> Unit,
) {
    suspend fun verify(
        onStageChanged: (KsenaxPostInstallValidationStage) -> Unit,
    ): KsenaxPostInstallValidationResult {
        val verificationSession = sessionRegistry.currentSession()

        onStageChanged(KsenaxPostInstallValidationStage.Presence)
        val hasCandidate = verifyBooleanStage {
            installUseCase.hasInstallCandidate()
        }
        if (!hasCandidate) {
            return KsenaxPostInstallValidationResult.Failure(
                stage = KsenaxPostInstallValidationStage.Presence,
            )
        }

        onStageChanged(KsenaxPostInstallValidationStage.Integrity)
        val isValid = verifyBooleanStage {
            installUseCase.hasValidInstallation()
        }
        if (!isValid) {
            return KsenaxPostInstallValidationResult.Failure(
                stage = KsenaxPostInstallValidationStage.Integrity,
            )
        }

        onStageChanged(KsenaxPostInstallValidationStage.Reachability)
        try {
            reachabilityProbe()
        } catch (exception: CancellationException) {
            throw exception
        } catch (error: Throwable) {
            return KsenaxPostInstallValidationResult.Failure(
                stage = KsenaxPostInstallValidationStage.Reachability,
                cause = error,
            )
        }

        return if (
            sessionRegistry.markVerified(
                modelKey = installUseCase.installTarget.id,
                expectedSession = verificationSession,
            )
        ) {
            KsenaxPostInstallValidationResult.Success
        } else {
            KsenaxPostInstallValidationResult.SessionExpired
        }
    }

    private suspend fun verifyBooleanStage(
        block: suspend () -> Boolean,
    ): Boolean {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Этап post-install проверки.
 *
 * @since 0.3
 */
enum class KsenaxPostInstallValidationStage {
    Presence,
    Integrity,
    Reachability,
}

/**
 * Результат post-install проверки.
 *
 * @since 0.3
 */
sealed interface KsenaxPostInstallValidationResult {
    data object Success : KsenaxPostInstallValidationResult
    data object SessionExpired : KsenaxPostInstallValidationResult

    data class Failure(
        val stage: KsenaxPostInstallValidationStage,
        val cause: Throwable? = null,
    ) : KsenaxPostInstallValidationResult
}
