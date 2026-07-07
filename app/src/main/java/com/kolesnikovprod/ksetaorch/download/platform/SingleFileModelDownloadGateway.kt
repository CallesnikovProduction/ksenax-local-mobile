package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.ModelArtifactSpec
import java.io.File

/**
 * Базовый gateway для моделей, распространяемых как один готовый runtime-файл.
 * Класс помечен абстрактным, поскольку напрямую создаваться **НЕ ДОЛЖЕН**!
 *
 * Класс реализует общий [KsenaxDownloadGateway] для single-file моделей:
 * `.litertlm`, `.task` или другого уже подготовленного runtime-артефакта,
 * который не требует распаковки, конвертации или дополнительного
 * post-processing после загрузки.
 *
 * Конкретный наследник передаёт [ModelArtifactSpec], где зафиксированы:
 * URL модели, локальное имя файла, директория хранения, ожидаемый размер
 * и SHA-256. Вся дальнейшая механика одинакова для всех таких моделей.
 *
 * Gateway не работает с Android DownloadManager и файловой системой напрямую.
 * Эти платформенные операции делегируются [AndroidModelDownloadBackend].
 * Сам gateway связывает модельную спецификацию с backend-операциями и
 * возвращает наружу доменные сущности download-контура.
 *
 * Перед новой загрузкой [enqueue] удаляет старый файл модели и runtime-cache
 * директорию. Это предотвращает запуск устаревшего или несовместимого
 * артефакта во время повторной установки.
 *
 * Проверка валидности модели через [hasValidModel] требует не только наличия
 * файла, но и совпадения размера и SHA-256. Поэтому битая, неполная или
 * подменённая загрузка не будет считаться установленной моделью.
 *
 * Все override-методы помечены как final, чтобы конкретные gateway-классы
 * не расходились в install/download-поведении. Наследники должны задавать
 * только [ModelArtifactSpec], а не менять общий алгоритм single-file установки.
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
internal abstract class SingleFileModelDownloadGateway(
    context: Context,
    private val artifact: ModelArtifactSpec,
    final override var allowOverMeteredNetwork: Boolean = false,
    final override var allowOverRoaming:        Boolean = false,
) : KsenaxDownloadGateway {

    /**
     * Нижний платформенный слой, умеющий работать с низкоуровневыми операциями
     * по установке ([android.app.DownloadManager]) и с файлами ([java.io.File] API)
     *
     * @since 0.2
     */
    private val backend = AndroidModelDownloadBackend(context.applicationContext)

    final override fun enqueue(): Long {
        deleteRuntimeCacheDirectory()
        deleteModelFile()

        return backend.enqueueNew(
            url                     = artifact.url,
            fileName                = artifact.localFileName,
            modelNameAsDirectory    = artifact.storageDirectoryName,
            allowOverMeteredNetwork = allowOverMeteredNetwork,
            allowOverRoaming        = allowOverRoaming,
        )
    }

    final override fun modelFilePath(): String {
        return backend.modelFilePath(
            modelNameAsDirectory = artifact.storageDirectoryName,
            fileName             = artifact.localFileName,
        )
    }

    final override fun modelDirectoryPath(): String {
        return backend.modelDirectoryPath(artifact.storageDirectoryName)
    }

    final override fun getModelFile(): File {
        return backend.getModelFile(
            modelNameAsDirectory = artifact.storageDirectoryName,
            fileName             = artifact.localFileName,
        )
    }

    final override fun getModelDirectory(): File {
        return backend.getModelDirectory(artifact.storageDirectoryName)
    }

    final override fun getModelSubdirectory(directoryName: String): File {
        return backend.getModelSubdirectory(
            modelNameAsDirectory = artifact.storageDirectoryName,
            directoryName        = directoryName,
        )
    }

    final override fun deleteModelFile(): Boolean {
        return backend.deleteModelFile(
            modelNameAsDirectory = artifact.storageDirectoryName,
            fileName             = artifact.localFileName,
        )
    }

    final override fun deleteModelSubdirectory(directoryName: String): Boolean {
        return backend.deleteModelSubdirectory(
            modelNameAsDirectory = artifact.storageDirectoryName,
            directoryName        = directoryName,
        )
    }

    final override fun cancelBy(downloadId: Long) {
        backend.cancelBy(downloadId)
    }

    final override fun querySnapshotBy(downloadId: Long): KsenaxDownloadTaskSnapshot? {
        return backend.querySnapshotBy(downloadId)
    }

    final override fun hasModelCandidate(): Boolean {
        return backend.hasModelCandidate(
            modelNameAsDirectory = artifact.storageDirectoryName,
            fileName             = artifact.localFileName,
        )
    }

    final override fun hasValidModel(): Boolean {
        return backend.hasValidModelFile(
            modelNameAsDirectory = artifact.storageDirectoryName,
            fileName             = artifact.localFileName,
            expectedSizeBytes    = artifact.expectedSizeBytes,
            expectedSha256       = artifact.expectedSha256,
        )
    }
}
