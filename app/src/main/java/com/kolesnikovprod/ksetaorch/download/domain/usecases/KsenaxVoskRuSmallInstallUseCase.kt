package com.kolesnikovprod.ksetaorch.download.domain.usecases

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.DownloadIdPreferencesStore
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import com.kolesnikovprod.ksetaorch.download.platform.VoskRuSmallDownloadGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Use case установки русской Vosk SMALL-модели для offline STT.
 *
 * В отличие от Gemma `.litertlm`, Vosk приходит zip-архивом и должен быть
 * распакован в директорию модели. Финальный runtime-путь этой реализации:
 *
 * ```text
 * models/vosk
 * ```
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 * @see com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
 * @see com.kolesnikovprod.ksetaorch.download.platform.VoskRuSmallDownloadGateway
 */
class KsenaxVoskRuSmallInstallUseCase(
    context: Context,
    private val downloadGateway: KsenaxDownloadGateway =
        VoskRuSmallDownloadGateway(context.applicationContext),
) : KsenaxModelInstallUseCase {

    override val installTarget: KsenaxInstallTarget = KsenaxInstallTarget.VOSK_RU_SMALL
    private val downloadIdPreferencesStore = DownloadIdPreferencesStore(
        context       = context,
        installTarget = installTarget,
    )

    /**
     * Перед новой загрузкой удаляет старую Vosk-директорию, zip и временную
     * папку распаковки, чтобы новый install-кандидат не смешался со старым.
     *
     * @since 0.2
     */
    override fun startDownloadAndSave(
        policy: KsenaxDownloadPolicy,
    ): Long {
        deleteLocalArtifacts()

        downloadGateway.allowOverMeteredNetwork = policy.allowOverMeteredNetwork
        downloadGateway.allowOverRoaming        = policy.allowOverRoaming

        val downloadId = downloadGateway.enqueue()

        saveDownloadId(downloadId)

        return downloadId
    }

    override fun getSavedDownloadId(): Long {
        return downloadIdPreferencesStore.get()
    }

    private fun saveDownloadId(downloadId: Long) {
        downloadIdPreferencesStore.save(downloadId)
    }

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
        val isArchiveDeleted = downloadGateway.deleteModelFile()
        val isInstallDirectoryDeleted = deleteIfExists(downloadGateway.getModelDirectory())
        val isTempDirectoryDeleted = deleteIfExists(getTempInstallDirectory())

        return isArchiveDeleted && isInstallDirectoryDeleted && isTempDirectoryDeleted
    }

    override suspend fun hasInstallCandidate(): Boolean {
        return withContext(Dispatchers.IO) {
            downloadGateway.hasModelCandidate() ||
                    VoskRuSmallDownloadGateway.isValidVoskModelDirectory(
                        getTempArchiveRootDirectory()
                    )
        }
    }

    /**
     * Распаковывает скачанный zip в temp-директорию, проверяет базовую Vosk-
     * структуру и переносит содержимое во финальный `models/vosk`.
     *
     * Если `models/vosk` уже валидна, метод ничего не делает.
     *
     * @since 0.2
     */
    override suspend fun prepareInstallCandidate(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    downloadGateway.hasValidModel() -> true
                    VoskRuSmallDownloadGateway.isValidVoskModelDirectory(
                        getTempArchiveRootDirectory()
                    ) ->
                        movePreparedTempModelToFinalDirectory()

                    downloadGateway.getModelFile().isFile ->
                        unzipArchiveToFinalDirectory()

                    else -> false
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }
        }
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

    override fun getInstalledPath(): String {
        return downloadGateway.getModelDirectory().absolutePath
    }

    /**
     * Возвращает путь к директории, куда Vosk-ветка может сохранять WAV-файлы
     * голосовых команд.
     *
     * @since 0.2
     */
    fun getVoskSavedVoicesDirPath(): String {
        return downloadGateway.getSavedVoicesDirectory().absolutePath
    }


    /*
     * ╔════════════════════════════════════╗
     * ║  VOSK ZIP INSTALL CONTOUR          ║
     * ╚════════════════════════════════════╝
     *
     * Vosk приходит zip-архивом, поэтому install-кандидат нужно распаковать,
     * проверить и только потом перенести в финальную директорию `models/vosk`.
     *
     * Распаковка всегда идёт во временную `.vosk-installing`, а не напрямую
     * в runtime-путь. Это не даёт неполной модели стать видимой для STT runtime.
     *
     * [unzipArchiveSafely] защищает от Zip Slip через canonical path check.
     */

    /**
     * Главный метод подготовки архива.
     *
     * Сценарий его работы:
     * 1. взять zip-файл;
     * 2. взять temp-директорию;
     * 3. проверить, что zip реально существует;
     * 4. удалить старый temp;
     * 5. создать новый temp;
     * 6. безопасно распаковать zip в temp;
     * 7. проверить структуру Vosk внутри temp;
     * 8. перенести подготовленную модель в финальную директорию.
     */
    private fun unzipArchiveToFinalDirectory(): Boolean {
        val archivedZipFile = downloadGateway.getModelFile() // 1
        val tempDirectory  = getTempInstallDirectory()      // 2

        if (!archivedZipFile.isFile || archivedZipFile.length() <= 0L) { // 3
            return false
        }

        deleteIfExists(tempDirectory) // 4

        if (!tempDirectory.mkdirs()) { // 5
            return false
        }

        val isUnpacked = unzipArchiveSafely( // 6
            archiveFile     = archivedZipFile,
            outputDirectory = tempDirectory,
        )

        if (!isUnpacked || !VoskRuSmallDownloadGateway.isValidVoskModelDirectory( // 7
                getTempArchiveRootDirectory()
            )
        ) {
            deleteIfExists(tempDirectory)
            return false
        }

        return movePreparedTempModelToFinalDirectory() // 8
    }

    private fun getTempInstallDirectory(): File {
        val finalDirectory = downloadGateway.getModelDirectory()
        val parentDirectory = finalDirectory.parentFile

        return File(
            parentDirectory,
            TEMP_INSTALL_DIRECTORY_NAME,
        )
    }

    private fun deleteIfExists(file: File): Boolean {
        if (!file.exists()) return true

        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    /**
     * Важный security-метод локального unzip-контура, защищающий от zip slip.
     *
     * *Zip Slip — ситуация плохого архива, содержащий `entry` с именем, например:*
     * ```
     * ../../../../some-dangerous-file
     * ```
     * *Если попробовать записать этот файл по обычному [File] API, то файл
     * может оказаться за пределами `outputDirectory`*.
     */
    private fun unzipArchiveSafely(
        archiveFile: File,
        outputDirectory: File,
    ): Boolean {
        val canonicalOutputDirectory = outputDirectory.canonicalFile
        val canonicalOutputPrefix = canonicalOutputDirectory.path + File.separator

        ZipInputStream(archiveFile.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                // один файл/директория внутри zip
                val entry = zipInputStream.nextEntry ?: break
                val outputFile = File(outputDirectory, entry.name)
                val canonicalOutputFile = outputFile.canonicalFile

                // Файл должен быть внутри temporary
                val isInsideOutputDirectory =
                    // out-file = out-directory
                    canonicalOutputFile == canonicalOutputDirectory ||
                            // out-file внутри out-directory
                            canonicalOutputFile.path.startsWith(canonicalOutputPrefix)

                if (!isInsideOutputDirectory) {
                    return false
                }

                if (entry.isDirectory) {
                    if (outputFile.parentFile?.exists() == false &&
                        outputFile.parentFile?.mkdirs() == false) {
                        return false
                    }
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }

                zipInputStream.closeEntry()
            }
        }

        return true
    }

    private fun movePreparedTempModelToFinalDirectory(): Boolean {
        val preparedRoot    = getTempArchiveRootDirectory()
        val finalDirectory  = downloadGateway.getModelDirectory()
        val parentDirectory = finalDirectory.parentFile ?: return false

        if (!VoskRuSmallDownloadGateway.isValidVoskModelDirectory(preparedRoot)) {
            return false
        }

        if (!deleteIfExists(finalDirectory)) {
            return false
        }

        if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
            return false
        }

        val isMoved = preparedRoot.renameTo(finalDirectory) ||
            preparedRoot.copyRecursively(
                target    = finalDirectory,
                overwrite = true,
            )

        if (!isMoved) {
            return false
        }

        deleteIfExists(getTempInstallDirectory())

        return VoskRuSmallDownloadGateway.isValidVoskModelDirectory(finalDirectory)
    }

    private fun getTempArchiveRootDirectory(): File {
        return File(
            getTempInstallDirectory(),
            VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_ROOT_DIRECTORY_NAME,
        )
    }



    private companion object {
        const val TEMP_INSTALL_DIRECTORY_NAME = ".vosk-installing"
    }
}
