package com.kolesnikovprod.ksetaorch.download

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис установки локальной модели.
 *
 * Этот класс собирает не-UI операции, которые раньше жили в MainActivity:
 * старт загрузки, отмену, восстановление download id, опрос DownloadManager,
 * удаление частичного файла и проверку локального `.litertlm` через размер и SHA256.
 *
 * Сервис не является Composable и не рисует экран. Его задача - дать экрану простой
 * API установки модели, чтобы UI работал с состояниями, а не с Android DownloadManager
 * и SharedPreferences напрямую.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class KsenaxModelInstallService(
    context: Context,
) {

    private val context = context.applicationContext

    private val downloadManager =
        this.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val downloadWrapper = KsenaxDownloadWrapper(
        KsenaxModelDownloader(
            this.context,
            allowOverRoaming = false,
            allowOverMeteredNetwork = false,
        )
    )

    /**
     * Возвращает сохраненный id активной загрузки или [NO_DOWNLOAD_ID].
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getSavedDownloadId(): Long {
        return context.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getLong(
            DOWNLOAD_ID_KEY,
            NO_DOWNLOAD_ID,
        )
    }

    /**
     * Запускает загрузку Gemma 4 E2B и сразу сохраняет download id.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun startGemma4E2BDownload(): Long {
        val downloadId = downloadWrapper.downloadGemma4E2B()

        saveDownloadId(downloadId)

        return downloadId
    }

    /**
     * Отменяет активную загрузку Gemma 4 E2B и удаляет частичный файл.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun cancelGemma4E2BDownload(downloadId: Long) {
        if (downloadId != NO_DOWNLOAD_ID) {
            downloadManager.remove(downloadId)
        }

        clearGemma4E2BDownloadArtifacts()
    }

    /**
     * Очищает сохраненный download id и удаляет локальный файл модели.
     *
     * Используется после отмены, ошибки загрузки или ситуации, где запись
     * DownloadManager уже не найдена.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun clearGemma4E2BDownloadArtifacts() {
        clearSavedDownloadId()
        deleteGemma4E2BFile()
    }

    /**
     * Удаляет только сохраненный download id, не трогая файл модели.
     *
     * Это нужно после успешной загрузки: активной задачи уже нет, но валидный файл
     * модели должен остаться на диске.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun clearSavedDownloadId() {
        context.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit()
            .remove(DOWNLOAD_ID_KEY)
            .apply()
    }

    /**
     * Проверяет, есть ли непустой файл-кандидат Gemma 4 E2B.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    suspend fun hasGemma4E2BCandidateFile(): Boolean {
        return withContext(Dispatchers.IO) {
            downloadWrapper.hasGemma4E2BFile()
        }
    }

    /**
     * Проверяет Gemma 4 E2B через ожидаемый размер и SHA256.
     *
     * Исключения доступа к файлу считаются неуспешной проверкой. Отмену coroutine
     * намеренно пробрасываем дальше, чтобы Compose/lifecycle могли корректно
     * остановить работу.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    suspend fun hasValidGemma4E2BFile(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                downloadWrapper.hasValidGemma4E2BFile()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Удаляет локальный файл Gemma 4 E2B.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun deleteGemma4E2BFile(): Boolean {
        return downloadWrapper.deleteGemma4E2BFile()
    }

    /**
     * Возвращает путь к локальному `.litertlm` файлу Gemma 4 E2B.
     *
     * Использовать для LiteRT-LM runtime только после успешной integrity-проверки.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getGemma4E2BModelPath(): String {
        return downloadWrapper.getGemma4E2BModelPath()
    }

    /**
     * Возвращает путь к runtime-кэшу Gemma 4 E2B.
     *
     * Директория находится рядом с `.litertlm` файлом модели:
     *
     * ```text
     * models/gemma-4-e2b/cashed
     * ```
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getGemma4E2BCacheDirPath(): String {
        return downloadWrapper.getGemma4E2BCacheDirPath()
    }

    /**
     * Возвращает снимок состояния загрузки по download id.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun queryDownloadSnapshot(downloadId: Long): KsenaxDownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            val status = it.getInt(
                it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            val downloadedBytes = it.getLong(
                it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val totalBytes = it.getLong(
                it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val progress = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            return KsenaxDownloadSnapshot(
                progress = progress,
                state = status.toKsenaxDownloadState(),
            )
        }
    }

    /**
     * Сохраняет id активной задачи загрузки в SharedPreferences.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun saveDownloadId(downloadId: Long) {
        context.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit()
            .putLong(DOWNLOAD_ID_KEY, downloadId)
            .apply()
    }

    /**
     * Переводит Android DownloadManager status в доменное состояние Ksenax.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun Int.toKsenaxDownloadState(): KsenaxDownloadState {
        return when (this) {
            DownloadManager.STATUS_PENDING -> KsenaxDownloadState.PENDING
            DownloadManager.STATUS_RUNNING -> KsenaxDownloadState.RUNNING
            DownloadManager.STATUS_PAUSED -> KsenaxDownloadState.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> KsenaxDownloadState.SUCCESSFUL
            DownloadManager.STATUS_FAILED -> KsenaxDownloadState.FAILED
            else -> KsenaxDownloadState.UNKNOWN
        }
    }

    private companion object {
        const val DOWNLOAD_PREFERENCES_NAME = "kseta_download_preferences"
        const val DOWNLOAD_ID_KEY = "active_model_download_id"
    }
}

/**
 * Снимок текущей задачи загрузки.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
data class KsenaxDownloadSnapshot(
    val progress: Float,
    val state: KsenaxDownloadState,
)

/**
 * Упрощенное состояние DownloadManager без прямой зависимости UI от Android-констант.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
enum class KsenaxDownloadState {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
    UNKNOWN,
}
