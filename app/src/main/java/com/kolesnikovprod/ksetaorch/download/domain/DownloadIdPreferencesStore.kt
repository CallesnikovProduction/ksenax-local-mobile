package com.kolesnikovprod.ksetaorch.download.domain

import android.content.Context
import android.content.SharedPreferences
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallCheckState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallFailure
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import java.util.concurrent.ConcurrentHashMap

/**
 * Маленькое хранилище активного `downloadId` для конкретного install target.
 *
 * Use case не должен руками собирать preference-key: иначе Gemma/Vosk начнут
 * копировать одну и ту же механику и однажды разъедутся.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal class DownloadIdPreferencesStore internal constructor(
    private val preferences: SharedPreferences,
    installTarget: KsenaxInstallTarget,
    legacyDownloadIdKey: String? = null,
) {
    constructor(
        context: Context,
        installTarget: KsenaxInstallTarget,
        legacyDownloadIdKey: String? = null,
    ) : this(
        context.applicationContext.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        installTarget,
        legacyDownloadIdKey,
    )

    private val downloadIdKey = "${installTarget.id}_active_download_id"
    private val operationLock = operationLocks.computeIfAbsent(downloadIdKey) { Any() }
    private val legacyDownloadIdKeys = buildSet {
        if (installTarget == KsenaxInstallTarget.GEMMA_4_E2B) {
            add("active_model_download_id")
        }
        legacyDownloadIdKey?.let(::add)
    }
    private val terminalKey = "${installTarget.id}_terminal_outcome"
    private val failureKey = "${installTarget.id}_terminal_failure"
    private val preparationKey = "${installTarget.id}_terminal_preparation"
    private val candidateKey = "${installTarget.id}_terminal_candidate"
    private val validationKey = "${installTarget.id}_terminal_validation"
    private val terminalKeys = listOf(
        terminalKey, failureKey, preparationKey, candidateKey, validationKey,
    )
    private val ownedKeys = listOf(downloadIdKey) + legacyDownloadIdKeys + terminalKeys

    fun get(): Long {
        return synchronized(operationLock) {
            getLocked()
        }
    }

    /** Detects and adopts a system orphan under the same cancellation/ownership lock. @since 0.4 */
    fun recoverIfMissing(recoverDownload: () -> Long?): Long = synchronized(operationLock) {
        val current = getLocked()
        if (current != NO_DOWNLOAD_ID) return@synchronized current
        val recovered = recoverDownload()?.takeIf { it > 0L } ?: return@synchronized NO_DOWNLOAD_ID
        saveLocked(recovered)
        recovered
    }

    fun getOrCreate(createDownload: () -> Long): Long {
        return getOrCreate(createDownload, recoverDownload = { null })
    }

    /**
     * Reuses saved ownership, then recovers an orphan before creating a task.
     * Invalid recovered IDs are ignored; a created ID must be positive.
     * Persistence failure throws rather than reporting durable ownership.
     *
     * @since 0.4
     */
    fun getOrCreate(
        createDownload: () -> Long,
        recoverDownload: () -> Long? = { null },
    ): Long {
        return synchronized(operationLock) {
            val existingDownloadId = getLocked()
            if (existingDownloadId > 0L) {
                return@synchronized existingDownloadId
            }

            val downloadId = recoverDownload()?.takeIf { it > 0L } ?: createDownload()
            require(downloadId > 0L) {
                "DownloadManager returned a non-positive download ID"
            }
            saveLocked(downloadId)
            downloadId
        }
    }

    /**
     * Runs under the shared target lock for the expected ID, or for local recovery
     * when [NO_DOWNLOAD_ID] is expected and no active ID is saved.
     *
     * @since 0.4
     */
    fun runIfMatches(expectedDownloadId: Long, action: () -> Boolean): Boolean {
        return synchronized(operationLock) {
            if (!matchesLocked(expectedDownloadId)) return@synchronized false
            action()
        }
    }

    /**
     * Persists a terminal outcome and removes ownership in one checked commit.
     * Cleanup runs under the target lock, but filesystem effects cannot be rolled back
     * if persistence fails. No progress, speed or other transient state is stored.
     *
     * @since 0.4
     */
    fun finishIfMatches(
        expectedDownloadId: Long,
        snapshot: KsenaxInstallSnapshot,
        cleanup: () -> Unit = {},
    ): Boolean {
        return synchronized(operationLock) {
            if (!matchesLocked(expectedDownloadId)) return@synchronized false
            require(
                listOf(snapshot.isInstalled, snapshot.isInterrupted, snapshot.isCancelled)
                    .count { it } == 1 && !snapshot.isDownloading && !snapshot.isValidating,
            ) { "A single terminal install outcome is required" }
            val outcome = when {
                snapshot.isInstalled -> "installed"
                snapshot.isCancelled -> "cancelled"
                else -> "failed"
            }
            cleanup()
            commitLocked {
                remove(downloadIdKey)
                legacyDownloadIdKeys.forEach { remove(it) }
                putString(terminalKey, outcome)
                putString(failureKey, snapshot.failure?.name)
                putString(preparationKey, snapshot.preparationState.name)
                putString(candidateKey, snapshot.hasCandidate.name)
                putString(validationKey, snapshot.isValidInstallation.name)
            }
            true
        }
    }

    /** Reconstructs the last terminal outcome without restoring transient fields. @since 0.4 */
    fun terminalSnapshot(): KsenaxInstallSnapshot? {
        return synchronized(operationLock) {
            val outcome = preferences.getString(terminalKey, null)
            if (outcome !in listOf("installed", "failed", "cancelled")) {
                return@synchronized null
            }
            val failureName = preferences.getString(failureKey, null)
            KsenaxInstallSnapshot(
                isInstalled = outcome == "installed",
                isInterrupted = outcome == "failed",
                isCancelled = outcome == "cancelled",
                preparationState = checkStateLocked(preparationKey),
                hasCandidate = checkStateLocked(candidateKey),
                isValidInstallation = checkStateLocked(validationKey),
                downloadProgress = if (outcome == "installed") 1f else 0f,
                downloadState = when (outcome) {
                    "installed" -> KsenaxDownloadState.SUCCESSFUL
                    "failed" -> KsenaxDownloadState.FAILED
                    else -> KsenaxDownloadState.UNKNOWN
                },
                failure = failureName?.let { name ->
                    KsenaxInstallFailure.entries.firstOrNull { it.name == name }
                        ?: KsenaxInstallFailure.UNKNOWN
                },
            )
        }
    }

    fun clearWithCleanup(cleanup: () -> Unit) {
        synchronized(operationLock) {
            cleanup()
            clearLocked()
        }
    }

    fun clearWithCleanupIfMatches(
        expectedDownloadId: Long,
        cleanup: () -> Unit,
    ): Boolean {
        return synchronized(operationLock) {
            if (!matchesLocked(expectedDownloadId)) {
                return@synchronized false
            }

            cleanup()
            clearLocked()
            true
        }
    }

    fun clearIfMatches(expectedDownloadId: Long): Boolean {
        return synchronized(operationLock) {
            if (!matchesLocked(expectedDownloadId)) {
                return@synchronized false
            }

            clearLocked()
            true
        }
    }

    fun clear() {
        synchronized(operationLock) {
            clearLocked()
        }
    }

    private fun getLocked(): Long {
        val targetDownloadId = preferences.getLong(
            downloadIdKey,
            NO_DOWNLOAD_ID,
        )

        if (targetDownloadId > 0L) {
            return targetDownloadId
        }

        return legacyDownloadIdKeys.asSequence()
            .map { preferences.getLong(it, NO_DOWNLOAD_ID) }
            .firstOrNull { it > 0L } ?: NO_DOWNLOAD_ID
    }

    private fun matchesLocked(expectedDownloadId: Long): Boolean {
        return (expectedDownloadId > 0L || expectedDownloadId == NO_DOWNLOAD_ID) &&
            getLocked() == expectedDownloadId
    }

    private fun checkStateLocked(key: String): KsenaxInstallCheckState {
        val name = preferences.getString(key, null)
        return KsenaxInstallCheckState.entries.firstOrNull { it.name == name }
            ?: KsenaxInstallCheckState.NON_CONFIRMED
    }

    private fun saveLocked(downloadId: Long) {
        commitLocked {
            putLong(downloadIdKey, downloadId)
            legacyDownloadIdKeys.forEach { remove(it) }
            terminalKeys.forEach { remove(it) }
        }
    }

    private fun clearLocked() {
        commitLocked {
            remove(downloadIdKey)
            legacyDownloadIdKeys.forEach { remove(it) }
        }
    }

    private fun commitLocked(change: SharedPreferences.Editor.() -> Unit) {
        val previous = preferences.all.filterKeys { it in ownedKeys }
        if (!preferences.edit().apply(change).commit()) {
            // Android updates memory even when the disk commit fails. Restore only
            // this target's keys; disk rollback remains best-effort on IO failure.
            val rollback = preferences.edit()
            ownedKeys.forEach { key ->
                when (val value = previous[key]) {
                    is Long -> rollback.putLong(key, value)
                    is String -> rollback.putString(key, value)
                    else -> rollback.remove(key)
                }
            }
            rollback.commit()
            error("Could not persist model download ownership or terminal outcome")
        }
    }

    private companion object {
        const val DOWNLOAD_PREFERENCES_NAME = "ksenax_download_preferences"
        val operationLocks = ConcurrentHashMap<String, Any>()
    }
}
