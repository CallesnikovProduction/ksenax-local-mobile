package com.kolesnikovprod.ksetaorch.download

import android.content.SharedPreferences
import com.kolesnikovprod.ksetaorch.download.domain.DownloadIdPreferencesStore
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallCheckState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallFailure
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Target ownership and durable terminal records, including failed disk writes. @since 0.4 */
class DownloadIdPreferencesStoreTest {
    private val preferences = FakePreferences()
    private val target = KsenaxInstallTarget.GEMMA_4_E2B

    private fun store(
        prefs: FakePreferences = preferences,
        installTarget: KsenaxInstallTarget = target,
        legacyKey: String? = null,
    ) = DownloadIdPreferencesStore(prefs, installTarget, legacyKey)

    @Test
    fun concurrentInstancesCreateOnlyOneDownload() {
        val stores = listOf(store(), store())
        val calls = AtomicInteger()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (0 until 8).map { index ->
                executor.submit(Callable {
                    ready.countDown()
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    stores[index % 2].getOrCreate { calls.incrementAndGet().toLong() }
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            results.forEach { assertEquals(1L, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
            assertEquals(1L, store(preferences.reopen()).get())
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun runIfMatchesHoldsTheLockAcrossInstances() {
        val first = store()
        val second = store()
        first.getOrCreate { 42L }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val clearStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val action = executor.submit(Callable {
                first.runIfMatches(42L) {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    true
                }
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val clear = executor.submit(Callable {
                clearStarted.countDown()
                second.clearIfMatches(42L)
            })
            assertTrue(clearStarted.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { clear.get(100, TimeUnit.MILLISECONDS) }
            release.countDown()
            assertTrue(action.get(5, TimeUnit.SECONDS))
            assertTrue(clear.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun staleOperationsDoNotMutateReplacementOrRunCleanup() {
        val old = store()
        val replacement = store()
        old.getOrCreate { 41L }
        assertTrue(old.clearIfMatches(41L))
        replacement.getOrCreate { 42L }
        val commits = preferences.commits
        assertFalse(old.clearIfMatches(41L))
        assertFalse(old.clearWithCleanupIfMatches(41L) { error("Stale cleanup") })
        assertFalse(old.runIfMatches(41L) { error("Stale action") })
        assertFalse(old.finishIfMatches(41L, KsenaxInstallSnapshot(isCancelled = true)) {
            error("Stale finish")
        })
        assertEquals(commits, preferences.commits)
        assertEquals(42L, replacement.get())
        assertNull(replacement.terminalSnapshot())
    }

    @Test
    fun noDownloadSentinelAllowsLocalInstalledRecovery() {
        val store = store()
        var actions = 0
        assertTrue(store.runIfMatches(NO_DOWNLOAD_ID) { actions++; true })
        assertEquals(1, actions)
        assertEquals(0, preferences.commits)
        val terminal = KsenaxInstallSnapshot(
            isInstalled = true,
            preparationState = KsenaxInstallCheckState.SUCCESS,
            hasCandidate = KsenaxInstallCheckState.SUCCESS,
            isValidInstallation = KsenaxInstallCheckState.SUCCESS,
            downloadState = KsenaxDownloadState.SUCCESSFUL,
            downloadProgress = 1f,
        )
        assertTrue(store.finishIfMatches(NO_DOWNLOAD_ID, terminal) { actions++ })
        assertEquals(2, actions)
        assertEquals(1, preferences.commits)
        val reconstructed = store(preferences.reopen())
        assertEquals(NO_DOWNLOAD_ID, reconstructed.get())
        assertEquals(terminal, reconstructed.terminalSnapshot())
    }

    @Test
    fun noDownloadSentinelCannotMutateActiveOrLegacyTask() {
        listOf("${target.id}_active_download_id", "active_model_download_id").forEach { key ->
            val prefs = FakePreferences()
            prefs.edit().putLong(key, 42L).commit()
            val store = store(prefs)
            val commits = prefs.commits
            assertFalse(store.runIfMatches(NO_DOWNLOAD_ID) { error("Active task exists") })
            assertFalse(store.clearIfMatches(NO_DOWNLOAD_ID))
            assertFalse(store.clearWithCleanupIfMatches(NO_DOWNLOAD_ID) { error("Active cleanup") })
            assertFalse(store.finishIfMatches(NO_DOWNLOAD_ID, KsenaxInstallSnapshot(isInstalled = true)) {
                error("Active finish")
            })
            assertEquals(commits, prefs.commits)
            assertEquals(42L, store.get())
            assertNull(store.terminalSnapshot())
        }
    }

    @Test
    fun noDownloadSentinelAllowsCompareClearWhenNoActiveTaskExists() {
        val store = store()
        var cleanups = 0
        assertTrue(store.clearIfMatches(NO_DOWNLOAD_ID))
        assertTrue(store.clearWithCleanupIfMatches(NO_DOWNLOAD_ID) { cleanups++ })
        assertEquals(1, cleanups)
        assertEquals(NO_DOWNLOAD_ID, store.get())
    }

    @Test
    fun otherNonPositiveExpectedIdsAreRejected() {
        val store = store()
        listOf(0L, -2L, Long.MIN_VALUE).forEach { id ->
            assertFalse(store.runIfMatches(id) { error("Invalid ID") })
            assertFalse(store.clearIfMatches(id))
            assertFalse(store.clearWithCleanupIfMatches(id) { error("Invalid cleanup") })
            assertFalse(store.finishIfMatches(id, KsenaxInstallSnapshot(isInstalled = true)) {
                error("Invalid finish")
            })
        }
        assertEquals(0, preferences.commits)
    }

    @Test
    fun savedIdSkipsRecoveryAndCreation() {
        store().getOrCreate { 42L }
        assertEquals(42L, store().getOrCreate(
            createDownload = { error("Duplicate enqueue") },
            recoverDownload = { error("Unnecessary recovery") },
        ))
    }

    @Test
    fun orphanRecoveryIsPersistedWithoutEnqueue() {
        assertEquals(73L, store().getOrCreate(
            createDownload = { error("Recovered task must be reused") },
            recoverDownload = { 73L },
        ))
        assertEquals(73L, store(preferences.reopen()).get())
    }

    @Test
    fun concurrentInstancesRecoverOnlyOnce() {
        val recoveries = AtomicInteger()
        val recover = { recoveries.incrementAndGet(); 73L }
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(store(), store()).map { store ->
                executor.submit(Callable {
                    ready.countDown()
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    store.getOrCreate({ error("Duplicate enqueue") }, recover)
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            results.forEach { assertEquals(73L, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, recoveries.get())
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun invalidRecoveryFallsBackToCreation() {
        listOf<Long?>(null, NO_DOWNLOAD_ID, 0L, -3L).forEach { recovered ->
            val prefs = FakePreferences()
            assertEquals(42L, store(prefs).getOrCreate({ 42L }, { recovered }))
        }
    }

    @Test
    fun nonPositiveCreatedIdsAreRejectedWithoutPersistence() {
        listOf(NO_DOWNLOAD_ID, 0L, -3L).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { store().getOrCreate { id } }
        }
        assertEquals(0, preferences.commits)
        assertEquals(NO_DOWNLOAD_ID, store().get())
    }

    @Test
    fun cancelledOutcomeSurvivesReconstructionWithoutTransientState() {
        val store = store()
        store.getOrCreate { 42L }
        val commits = preferences.commits
        var cleaned = false
        assertTrue(store.finishIfMatches(42L, KsenaxInstallSnapshot(
            currentDownloadId = 42L,
            isCancelled = true,
            downloadProgress = 0.7f,
            downloadState = KsenaxDownloadState.RUNNING,
            transferMetrics = KsenaxDownloadTransferMetrics(downloadedBytes = 100L),
        )) {
            assertEquals(42L, store.get())
            cleaned = true
        })
        assertTrue(cleaned)
        assertEquals(commits + 1, preferences.commits)
        val reconstructed = store(preferences.reopen())
        assertEquals(NO_DOWNLOAD_ID, reconstructed.get())
        assertEquals(KsenaxInstallSnapshot(isCancelled = true), reconstructed.terminalSnapshot())
    }

    @Test
    fun failedOutcomePreservesFailureAndCheckResults() {
        KsenaxInstallFailure.entries.forEach { failure ->
            val prefs = FakePreferences()
            val store = store(prefs)
            store.getOrCreate { 42L }
            val terminal = KsenaxInstallSnapshot(
                isInterrupted = true,
                preparationState = KsenaxInstallCheckState.SUCCESS,
                hasCandidate = KsenaxInstallCheckState.SUCCESS,
                isValidInstallation = KsenaxInstallCheckState.FAILURE,
                downloadState = KsenaxDownloadState.FAILED,
                failure = failure,
            )
            assertTrue(store.finishIfMatches(42L, terminal))
            assertEquals(terminal, store(prefs.reopen()).terminalSnapshot())
        }
    }

    @Test
    fun installedOutcomePreservesSuccessfulChecks() {
        val store = store()
        store.getOrCreate { 42L }
        val terminal = KsenaxInstallSnapshot(
            isInstalled = true,
            preparationState = KsenaxInstallCheckState.SUCCESS,
            hasCandidate = KsenaxInstallCheckState.SUCCESS,
            isValidInstallation = KsenaxInstallCheckState.SUCCESS,
            downloadState = KsenaxDownloadState.SUCCESSFUL,
            downloadProgress = 1f,
        )
        assertTrue(store.finishIfMatches(42L, terminal))
        assertEquals(terminal, store(preferences.reopen()).terminalSnapshot())
    }

    @Test
    fun startingOrRecoveringNewTaskClearsPreviousTerminalRecord() {
        listOf<Long?>(null, 73L).forEach { recovered ->
            val prefs = FakePreferences()
            val store = store(prefs)
            store.getOrCreate { 42L }
            store.finishIfMatches(42L, KsenaxInstallSnapshot(isCancelled = true))
            assertEquals(recovered ?: 43L, store.getOrCreate({ 43L }, { recovered }))
            assertNull(store(prefs.reopen()).terminalSnapshot())
        }
    }

    @Test
    fun cleanupExceptionLeavesOwnershipAndTerminalUntouched() {
        val store = store()
        store.getOrCreate { 42L }
        assertThrows(IllegalStateException::class.java) {
            store.finishIfMatches(42L, KsenaxInstallSnapshot(isCancelled = true)) {
                error("Cleanup failed")
            }
        }
        assertEquals(42L, store(preferences.reopen()).get())
        assertNull(store.terminalSnapshot())
    }

    @Test
    fun invalidTerminalSnapshotIsRejectedBeforeCleanup() {
        val store = store()
        store.getOrCreate { 42L }
        listOf(
            KsenaxInstallSnapshot(),
            KsenaxInstallSnapshot(isInstalled = true, isCancelled = true),
            KsenaxInstallSnapshot(isInstalled = true, isDownloading = true),
            KsenaxInstallSnapshot(isInstalled = true, isValidating = true),
        ).forEach { snapshot ->
            assertThrows(IllegalArgumentException::class.java) {
                store.finishIfMatches(42L, snapshot) { error("Invalid cleanup") }
            }
        }
        assertEquals(42L, store.get())
    }

    @Test
    fun failedEnqueueCommitIsNotReportedAsSavedAndOrphanCanBeRecovered() {
        preferences.failCommits = 2
        assertThrows(IllegalStateException::class.java) { store().getOrCreate { 42L } }
        assertEquals(NO_DOWNLOAD_ID, store().get())
        assertEquals(NO_DOWNLOAD_ID, store(preferences.reopen()).get())
        assertEquals(42L, store().getOrCreate({ error("Orphan duplicated") }, { 42L }))
        assertEquals(42L, store(preferences.reopen()).get())
    }

    @Test
    fun failedTerminalCommitRestoresIdAndDoesNotPublishOutcome() {
        val store = store()
        store.getOrCreate { 42L }
        preferences.failCommits = 2
        assertThrows(IllegalStateException::class.java) {
            store.finishIfMatches(42L, KsenaxInstallSnapshot(isCancelled = true))
        }
        listOf(store(), store(preferences.reopen())).forEach {
            assertEquals(42L, it.get())
            assertNull(it.terminalSnapshot())
        }
    }

    @Test
    fun failedClearCommitDoesNotReportSuccessOrEraseOwnership() {
        store().getOrCreate { 42L }
        preferences.failCommits = 2
        assertThrows(IllegalStateException::class.java) { store().clearIfMatches(42L) }
        assertEquals(42L, store().get())
        assertEquals(42L, store(preferences.reopen()).get())
    }

    @Test
    fun failedStartPreservesPreviousOutcomeAndOtherTargets() {
        listOf(1, 2).forEach { failedCommits ->
            val prefs = FakePreferences()
            val store = store(prefs)
            val otherTarget = KsenaxInstallTarget.VOSK_RU_SMALL
            store(prefs, otherTarget).getOrCreate { 99L }
            store.getOrCreate { 42L }
            store.finishIfMatches(42L, KsenaxInstallSnapshot(isCancelled = true))
            prefs.failCommits = failedCommits
            assertThrows(IllegalStateException::class.java) { store.getOrCreate { 43L } }
            listOf(prefs, prefs.reopen()).forEach { restored ->
                assertEquals(NO_DOWNLOAD_ID, store(restored).get())
                assertEquals(KsenaxInstallSnapshot(isCancelled = true), store(restored).terminalSnapshot())
                assertEquals(99L, store(restored, otherTarget).get())
            }
        }
    }

    @Test
    fun gemmaLegacyIdIsVisibleToInstancesWithoutExplicitLegacyKey() {
        preferences.edit().putLong("active_model_download_id", 42L).commit()
        assertEquals(42L, store().get())
        assertEquals(42L, store(legacyKey = "active_model_download_id").get())
        assertEquals(42L, store().getOrCreate { error("Legacy task duplicated") })
        assertTrue(store().finishIfMatches(42L, KsenaxInstallSnapshot(isCancelled = true)))
        assertFalse(preferences.contains("active_model_download_id"))
        assertEquals(NO_DOWNLOAD_ID, store(preferences.reopen()).get())
    }

    @Test
    fun targetIdWinsOverLegacyAndClearRemovesBoth() {
        preferences.edit().putLong("active_model_download_id", 41L)
            .putLong("${target.id}_active_download_id", 42L).commit()
        assertEquals(42L, store().get())
        assertFalse(store().clearIfMatches(41L))
        assertTrue(store().clearIfMatches(42L))
        assertFalse(preferences.contains("active_model_download_id"))
        assertEquals(NO_DOWNLOAD_ID, store(preferences.reopen()).get())
    }

    @Test
    fun legacyFallbackIsTargetSpecificAndInvalidIdsAreIgnored() {
        preferences.edit().putLong("active_model_download_id", 42L)
            .putLong("${target.id}_active_download_id", 0L).commit()
        assertEquals(42L, store().get())
        val other = store(installTarget = KsenaxInstallTarget.VOSK_RU_SMALL)
        assertEquals(NO_DOWNLOAD_ID, other.get())
        other.clear()
        assertEquals(42L, store().get())
    }

    @Test
    fun existingClearMethodsAndCustomLegacyKeyRemainSupported() {
        preferences.edit().putLong("legacy_vosk", 42L).commit()
        val store = store(installTarget = KsenaxInstallTarget.VOSK_RU_SMALL, legacyKey = "legacy_vosk")
        var cleanups = 0
        assertTrue(store.clearWithCleanupIfMatches(42L) { cleanups++ })
        assertEquals(NO_DOWNLOAD_ID, store.get())
        store.getOrCreate { 43L }
        store.clearWithCleanup { cleanups++ }
        assertEquals(NO_DOWNLOAD_ID, store.get())
        store.getOrCreate { 44L }
        store.clear()
        assertEquals(NO_DOWNLOAD_ID, store.get())
        assertEquals(2, cleanups)
        assertEquals(NO_DOWNLOAD_ID, store(
            preferences.reopen(),
            installTarget = KsenaxInstallTarget.VOSK_RU_SMALL,
            legacyKey = "legacy_vosk",
        ).get())
    }

    @Test
    fun matchingActionReturnsItsResultWithoutClearingOwnership() {
        store().getOrCreate { 42L }
        assertFalse(store().runIfMatches(42L) { false })
        assertTrue(store().runIfMatches(42L) { true })
        assertEquals(42L, store().get())
    }

    @Test
    fun unknownFailureNameIsRecoveredAsUnknown() {
        store().getOrCreate { 42L }
        store().finishIfMatches(42L, KsenaxInstallSnapshot(isInterrupted = true))
        preferences.edit().putString("${target.id}_terminal_failure", "FUTURE_FAILURE").commit()
        assertEquals(KsenaxInstallFailure.UNKNOWN, store(preferences.reopen()).terminalSnapshot()?.failure)
    }

    private class FakePreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
        private val memory = initial.toMutableMap()
        private var disk = initial.toMap()
        var failCommits = 0
        var commits = 0
            private set

        @Synchronized fun reopen() = FakePreferences(disk)
        @Synchronized override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
        @Synchronized override fun contains(key: String?) = memory.containsKey(key)
        @Synchronized override fun getLong(key: String?, defValue: Long) = memory[key] as? Long ?: defValue
        @Synchronized override fun getString(key: String?, defValue: String?) = memory[key] as? String ?: defValue
        @Synchronized override fun getInt(key: String?, defValue: Int) = memory[key] as? Int ?: defValue
        @Synchronized override fun getFloat(key: String?, defValue: Float) = memory[key] as? Float ?: defValue
        @Synchronized override fun getBoolean(key: String?, defValue: Boolean) = memory[key] as? Boolean ?: defValue
        @Suppress("UNCHECKED_CAST")
        @Synchronized override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (memory[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            private var clear = false
            override fun putLong(key: String?, value: Long) = change(key, value)
            override fun putString(key: String?, value: String?) = change(key, value)
            override fun putInt(key: String?, value: Int) = change(key, value)
            override fun putFloat(key: String?, value: Float) = change(key, value)
            override fun putBoolean(key: String?, value: Boolean) = change(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?) = change(key, values?.toSet())
            override fun remove(key: String?) = change(key, null)
            override fun clear(): SharedPreferences.Editor = apply { clear = true }
            private fun change(key: String?, value: Any?): SharedPreferences.Editor = apply {
                updates[requireNotNull(key)] = value
            }

            override fun commit(): Boolean = synchronized(this@FakePreferences) {
                commits++
                if (clear) memory.clear()
                updates.forEach { (key, value) ->
                    if (value == null) memory.remove(key) else memory[key] = value
                }
                // Match Android: memory changes even when writing to disk fails.
                if (failCommits > 0) {
                    failCommits--
                    false
                } else {
                    disk = memory.toMap()
                    true
                }
            }

            override fun apply(): Unit = error("Store must use commit, not apply")
        }
    }
}
