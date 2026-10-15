package com.kolesnikovprod.ksetaorch.download.domain.usecases

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.*
import com.kolesnikovprod.ksetaorch.download.domain.data.*
import com.kolesnikovprod.ksetaorch.download.platform.BackgroundModelInstallScheduler
import com.kolesnikovprod.ksetaorch.download.platform.VoskRuSmallDownloadGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

/**
 * Устанавливает Vosk Small RU из закреплённого ZIP в models/vosk.
 * Кандидаты изолированы по задаче; прежняя модель сохраняется при отказе/отмене.
 * @since 0.4
 */
class KsenaxVoskRuSmallInstallUseCase(
    context: Context,
    private val downloadGateway: KsenaxDownloadGateway = VoskRuSmallDownloadGateway(context.applicationContext),
) : KsenaxModelInstallUseCase {
    private val appContext = context.applicationContext
    override val installTarget = KsenaxInstallTarget.VOSK_RU_SMALL
    private val store = DownloadIdPreferencesStore(context, installTarget)

    override fun startDownloadAndSave(policy: KsenaxDownloadPolicy): Long {
        val id = store.getOrCreate(createDownload = {
            downloadGateway.allowOverMeteredNetwork = policy.allowOverMeteredNetwork
            downloadGateway.allowOverRoaming = policy.allowOverRoaming
            downloadGateway.enqueue()
        }, recoverDownload = downloadGateway::findExistingDownload)
        BackgroundModelInstallScheduler(appContext).enqueueFinalizationFor(id)
        return id
    }

    override fun getSavedDownloadId(): Long = store.get()
    override fun persistedSnapshot(): KsenaxInstallSnapshot? = store.terminalSnapshot()

    override fun scheduleReconciliation() {
        BackgroundModelInstallScheduler(appContext).enqueueRecoveryFor(installTarget)
    }

    override fun reconcileDownload(): Long {
        val id = store.recoverIfMissing(downloadGateway::findExistingDownload)
        if (id == NO_DOWNLOAD_ID) return id
        BackgroundModelInstallScheduler(appContext).enqueueFinalizationFor(id)
        return id
    }

    override fun cancelDownload(downloadId: Long) {
        if (downloadId == NO_DOWNLOAD_ID) return
        store.finishIfMatches(downloadId, InstallSnapshots.cancelled()) {
            cleanup(downloadId)
            downloadGateway.cancelBy(downloadId)
        }
    }

    override fun clearArtifacts() { clearArtifactsIfOwnedBy(store.get()) }
    override fun clearSavedDownloadId() { store.clear() }
    override fun clearArtifactsIfOwnedBy(downloadId: Long): Boolean =
        failDownloadIfOwnedBy(downloadId, KsenaxInstallFailure.INTEGRITY)

    override fun failDownloadIfOwnedBy(downloadId: Long, failure: KsenaxInstallFailure): Boolean =
        store.finishIfMatches(downloadId, InstallSnapshots.failed(failure)) { cleanup(downloadId) }

    override fun clearSavedDownloadIdIfOwnedBy(downloadId: Long): Boolean =
        store.finishIfMatches(downloadId, InstallSnapshots.installed()) { cleanup(downloadId) }

    override fun deleteLocalArtifacts(): Boolean =
        FileTreeDeleter.deleteIfExists(downloadGateway.getModelDirectory())

    override suspend fun hasInstallCandidate(): Boolean = withContext(Dispatchers.IO) {
        downloadGateway.hasModelCandidate() ||
            VoskRuSmallDownloadGateway.isValidVoskModelDirectory(backupDirectory())
    }

    override suspend fun prepareInstallCandidate(): Boolean = prepareInstallCandidate(store.get())

    override suspend fun prepareInstallCandidate(downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val jobContext = currentCoroutineContext()
        val checkActive = {
            jobContext.ensureActive()
            if (store.get() != downloadId) throw InstallSupersededException()
        }
        checkActive()
        val finalDirectory = downloadGateway.getModelDirectory()
        val backup = backupDirectory()
        if (!finalDirectory.exists() && VoskRuSmallDownloadGateway.isValidVoskModelDirectory(backup)) {
            store.runIfMatches(downloadId) {
                Files.move(backup.toPath(), finalDirectory.toPath(), ATOMIC_MOVE)
                true
            }
        }
        if (downloadId == NO_DOWNLOAD_ID && downloadGateway.hasValidModel()) return@withContext true
        val archive = if (downloadId == NO_DOWNLOAD_ID) downloadGateway.getModelFile()
            else downloadGateway.getDownloadFile(downloadId)
        if (!FileIntegrityVerifier.matches(
                archive,
                VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SIZE_BYTES,
                VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SHA256,
                checkActive,
            )) return@withContext false

        val temp = temporaryDirectory(downloadId)
        check(FileTreeDeleter.deleteIfExists(temp)) { "Cannot clear extraction staging" }
        if (!SafeZipExtractor.extract(archive, temp, checkActive)) return@withContext false
        val root = File(temp, VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_ROOT_DIRECTORY_NAME)
        if (!VoskRuSmallDownloadGateway.isValidVoskModelDirectory(root)) return@withContext false
        checkActive()
        store.runIfMatches(downloadId) {
            // Do not replace the same pinned, usable model or its saved voices.
            if (downloadGateway.hasValidModel()) return@runIfMatches true
            check(FileTreeDeleter.deleteIfExists(backup)) { "Cannot clear install backup" }
            val hadPrevious = finalDirectory.exists()
            if (hadPrevious) Files.move(finalDirectory.toPath(), backup.toPath(), ATOMIC_MOVE)
            try {
                Files.move(root.toPath(), finalDirectory.toPath(), ATOMIC_MOVE)
            } catch (exception: Exception) {
                if (hadPrevious && !finalDirectory.exists()) {
                    Files.move(backup.toPath(), finalDirectory.toPath(), ATOMIC_MOVE)
                }
                throw exception
            }
            FileTreeDeleter.deleteIfExists(backup)
            true
        }
    }

    override suspend fun hasValidInstallation(): Boolean = withContext(Dispatchers.IO) {
        downloadGateway.hasValidModel()
    }

    override fun queryDownloadSnapshot(downloadId: Long): KsenaxDownloadTaskSnapshot? =
        downloadGateway.querySnapshotBy(downloadId)

    override fun getInstalledPath(): String = downloadGateway.getModelDirectory().absolutePath

    /** Путь WAV-команд, сохраняемый между неудачными попытками установки. @since 0.4 */
    fun getVoskSavedVoicesDirPath(): String = downloadGateway.getSavedVoicesDirectory().absolutePath

    private fun temporaryDirectory(id: Long): File =
        File(downloadGateway.getModelDirectory().parentFile, ".vosk-installing-$id")

    private fun backupDirectory(): File = File(downloadGateway.getModelDirectory().parentFile, ".vosk-backup")

    private fun cleanup(id: Long) {
        if (id != NO_DOWNLOAD_ID) downloadGateway.deleteDownloadArtifacts(id)
        FileTreeDeleter.deleteIfExists(temporaryDirectory(id))
    }
}
