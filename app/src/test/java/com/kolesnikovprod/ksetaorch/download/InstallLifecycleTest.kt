package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.*
import com.kolesnikovprod.ksetaorch.download.domain.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class InstallLifecycleTest {
    @Test(expected = IllegalArgumentException::class) fun nonFiniteTaskProgressIsRejected() {
        KsenaxDownloadTaskSnapshot(Float.NaN, KsenaxDownloadState.RUNNING)
    }

    @Test fun unknownTotalIsSafe() {
        val snapshot = InstallSnapshots.transfer(12, KsenaxDownloadTaskSnapshot(0f, KsenaxDownloadState.RUNNING))
        assertEquals(0f, snapshot.downloadProgress)
        assertNull(snapshot.transferMetrics.totalBytes)
        assertFalse(snapshot.isInstalled)
    }

    @Test fun completionIsNotInstallation() {
        val snapshot = InstallSnapshots.transfer(12, KsenaxDownloadTaskSnapshot(1f, KsenaxDownloadState.SUCCESSFUL))
        assertFalse(snapshot.isInstalled)
        assertNotEquals(KsenaxInstallCheckState.SUCCESS, snapshot.isValidInstallation)
    }

    @Test fun permanentHttpErrorsAreNotRetryable() {
        assertFalse(DownloadFailureClassifier.classify(404).isRetryable)
        assertFalse(DownloadFailureClassifier.classify(403).isRetryable)
        assertFalse(DownloadFailureClassifier.classify(1006).isRetryable)
        assertTrue(DownloadFailureClassifier.classify(429).isRetryable)
        assertTrue(DownloadFailureClassifier.classify(503).isRetryable)
    }

    @Test fun observerCancellationDoesNotCancelTransfer() = runTest {
        val useCase = FakeUseCase()
        val coordinator = KsenaxModelInstallCoordinator(useCase)
        val observer = launch { coordinator.observeInstallState(coordinator.initialSnapshot()) {} }
        runCurrent()
        observer.cancelAndJoin()
        assertEquals(12L, useCase.id)
        assertEquals(0, useCase.cancellations)
        assertEquals(0, useCase.cleaned)
    }

    @Test fun staleCancelCannotCancelNewTask() {
        val useCase = FakeUseCase().apply { id = 99 }
        val result = KsenaxModelInstallCoordinator(useCase).cancelDownload(InstallSnapshots.queued(12))
        assertEquals(99L, result.currentDownloadId)
        assertEquals(0, useCase.cancellations)
    }

    @Test fun staleLocalInstalledFlagIsRevalidated() = runTest {
        val useCase = FakeUseCase().apply { id = NO_DOWNLOAD_ID; candidate = false }
        var result = InstallSnapshots.installed()
        KsenaxModelInstallCoordinator(useCase).observeInstallState(result) { result = it }
        assertFalse(result.isInstalled)
    }

    @Test fun backgroundCancellationReplacesStaleUiProgress() = runTest {
        val useCase = FakeUseCase().apply { id = NO_DOWNLOAD_ID; terminal = InstallSnapshots.cancelled(); valid = false }
        var result = InstallSnapshots.queued(12)
        KsenaxModelInstallCoordinator(useCase).observeInstallState(result) { result = it }
        assertTrue(result.isCancelled)
        assertFalse(result.isDownloading)
    }

    @Test fun cancelledReplacementDoesNotHidePreviousVerifiedModel() = runTest {
        val useCase = FakeUseCase().apply { id = NO_DOWNLOAD_ID; terminal = InstallSnapshots.cancelled() }
        var result = KsenaxInstallSnapshot()
        KsenaxModelInstallCoordinator(useCase).observeInstallState(result) { result = it }
        assertTrue(result.isInstalled)
        assertFalse(result.isCancelled)
    }

    @Test fun replacementDuringPreparationCannotBeClearedByOldFinalizer() = runTest {
        val useCase = FakeUseCase().apply { onPrepare = { id = 99 } }
        val outcome = InstallCandidateFinalizer(useCase).finalize(12)
        assertEquals(InstallFinalizationOutcome.SUPERSEDED, outcome)
        assertEquals(99L, useCase.id)
        assertEquals(0, useCase.cleaned)
    }

    @Test fun cancelledPreparationLeavesCandidateForBackgroundRetry() = runTest {
        val useCase = FakeUseCase().apply { onPrepare = { throw CancellationException() } }
        try {
            InstallCandidateFinalizer(useCase).finalize(12)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(12L, useCase.id)
        assertEquals(0, useCase.cleaned)
    }

    @Test fun invalidCandidateIsNeverInstalled() = runTest {
        val useCase = FakeUseCase().apply { valid = false }
        assertEquals(InstallFinalizationOutcome.INVALID, InstallCandidateFinalizer(useCase).finalize(12))
        assertFalse(useCase.terminal!!.isInstalled)
        assertTrue(useCase.terminal!!.isInterrupted)
    }

    @Test fun providerOutageRetainsTaskAndDoesNotDeleteFiles() = runTest {
        val useCase = FakeUseCase().apply { queryError = IOException("Provider unavailable") }
        var result = InstallSnapshots.queued(12)
        KsenaxModelInstallCoordinator(useCase).observeInstallState(result) { result = it }
        assertEquals(12L, useCase.id)
        assertEquals(0, useCase.cleaned)
        assertEquals(0, useCase.cancellations)
        assertEquals(KsenaxInstallFailure.STORAGE, result.failure)
    }

    private class FakeUseCase : KsenaxModelInstallUseCase {
        override val installTarget = KsenaxInstallTarget.FUNCTION_GEMMA_270M
        var id = 12L
        var valid = true
        var candidate = true
        var cleaned = 0
        var cancellations = 0
        var terminal: KsenaxInstallSnapshot? = null
        var onPrepare: () -> Unit = {}
        var queryError: Exception? = null
        override fun startDownloadAndSave(policy: KsenaxDownloadPolicy): Long = id
        override fun getSavedDownloadId(): Long = id
        override fun persistedSnapshot() = terminal
        override fun cancelDownload(downloadId: Long) { cancellations++; id = NO_DOWNLOAD_ID }
        override fun clearArtifacts() { cleaned++; id = NO_DOWNLOAD_ID; terminal = InstallSnapshots.failed(KsenaxInstallFailure.INTEGRITY) }
        override fun clearSavedDownloadId() { id = NO_DOWNLOAD_ID; terminal = InstallSnapshots.installed() }
        override fun deleteLocalArtifacts() = true
        override suspend fun hasInstallCandidate() = candidate
        override suspend fun prepareInstallCandidate(): Boolean { onPrepare(); return true }
        override suspend fun hasValidInstallation() = valid
        override fun queryDownloadSnapshot(downloadId: Long): KsenaxDownloadTaskSnapshot {
            queryError?.let { throw it }
            return KsenaxDownloadTaskSnapshot(0.5f, KsenaxDownloadState.RUNNING)
        }
        override fun getInstalledPath() = "unused-model"
    }
}
