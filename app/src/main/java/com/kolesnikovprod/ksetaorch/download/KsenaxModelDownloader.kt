package com.kolesnikovprod.ksetaorch.download

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

/**
 * Android-реализация загрузчика модельных файлов.
 *
 * Класс является тонкой оберткой над системным [DownloadManager]. Он не скачивает файл
 * вручную через HTTP-клиент, а передает Android одну задачу загрузки и получает от
 * системы `downloadId`. Этот id потом можно использовать во внешнем слое, чтобы
 * опрашивать прогресс, статус или отменять загрузку.
 *
 * Важно: этот класс не запускает модель и не знает про LiteRT-LM runtime. Его зона
 * ответственности заканчивается на доставке файла в локальное хранилище приложения,
 * удалении старых/битых файлов и integrity-проверке уже скачанного артефакта.
 *
 * @param context контекст приложения, через который берется системный DownloadManager
 * и внутренняя ориентированная на приложение директория.
 * @param allowOverMeteredNetwork разрешает загрузку через metered-сеть, например
 * мобильный интернет. По умолчанию выключено, чтобы случайно не начать тяжелую загрузку.
 * @param allowOverRoaming разрешает загрузку в роуминге. По умолчанию выключено.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 * @see KsenaxDownloadWrapper
 * @see KsenaxDownloader
 */
class KsenaxModelDownloader(
    private val context: Context,
    private var allowOverMeteredNetwork: Boolean = false,
    private var allowOverRoaming: Boolean = false,
) : KsenaxDownloader {

    /**
     * Системная очередь загрузок Android.
     *
     * Именно DownloadManager отвечает за сетевую часть, уведомление о загрузке,
     * продолжение задачи вне активной Activity и хранение статуса задачи.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private val ksenaxManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Ставит файл модели в системную очередь загрузок.
     *
     * Перед постановкой новой задачи метод удаляет локальный файл с тем же именем.
     * Это нужно, чтобы после отмены или падения загрузки не осталась пустышка или
     * частично скачанный `.litertlm`, который позже можно ошибочно принять за модель.
     *
     * Возвращаемый [Long] - это `downloadId` задачи DownloadManager, а не id модели
     * и не признак успешной установки. Успешность нужно проверять отдельно: статусом
     * DownloadManager, размером файла и SHA256.
     *
     * @param url удаленный URL файла модели.
     * @param fileName имя файла внутри директории конкретной модели.
     * @param modelName имя директории модели внутри `models/`.
     * @return id задачи DownloadManager.
     * @see KsenaxDownloadWrapper
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    override fun download(
        url: String,
        fileName: String,
        modelName: String
    ): Long {
        deleteModelFile(modelName, fileName)

        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setDescription("Downloading model file...")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(allowOverMeteredNetwork)
            .setAllowedOverRoaming(allowOverRoaming)
            .setDestinationInExternalFilesDir(
                context,
                null,
                modelFilePath(modelName, fileName)
            )

        return ksenaxManager.enqueue(request)
    }

    /**
     * Удаляет локальный файл модели.
     *
     * Метод используется перед новой загрузкой, после отмены и после неуспешной
     * проверки файла. Если файла нет, возвращает `true`, потому что целевое состояние
     * "файла нет" уже достигнуто.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun deleteModelFile(
        modelName: String,
        fileName: String
    ): Boolean {
        val modelFile = getModelFile(modelName, fileName)

        if (!modelFile.exists()) {
            return true
        }

        return modelFile.delete()
    }

    /**
     * Удаляет директорию кэша конкретной модели.
     *
     * Кэш лежит рядом с `.litertlm` файлом внутри директории модели. Его безопасно
     * чистить при удалении или повторной загрузке модели, чтобы LiteRT-LM не работал
     * со старыми runtime-артефактами от прежнего файла.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun deleteModelCacheDirectory(
        modelName: String,
        cacheDirectoryName: String,
    ): Boolean {
        val cacheDirectory = getModelCacheDirectory(modelName, cacheDirectoryName)

        if (!cacheDirectory.exists()) {
            return true
        }

        return cacheDirectory.deleteRecursively()
    }

    /**
     * Возвращает объект [File] для ожидаемого локального пути модели.
     *
     * Метод не проверяет существование файла и не создает его. Он только собирает
     * путь из app-specific external files директории и относительного model path.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getModelFile(
        modelName: String,
        fileName: String
    ): File {
        return File(getModelDirectory(modelName), fileName)
    }

    /**
     * Возвращает директорию конкретной модели внутри app-specific storage.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getModelDirectory(modelName: String): File {
        return File(
            context.getExternalFilesDir(null),
            modelDirectoryPath(modelName),
        )
    }

    /**
     * Возвращает директорию кэша конкретной модели.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getModelCacheDirectory(
        modelName: String,
        cacheDirectoryName: String,
    ): File {
        return File(getModelDirectory(modelName), cacheDirectoryName)
    }

    /**
     * Формирует относительный путь файла модели внутри app-specific external files storage.
     *
     * При `modelName = "gemma-4-e2b"` и `fileName = "gemma-4-e2b-it.litertlm"`
     * итоговый относительный путь будет:
     *
     * ```bash
     * models/gemma-4-e2b/gemma-4-e2b-it.litertlm
     * ```
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun modelFilePath(
        modelName: String,
        fileName: String
    ): String {
        return "${modelDirectoryPath(modelName)}/$fileName"
    }

    /**
     * Формирует относительный путь директории модели внутри app-specific storage.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun modelDirectoryPath(modelName: String): String {
        return "models/$modelName"
    }

    /**
     * Быстро проверяет наличие файла-кандидата.
     *
     * Это не полная проверка установленной модели. Метод отвечает только на вопрос:
     * "есть ли непустой файл в ожидаемом месте?". Частичный файл тоже может пройти
     * эту проверку, поэтому для финального флага установки нужно использовать
     * [hasValidModelFile].
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun hasModelFile(
        modelName: String,
        fileName: String
    ): Boolean {
        val modelFile = getModelFile(modelName, fileName)

        return modelFile.exists() && modelFile.length() > 0L
    }

    /**
     * Проверяет, что локальный файл совпадает с ожидаемым модельным артефактом.
     *
     * Алгоритм проверки:
     * 1. файл существует;
     * 2. путь указывает именно на файл;
     * 3. размер файла равен ожидаемому размеру;
     * 4. локально посчитанный SHA256 равен ожидаемому SHA256.
     *
     * Подсчет SHA256 читает весь файл, поэтому вызывать этот метод нужно из
     * фонового контекста, например через `withContext(Dispatchers.IO)`.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun hasValidModelFile(
        modelName: String,
        fileName: String,
        expectedSizeBytes: Long,
        expectedSha256: String,
    ): Boolean {
        val modelFile = getModelFile(modelName, fileName)

        if (!modelFile.exists() || !modelFile.isFile) {
            return false
        }

        if (modelFile.length() != expectedSizeBytes) {
            return false
        }

        return calculateSha256(modelFile).equals(expectedSha256, ignoreCase = true)
    }


    /**
     * Считает SHA256 локального файла потоковым чтением.
     *
     * Файл читается блоками через буфер, чтобы не загружать модель целиком в память.
     * Результат возвращается в hex-строке нижнего регистра.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        file.inputStream().use { inputStream ->
            while (true) {
                val readBytes = inputStream.read(buffer)

                if (readBytes == -1) {
                    break
                }

                digest.update(buffer, 0, readBytes)
            }
        }

        return digest.digest()
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }


    /*
     * Геттеры и сеттеры в Java-стиле.
     * */

    /**
     * Возвращает, разрешены ли загрузки через metered-сеть.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun isAllowOverMeteredNetwork(): Boolean {
        return allowOverMeteredNetwork
    }

    /**
     * Меняет флаг загрузки через metered-сеть.
     *
     * Флаг будет применен к новым задачам загрузки. Уже поставленные задачи
     * DownloadManager этим методом не перенастраиваются.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun setAllowOverMeteredNetwork(allowOverMeteredNetwork: Boolean) {
        this.allowOverMeteredNetwork = allowOverMeteredNetwork
    }

    /**
     * Возвращает, разрешены ли загрузки в роуминге.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun isAllowOverRoaming(): Boolean {
        return allowOverRoaming
    }

    /**
     * Меняет флаг загрузки в роуминге.
     *
     * Флаг будет применен только к новым задачам загрузки.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun setAllowOverRoaming(allowOverRoaming: Boolean) {
        this.allowOverRoaming = allowOverRoaming
    }
}
