package com.kolesnikovprod.ksetaorch.download.domain.usecases

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.DownloadIdPreferencesStore
import com.kolesnikovprod.ksetaorch.download.domain.FileTreeDeleter
import com.kolesnikovprod.ksetaorch.download.domain.InstallSnapshots
import com.kolesnikovprod.ksetaorch.download.domain.InstallSupersededException
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallFailure
import com.kolesnikovprod.ksetaorch.download.platform.BackgroundModelInstallScheduler
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Делегат установки single-file модели, поставляемых одним готовым runtime-файлом.
 * Некий рабочий движок установки, который можно подкладывать под разные конкретные модели.
 *
 * Содержит универсальную механику, а конкретика модели уже прячется в
 * [KsenaxDownloadGateway] и [KsenaxInstallTarget]:
 * - запуск загрузки через [KsenaxDownloadGateway];
 * - сохранение и восстановление идентификатора загрузки;
 * - отмену загрузки;
 * - проверку наличия скачанного кандидата;
 * - проверку валидной локальной установки;
 * - удаление файла модели и runtime-cache артефактов.
 *
 * Может использоваться как Kotlin interface-delegate для конкретных use-case'ов:
 *
 * ```
 * internal class GemmaInstallUseCase(
 *     delegate: SingleFileInstallDelegate,
 * ) : KsenaxModelInstallUseCase by delegate
 * ```
 *
 * Делегат не скачивает файл сам. Он понятия не имеет, что за URL, имя файла и прочее.
 * Его существование нужно для:
 * - настройки политики сети
 * - сохранения `downloadId`
 * - проверка кандидата
 * - проверка валидной установки
 * - удаление артефактов при необходимости
 *
 * @property downloadGateway первое направление делегирования, отвечающее
 * за реальные файловые и [android.app.DownloadManager]-операции.
 * Даёт возможность делать следующие операции:
 * ```kotlin
 * downloadGateway.enqueue()
 * downloadGateway.cancelBy(downloadId)
 * downloadGateway.deleteModelFile()
 * downloadGateway.deleteRuntimeCacheDirectory()
 * downloadGateway.hasModelCandidate()
 * downloadGateway.hasValidModel()
 * downloadGateway.querySnapshotBy(downloadId)
 * downloadGateway.getModelFile()
 * ```
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal class SingleFileInstallDelegate(
    context:                     Context,
    override val installTarget:  KsenaxInstallTarget,
    private val downloadGateway: KsenaxDownloadGateway,
    legacyDownloadIdKey:         String? = null,
) : KsenaxModelInstallUseCase {

    private val appContext = context.applicationContext

    /**
     * Второе направление делегирования, отвечающее за сохранение ID загрузки.
     *
     * По сути, позволяет вызывать:
     * ```kotlin
     * preferencesStore.getOrCreate { enqueue() }
     * preferencesStore.get()
     * preferencesStore.clearIfMatches(id)
     * ```
     *
     * @since 0.2
     */
    private val downloadIdPreferencesStore = DownloadIdPreferencesStore(
        context             = context,
        installTarget       = installTarget,
        legacyDownloadIdKey = legacyDownloadIdKey,
    )


    /*
    * А с этого момента начинается замечательная красота языка в том, что...
    *
    * Делегирование позволяет НЕ ПИСАТЬ эти методы в качестве обёрток в usecases.
    * Компилятор автоматически сгенерирует forwarding.
    * */

    override fun startDownloadAndSave(
        policy: KsenaxDownloadPolicy
    ): Long {
        val downloadId = downloadIdPreferencesStore.getOrCreate(createDownload = {
            downloadGateway.allowOverMeteredNetwork = policy.allowOverMeteredNetwork
            downloadGateway.allowOverRoaming        = policy.allowOverRoaming

            downloadGateway.enqueue()
        }, recoverDownload = downloadGateway::findExistingDownload)
        BackgroundModelInstallScheduler(appContext).enqueueFinalizationFor(downloadId)
        return downloadId
    }

    override fun getSavedDownloadId(): Long = downloadIdPreferencesStore.get()

    override fun persistedSnapshot() = downloadIdPreferencesStore.terminalSnapshot()

    override fun scheduleReconciliation() {
        BackgroundModelInstallScheduler(appContext).enqueueRecoveryFor(installTarget)
    }

    override fun reconcileDownload(): Long {
        val id = downloadIdPreferencesStore.recoverIfMissing(downloadGateway::findExistingDownload)
        if (id == NO_DOWNLOAD_ID) return id
        BackgroundModelInstallScheduler(appContext).enqueueFinalizationFor(id)
        return id
    }

    override fun cancelDownload(downloadId: Long) {
        if (downloadId == NO_DOWNLOAD_ID) return

        downloadIdPreferencesStore.finishIfMatches(downloadId, InstallSnapshots.cancelled()) {
            downloadGateway.deleteDownloadArtifacts(downloadId)
            downloadGateway.cancelBy(downloadId)
        }
    }

    override fun clearArtifacts() {
        clearArtifactsIfOwnedBy(getSavedDownloadId())
    }

    override fun clearArtifactsIfOwnedBy(downloadId: Long): Boolean {
        return failDownloadIfOwnedBy(downloadId, KsenaxInstallFailure.INTEGRITY)
    }

    override fun failDownloadIfOwnedBy(downloadId: Long, failure: KsenaxInstallFailure): Boolean {
        return downloadIdPreferencesStore.finishIfMatches(downloadId, InstallSnapshots.failed(failure)) {
            if (downloadId != NO_DOWNLOAD_ID) downloadGateway.deleteDownloadArtifacts(downloadId)
        }
    }

    override fun clearSavedDownloadId() {
        downloadIdPreferencesStore.clear()
    }

    override fun clearSavedDownloadIdIfOwnedBy(downloadId: Long): Boolean {
        return downloadIdPreferencesStore.finishIfMatches(downloadId, InstallSnapshots.installed()) {
            if (downloadId != NO_DOWNLOAD_ID) downloadGateway.deleteDownloadArtifacts(downloadId)
        }
    }

    override fun deleteLocalArtifacts(): Boolean {
        val isModelDeleted = downloadGateway.deleteModelFile()
        val isRuntimeCacheDeleted = downloadGateway.deleteRuntimeCacheDirectory()

        return isModelDeleted && isRuntimeCacheDeleted
    }

    override suspend fun hasInstallCandidate(): Boolean {
        return withContext(Dispatchers.IO) {
            downloadGateway.hasModelCandidate()
        }
    }

    override suspend fun prepareInstallCandidate(): Boolean {
        val id = getSavedDownloadId()
        return if (id == NO_DOWNLOAD_ID) hasValidInstallation() else prepareInstallCandidate(id)
    }

    override suspend fun prepareInstallCandidate(downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val jobContext = currentCoroutineContext()
        val checkActive = {
            jobContext.ensureActive()
            if (getSavedDownloadId() != downloadId) throw InstallSupersededException()
        }
        checkActive()
        downloadGateway.installDownloadedFile(downloadId, checkActive) { publish ->
            downloadIdPreferencesStore.runIfMatches(downloadId, publish)
        }
    }

    override suspend fun hasValidInstallation(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jobContext = currentCoroutineContext()
                downloadGateway.hasValidModel { jobContext.ensureActive() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun queryDownloadSnapshot(downloadId: Long): KsenaxDownloadTaskSnapshot? {
        return downloadGateway.querySnapshotBy(downloadId)
    }

    override fun getInstalledPath(): String = getModelPath()


    /*
    * Внутренняя кухня делегата: то, что не получает делегируемый класс.
    * */

    fun getModelPath():        String = downloadGateway.getModelFile().absolutePath

    fun getRuntimeCachePath(): String = downloadGateway.getRuntimeCacheDirectory().absolutePath

    fun getSavedVoicesPath():  String = downloadGateway.getSavedVoicesDirectory().absolutePath

    fun clearSavedVoices() {
        val savedVoicesDirectory = downloadGateway.getSavedVoicesDirectory()

        FileTreeDeleter.deleteIfExists(savedVoicesDirectory)
        savedVoicesDirectory.mkdirs()
    }
}
