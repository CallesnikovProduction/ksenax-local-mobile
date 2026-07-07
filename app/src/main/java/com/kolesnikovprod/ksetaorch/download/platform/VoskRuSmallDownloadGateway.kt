package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import java.io.File
import java.util.zip.ZipFile

/**
 * Android-реализация [KsenaxDownloadGateway] для русской Vosk SMALL-модели.
 *
 * Gateway обслуживает скачиваемый zip-артефакт. Финальная установка Vosk
 * является директорией, поэтому распаковку и перенос во runtime-вид делает
 * [com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxVoskRuSmallInstallUseCase].
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal class VoskRuSmallDownloadGateway(
    context: Context,
    override var allowOverMeteredNetwork: Boolean = false,
    override var allowOverRoaming:        Boolean = false,
) : KsenaxDownloadGateway {

    private val backend = AndroidModelDownloadBackend(
        appContext = context.applicationContext,
    )

    /**
     * Ставит архив `vosk-model-small-ru-0.22.zip` в очередь загрузки.
     *
     * Официальная Vosk-страница помечает эту модель как lightweight wideband
     * модель для Android/iOS/RPi. Большие русские модели слишком тяжелые для
     * первого mobile-first STT-контура.
     *
     * @since 0.2
     */
    override fun enqueue(): Long {
        deleteModelFile()

        return backend.enqueueNew(
            url                     = VOSK_RU_SMALL_URL,
            fileName                = VOSK_RU_SMALL_ARCHIVE_FILE_NAME,
            modelNameAsDirectory    = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
            allowOverMeteredNetwork = allowOverMeteredNetwork,
            allowOverRoaming        = allowOverRoaming,
        )
    }

    override fun modelFilePath(): String {
        return backend.modelFilePath(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
            fileName             = VOSK_RU_SMALL_ARCHIVE_FILE_NAME,
        )
    }

    override fun modelDirectoryPath(): String {
        return backend.modelDirectoryPath(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
        )
    }

    override fun getModelFile(): File {
        return backend.getModelFile(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
            fileName             = VOSK_RU_SMALL_ARCHIVE_FILE_NAME,
        )
    }

    override fun getModelDirectory(): File {
        return backend.getModelDirectory(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
        )
    }

    override fun getModelSubdirectory(directoryName: String): File {
        return backend.getModelSubdirectory(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
            directoryName        = directoryName,
        )
    }

    override fun deleteModelFile(): Boolean {
        return backend.deleteModelFile(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
            fileName             = VOSK_RU_SMALL_ARCHIVE_FILE_NAME,
        )
    }

    override fun deleteModelSubdirectory(directoryName: String): Boolean {
        return backend.deleteModelSubdirectory(
            modelNameAsDirectory = VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY,
            directoryName        = directoryName,
        )
    }

    override fun cancelBy(downloadId: Long) {
        backend.cancelBy(downloadId)
    }

    override fun querySnapshotBy(downloadId: Long): KsenaxDownloadTaskSnapshot? {
        val systemSnapshot = backend.querySnapshotBy(downloadId)
        val hasCompleteArchive = isCompleteVoskArchive(getModelFile())

        if (hasCompleteArchive) {
            return (systemSnapshot ?: KsenaxDownloadTaskSnapshot(
                progress = 1f,
                state = KsenaxDownloadState.SUCCESSFUL,
            )).copy(
                progress = 1f,
                state = KsenaxDownloadState.SUCCESSFUL,
            )
        }

        return systemSnapshot
    }

    override fun hasModelCandidate(): Boolean {
        val archiveFile = getModelFile()

        return isCompleteVoskArchive(archiveFile) || hasValidModel()
    }

    override fun hasValidModel(): Boolean {
        return isValidVoskModelDirectory(getModelDirectory())
    }

    companion object {
        /**
         * Финальная директория Vosk-модели внутри `models/`.
         *
         * @since 0.2
         */
        const val VOSK_RU_SMALL_MODEL_NAME_IN_DIRECTORY = "vosk"

        /**
         * Закреплённое Hugging Face зеркало официального Vosk SMALL архива.
         *
         * Версия `0.22` взята из списка моделей Vosk как mobile lightweight
         * вариант для Android/iOS/RPi. Commit закреплён, чтобы URL всегда
         * указывал на один и тот же архив.
         *
         * @since 0.2
         */
        const val VOSK_RU_SMALL_URL =
            "https://huggingface.co/rhasspy/vosk-models/resolve/" +
                "e7ac2109d134b5f2404ba95389b2fb51916d4cab/ru/" +
                "vosk-model-small-ru-0.22.zip"

        /**
         * Имя скачиваемого zip-файла.
         *
         * @since 0.2
         */
        const val VOSK_RU_SMALL_ARCHIVE_FILE_NAME = "openksenax_vosk-model-small-ru-0.22.zip"

        /**
         * Имя корневой директории внутри официального zip-архива.
         *
         * @since 0.2
         */
        const val VOSK_RU_SMALL_ARCHIVE_ROOT_DIRECTORY_NAME = "vosk-model-small-ru-0.22"

        /**
         * Минимальная структурная проверка Vosk-модели.
         *
         * Vosk-модель является директорией, а не одним файлом. Для первого
         * install-контура достаточно проверить базовые каталоги и ключевые
         * файлы, без попытки создать runtime `org.vosk.Model`.
         *
         * @since 0.2
         */
        fun isValidVoskModelDirectory(directory: File): Boolean {
            if (!directory.isDirectory) return false

            return VOSK_REQUIRED_MODEL_FILES.all { relativePath ->
                File(directory, relativePath).isFile
            }
        }

        /**
         * Подтверждает, что ZIP уже дописан и содержит обязательную структуру
         * Vosk. Частичный архив без central directory эту проверку не пройдет.
         */
        private fun isCompleteVoskArchive(archiveFile: File): Boolean {
            if (!archiveFile.isFile || archiveFile.length() <= 0L) return false

            return try {
                ZipFile(archiveFile).use { zipFile ->
                    VOSK_REQUIRED_MODEL_FILES.all { relativePath ->
                        val entry = zipFile.getEntry(
                            "$VOSK_RU_SMALL_ARCHIVE_ROOT_DIRECTORY_NAME/$relativePath"
                        )

                        entry != null && !entry.isDirectory
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        private val VOSK_REQUIRED_MODEL_FILES = listOf(
            "am/final.mdl",
            "conf/model.conf",
            "conf/mfcc.conf",
            "graph/HCLr.fst",
            "graph/Gr.fst",
            "ivector/final.dubm",
            "ivector/final.ie",
            "ivector/final.mat",
            "ivector/global_cmvn.stats",
            "ivector/online_cmvn.conf",
            "ivector/splice.conf",
        )
    }
}
