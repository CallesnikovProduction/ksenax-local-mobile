package com.kolesnikovprod.ksetaorch.download.domain.usecases

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.DownloadIdPreferencesStore
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /**
     * Второе направление делегирования, отвечающее за сохранение ID загрузки.
     *
     * По сути, позволяет вызывать:
     * ```kotlin
     * preferencesStore.save(id)
     * preferencesStore.get()
     * preferencesStore.clear()
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
        downloadGateway.allowOverMeteredNetwork = policy.allowOverMeteredNetwork
        downloadGateway.allowOverRoaming        = policy.allowOverRoaming

        return downloadGateway.enqueue().also(downloadIdPreferencesStore::save)
    }

    override fun getSavedDownloadId(): Long = downloadIdPreferencesStore.get()

    override fun cancelDownload(downloadId: Long) {
        if (downloadId != NO_DOWNLOAD_ID) {
            downloadGateway.cancelBy(downloadId)
        }

        clearArtifacts()
    }

    override fun clearArtifacts() {
        clearSavedDownloadId()
        deleteLocalArtifacts()
    }

    override fun clearSavedDownloadId() {
        downloadIdPreferencesStore.clear()
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
        return hasInstallCandidate()
    }

    override suspend fun hasValidInstallation(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                downloadGateway.hasValidModel()
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

        if (savedVoicesDirectory.exists()) {
            savedVoicesDirectory.deleteRecursively()
        }

        savedVoicesDirectory.mkdirs()
    }
}
