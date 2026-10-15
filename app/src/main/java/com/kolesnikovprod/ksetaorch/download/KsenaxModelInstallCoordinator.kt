package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.*
import com.kolesnikovprod.ksetaorch.download.domain.data.*
import com.kolesnikovprod.ksetaorch.download.platform.InsufficientModelStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Команды и наблюдение установки. HTTP принадлежит DownloadManager, фоновая
 * сверка и финализация запускаются use case независимо от observer-а.
 * Callback доставляется в контексте вызывающего; файловый/provider IO вынесен
 * на Dispatchers.IO. Отмена observer-а не отменяет системную задачу.
 * @since 0.4
 */
class KsenaxModelInstallCoordinator(
    private val installUseCase: KsenaxModelInstallUseCase,
) {
    private val finalizer = InstallCandidateFinalizer(installUseCase)

    fun initialSnapshot(): KsenaxInstallSnapshot = try {
        installUseCase.scheduleReconciliation()
        val id = installUseCase.getSavedDownloadId()
        if (id != NO_DOWNLOAD_ID) InstallSnapshots.queued(id)
        else installUseCase.persistedSnapshot() ?: KsenaxInstallSnapshot()
    } catch (_: Exception) {
        // Preserve a known identity even when the system provider is temporarily unavailable.
        val id = installUseCase.getSavedDownloadId()
        if (id != NO_DOWNLOAD_ID) InstallSnapshots.queued(id)
        else InstallSnapshots.failed(KsenaxInstallFailure.STORAGE)
    }

    fun startDownload(
        currentSnapshot: KsenaxInstallSnapshot,
        policy: KsenaxDownloadPolicy = KsenaxDownloadPolicy(),
    ): KsenaxInstallSnapshot = try {
        val id = installUseCase.startDownloadAndSave(policy)
        if (id <= 0L) InstallSnapshots.failed(KsenaxInstallFailure.UNKNOWN)
        else if (currentSnapshot.currentDownloadId == id && currentSnapshot.isDownloading) currentSnapshot
        else InstallSnapshots.queued(id)
    } catch (exception: Exception) {
        val reason = when (exception) {
            is InsufficientModelStorageException -> KsenaxInstallFailure.INSUFFICIENT_STORAGE
            is IOException -> KsenaxInstallFailure.STORAGE
            else -> KsenaxInstallFailure.UNKNOWN
        }
        // Enqueue may have succeeded before a scheduling failure. Keep that task observable.
        val id = installUseCase.getSavedDownloadId()
        if (id != NO_DOWNLOAD_ID) InstallSnapshots.queued(id)
        else {
            runCatching { installUseCase.failDownloadIfOwnedBy(NO_DOWNLOAD_ID, reason) }
            InstallSnapshots.failed(reason)
        }
    }

    fun cancelDownload(currentSnapshot: KsenaxInstallSnapshot): KsenaxInstallSnapshot {
        val id = installUseCase.getSavedDownloadId()
        if (id != currentSnapshot.currentDownloadId) {
            return if (id != NO_DOWNLOAD_ID) InstallSnapshots.queued(id)
            else installUseCase.persistedSnapshot() ?: currentSnapshot
        }
        return try {
            installUseCase.cancelDownload(id)
            installUseCase.persistedSnapshot() ?: InstallSnapshots.cancelled()
        } catch (_: Exception) {
            InstallSnapshots.failed(KsenaxInstallFailure.STORAGE).copy(currentDownloadId = id)
        }
    }

    suspend fun observeInstallState(
        initialSnapshot: KsenaxInstallSnapshot,
        onSnapshotChanged: (KsenaxInstallSnapshot) -> Unit,
    ) {
        var snapshot = initialSnapshot
        suspend fun publish(next: KsenaxInstallSnapshot) {
            currentCoroutineContext().ensureActive()
            snapshot = next
            onSnapshotChanged(next)
        }

        var queryFailures = 0
        try {
            withContext(Dispatchers.IO) { installUseCase.reconcileDownload() }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The separately scheduled recovery worker retries provider outages.
            publish(InstallSnapshots.failed(KsenaxInstallFailure.STORAGE).copy(
                currentDownloadId = installUseCase.getSavedDownloadId(),
            ))
            return
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            val id = installUseCase.getSavedDownloadId()
            if (id == NO_DOWNLOAD_ID) {
                val terminal = installUseCase.persistedSnapshot()
                if (terminal?.isCancelled == true || terminal?.isInterrupted == true) {
                    publish(if (installUseCase.hasValidInstallation()) {
                        InstallSnapshots.installed().copy(failure = terminal.failure)
                    } else terminal)
                    return
                }
                publish(KsenaxInstallSnapshot(
                    isValidating = true,
                    hasCandidate = KsenaxInstallCheckState.LOADING,
                    isValidInstallation = KsenaxInstallCheckState.LOADING,
                ))
                if (!installUseCase.hasInstallCandidate()) {
                    publish(KsenaxInstallSnapshot())
                    return
                }
                val outcome = finalize(null) { event ->
                    snapshot = snapshot.withEvent(event)
                    onSnapshotChanged(snapshot)
                }
                if (outcome == InstallFinalizationOutcome.SUPERSEDED) continue
                publish(if (outcome == InstallFinalizationOutcome.INSTALLED) InstallSnapshots.installed()
                    else installUseCase.persistedSnapshot() ?: InstallSnapshots.failed(KsenaxInstallFailure.INTEGRITY))
                return
            }

            val task = try {
                withContext(Dispatchers.IO) { installUseCase.queryDownloadSnapshot(id) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                queryFailures++
                if (queryFailures >= 3) {
                    publish(InstallSnapshots.failed(KsenaxInstallFailure.STORAGE).copy(currentDownloadId = id))
                    return
                }
                delay(500L)
                continue
            }
            queryFailures = 0
            if (installUseCase.getSavedDownloadId() != id) continue

            if (task == null) {
                if (installUseCase.hasValidInstallation()) {
                    withContext(Dispatchers.IO) { installUseCase.clearSavedDownloadIdIfOwnedBy(id) }
                } else {
                    withContext(Dispatchers.IO) {
                        installUseCase.failDownloadIfOwnedBy(id, KsenaxInstallFailure.MISSING_TASK)
                    }
                }
                continue
            }

            when (task.state) {
                KsenaxDownloadState.FAILED -> {
                    withContext(Dispatchers.IO) {
                        installUseCase.failDownloadIfOwnedBy(id, DownloadFailureClassifier.classify(task.reasonCode))
                    }
                }
                KsenaxDownloadState.SUCCESSFUL -> {
                    publish(InstallSnapshots.transfer(id, task).copy(
                        isDownloading = false,
                        isValidating = true,
                        preparationState = KsenaxInstallCheckState.LOADING,
                        hasCandidate = KsenaxInstallCheckState.SUCCESS,
                    ))
                    val outcome = finalize(id) { event ->
                        snapshot = snapshot.withEvent(event)
                        onSnapshotChanged(snapshot)
                    }
                    if (outcome == InstallFinalizationOutcome.SUPERSEDED) continue
                    publish(if (outcome == InstallFinalizationOutcome.INSTALLED) {
                        InstallSnapshots.installed(task.transferMetrics)
                    } else installUseCase.persistedSnapshot() ?: InstallSnapshots.failed(KsenaxInstallFailure.INTEGRITY))
                    return
                }
                else -> {
                    publish(InstallSnapshots.transfer(id, task))
                    delay(500L)
                }
            }
        }
    }

    private suspend fun finalize(
        id: Long?,
        onEvent: (InstallFinalizationEvent) -> Unit,
    ): InstallFinalizationOutcome = finalizer.finalize(id, onEvent)

    private fun KsenaxInstallSnapshot.withEvent(event: InstallFinalizationEvent): KsenaxInstallSnapshot =
        when (event) {
            InstallFinalizationEvent.PreparationStarted ->
                copy(isDownloading = false, isValidating = true, preparationState = KsenaxInstallCheckState.LOADING)
            is InstallFinalizationEvent.PreparationFinished ->
                copy(preparationState = if (event.isSuccessful) KsenaxInstallCheckState.SUCCESS else KsenaxInstallCheckState.FAILURE)
            InstallFinalizationEvent.ValidationStarted ->
                copy(isValidating = true, isValidInstallation = KsenaxInstallCheckState.LOADING)
            is InstallFinalizationEvent.ValidationFinished ->
                copy(isValidating = false, isValidInstallation =
                    if (event.isSuccessful) KsenaxInstallCheckState.SUCCESS else KsenaxInstallCheckState.FAILURE)
        }
}
