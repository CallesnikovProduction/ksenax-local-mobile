package com.kolesnikovprod.ksetaorch.download.domain

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Единая точка подготовки и глубокой проверки скачанного model artifact.
 *
 * Финализатор используется как UI-coordinator-ом, так и фоновым Worker-ом.
 * Операции одного [KsenaxInstallTarget] сериализуются, поэтому два наблюдателя
 * не могут одновременно распаковывать Vosk или удалять проверяемый файл.
 *
 * [expectedDownloadId] защищает от запоздалой финализации: если пользователь
 * уже отменил задачу или начал новую, старый владелец не подтверждает установку.
 * `null` означает локальное восстановление, для которого ожидается отсутствие
 * активного сохранённого id.
 *
 * @since 0.3
 */
internal class InstallCandidateFinalizer(
    private val installUseCase: KsenaxModelInstallUseCase,
) {

    suspend fun finalize(
        expectedDownloadId: Long? = null,
        onEvent: (InstallFinalizationEvent) -> Unit = {},
    ): InstallFinalizationOutcome {
        return InstallFinalizationGate.withTargetLock(
            installTarget = installUseCase.installTarget,
        ) {
            resolveSupersededOutcome(expectedDownloadId)?.let { outcome ->
                return@withTargetLock outcome
            }

            onEvent(InstallFinalizationEvent.PreparationStarted)
            val isPrepared = installUseCase.prepareInstallCandidate()
            onEvent(
                InstallFinalizationEvent.PreparationFinished(
                    isSuccessful = isPrepared,
                )
            )

            resolveSupersededOutcome(expectedDownloadId)?.let { outcome ->
                return@withTargetLock outcome
            }

            if (!isPrepared) {
                return@withTargetLock clearInvalidCandidate(expectedDownloadId)
            }

            onEvent(InstallFinalizationEvent.ValidationStarted)
            val isValid = installUseCase.hasValidInstallation()
            onEvent(
                InstallFinalizationEvent.ValidationFinished(
                    isSuccessful = isValid,
                )
            )

            resolveSupersededOutcome(expectedDownloadId)?.let { outcome ->
                return@withTargetLock outcome
            }

            if (isValid) {
                completeValidInstallation(expectedDownloadId)
            } else {
                clearInvalidCandidate(expectedDownloadId)
            }
        }
    }

    private suspend fun completeValidInstallation(
        expectedDownloadId: Long?,
    ): InstallFinalizationOutcome {
        val ownerId = expectedDownloadId ?: NO_DOWNLOAD_ID
        if (installUseCase.clearSavedDownloadIdIfOwnedBy(ownerId)) {
            return InstallFinalizationOutcome.INSTALLED
        }

        return resolveSupersededOutcome(expectedDownloadId)
            ?: InstallFinalizationOutcome.SUPERSEDED
    }

    private suspend fun clearInvalidCandidate(
        expectedDownloadId: Long?,
    ): InstallFinalizationOutcome {
        val ownerId = expectedDownloadId ?: NO_DOWNLOAD_ID
        if (installUseCase.clearArtifactsIfOwnedBy(ownerId)) {
            return InstallFinalizationOutcome.INVALID
        }

        return resolveSupersededOutcome(expectedDownloadId)
            ?: InstallFinalizationOutcome.SUPERSEDED
    }

    /**
     * Отличает пользовательскую отмену/замену задачи от уже завершившегося
     * параллельного финализатора.
     */
    private suspend fun resolveSupersededOutcome(
        expectedDownloadId: Long?,
    ): InstallFinalizationOutcome? {
        val ownerId = expectedDownloadId ?: NO_DOWNLOAD_ID
        if (installUseCase.getSavedDownloadId() == ownerId) {
            return null
        }

        return if (installUseCase.hasValidInstallation()) {
            InstallFinalizationOutcome.INSTALLED
        } else {
            InstallFinalizationOutcome.SUPERSEDED
        }
    }
}

/**
 * Терминальный результат одной попытки финализации.
 */
internal enum class InstallFinalizationOutcome {
    INSTALLED,
    INVALID,
    SUPERSEDED,
}

/**
 * Внутренние события финализации для отображения этапов без дублирования
 * prepare/validate-алгоритма в UI-coordinator-е.
 */
internal sealed interface InstallFinalizationEvent {
    data object PreparationStarted : InstallFinalizationEvent

    data class PreparationFinished(
        val isSuccessful: Boolean,
    ) : InstallFinalizationEvent

    data object ValidationStarted : InstallFinalizationEvent

    data class ValidationFinished(
        val isSuccessful: Boolean,
    ) : InstallFinalizationEvent
}

/**
 * Process-level сериализация финализации по install target.
 */
private object InstallFinalizationGate {
    private val targetMutexes = ConcurrentHashMap<KsenaxInstallTarget, Mutex>()

    suspend fun <T> withTargetLock(
        installTarget: KsenaxInstallTarget,
        action: suspend () -> T,
    ): T {
        val mutex = targetMutexes.computeIfAbsent(installTarget) { Mutex() }
        mutex.lock()

        return try {
            action()
        } finally {
            mutex.unlock()
        }
    }
}
