package com.kolesnikovprod.ksetaorch.download

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Прикладной сервис установки локальной модели.
 *
 * Этот класс собирает non-UI операции:
 * - старт загрузки,
 * - отмену,
 * - восстановление download id,
 * - опрос состояния загрузки,
 * - удаление частичного файла и проверку локального `.litertlm` через размер и SHA256.
 *
 * Задача сервиса - дать экрану простой API установки модели,
 * чтобы UI работал с состояниями, а не с download-адаптером и SharedPreferences напрямую.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class KsenaxModelInstallService(
    context: Context,
) {

    private val context = context.applicationContext

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
            downloadWrapper.cancelGemma4E2BDownload(downloadId)
        }

        clearGemma4E2BDownloadArtifacts()
    }

    /**
     * Очищает сохраненный download id и удаляет локальный файл модели.
     *
     * Используется после отмены, ошибки загрузки или ситуации, где запись активной
     * загрузки уже не найдена.
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
        return downloadWrapper.queryGemma4E2BDownloadSnapshot(downloadId)
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

    private companion object {
        const val DOWNLOAD_PREFERENCES_NAME = "kseta_download_preferences"
        const val DOWNLOAD_ID_KEY = "active_model_download_id"
    }
}
