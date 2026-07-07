package com.kolesnikovprod.ksetaorch.download.platform

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import java.io.File
import java.security.MessageDigest

/**
 * Низкоуровневый Android-backend для загрузки и хранения модельных файлов.
 *
 * Класс инкапсулирует платформенный бойлерплейт вокруг [DownloadManager],
 * app-specific external storage и базовых файловых операций. Он намеренно
 * не знает, какую именно модель обслуживает: имя директории модели, имя файла,
 * URL и ожидаемые параметры артефакта передаются в методы явно.
 *
 * Этот backend находится ниже model-specific gateway-слоя. Публичный download API
 * приложения должен оставаться в реализациях
 * [com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway], а данный
 * класс должен использоваться как общий Android-исполнитель для:
 *
 * - постановки новой загрузки в [DownloadManager];
 * - построения путей к файлу модели и связанным директориям;
 * - удаления файла модели и поддиректорий runtime-артефактов;
 * - отмены загрузки по downloadId;
 * - чтения состояния загрузки из [DownloadManager];
 * - проверки наличия скачанного кандидата;
 * - проверки валидности файла по размеру и SHA-256.
 *
 * Файлы сохраняются в app-specific external storage через
 * [Context.getExternalFilesDir]. Такие файлы принадлежат приложению и не требуют
 * отдельного runtime permission на запись во внешнее хранилище.
 *
 * В класс следует передавать application context. Backend держит ссылку на
 * [Context] и получает из него системный [DownloadManager], поэтому передача
 * Activity context может привести к лишнему удержанию Activity.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal class AndroidModelDownloadBackend(
    private val appContext: Context,
) {

    /**
     * Системный Android-сервис загрузок.
     *
     * Используется для постановки model artifact download-задач, отмены загрузок
     * и чтения текущего состояния задачи по downloadId.
     */
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Создаёт новую задачу загрузки модельного файла через [DownloadManager].
     *
     * Метод не выполняет post-processing и не проверяет файл после загрузки.
     * Он только создаёт [DownloadManager.Request], настраивает сетевую политику,
     * задаёт destination внутри app-specific external storage и возвращает
     * системный downloadId.
     *
     * @param url должен использовать HTTPS. Небезопасные HTTP-ссылки запрещены,
     * потому что модельный файл является доверенным runtime-артефактом приложения.
     *
     * @param fileName используется как отображаемый заголовок загрузки и как имя
     * локального файла.
     *
     * @param modelNameAsDirectory определяет модельную директорию внутри `models/`.
     * Например, для `gemma_4_e2b` итоговый относительный путь будет выглядеть
     * как `models/gemma_4_e2b/<fileName>`.
     *
     * @param allowOverMeteredNetwork разрешает загрузку через metered network
     * при включённой пользовательской политике.
     *
     * @param allowOverRoaming разрешает загрузку в роуминге при включённой
     * пользовательской политике.
     *
     * @return системный идентификатор задачи [DownloadManager], который нужно
     * сохранить для последующего polling-а, отмены или восстановления состояния.
     *
     * @throws IllegalArgumentException если [url] не использует HTTPS.
     * @since 0.2
     */
    fun enqueueNew(
        url:                     String,
        fileName:                String,
        modelNameAsDirectory:    String,
        allowOverMeteredNetwork: Boolean,
        allowOverRoaming:        Boolean,
    ): Long {
        require(url.startsWith("https://")) { "Model-URL must be HTTPS" }

        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setDescription("Downloading $fileName..")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(allowOverMeteredNetwork)
            .setAllowedOverRoaming(allowOverRoaming)
            .setDestinationInExternalFilesDir(
                appContext,
                null,
                modelFilePath(
                    modelNameAsDirectory = modelNameAsDirectory,
                    fileName             = fileName,
                ),
            )

        return downloadManager.enqueue(request)
    }

    /**
     * Возвращает относительный путь к файлу модели внутри app-specific external storage.
     *
     * Этот путь используется как destination для [DownloadManager.Request].
     * Он не является absolute path. Для получения [File] или абсолютного пути
     * следует использовать [getModelFile].
     *
     * Пример результата:
     *
     * ```text
     * models/gemma_4_e2b/gemma-4-E2B-it.litertlm
     * ```
     *
     * @since 0.2
     */
    fun modelFilePath(
        modelNameAsDirectory: String,
        fileName:             String,
    ): String {
        return "${modelDirectoryPath(modelNameAsDirectory)}/$fileName"
    }

    /**
     * Возвращает относительный путь к директории конкретной модели.
     *
     * Все модельные артефакты группируются под общей директорией `models/`,
     * а [modelNameAsDirectory] используется как стабильное имя поддиректории
     * конкретной модели.
     *
     * Пример результата:
     *
     * ```text
     * models/gemma_4_e2b
     * ```
     *
     * @since 0.2
     */
    fun modelDirectoryPath(modelNameAsDirectory: String): String {
        return "models/$modelNameAsDirectory"
    }

    /**
     * Возвращает [File] главного runtime-файла модели.
     *
     * Метод строит путь внутри директории конкретной модели, но не проверяет,
     * существует ли файл физически.
     *
     * Для проверки наличия скачанного файла
     * используется [hasModelCandidate], а для строгой проверки целостности —
     * [hasValidModelFile].
     *
     * @since 0.2
     */
    fun getModelFile(
        modelNameAsDirectory: String,
        fileName:             String,
    ): File {
        return File(
            getModelDirectory(modelNameAsDirectory),
            fileName,
        )
    }

    /**
     * Возвращает [File] директории конкретной модели в app-specific external storage.
     *
     * Директория является контейнером для главного файла модели и связанных
     * runtime-артефактов: cache, temporary files, saved voices и других
     * поддиректорий, которые создаются gateway- или runtime-слоем.
     *
     * Метод только строит объект [File] и не создаёт директорию принудительно.
     * @since 0.2
     */
    fun getModelDirectory(modelNameAsDirectory: String): File {
        return File(
            appContext.getExternalFilesDir(null),
            modelDirectoryPath(modelNameAsDirectory),
        )
    }

    /**
     * Возвращает [File] поддиректории внутри директории конкретной модели.
     *
     * Используется для связанных с моделью артефактов, которые должны жить рядом
     * с основным runtime-файлом, но не смешиваться с ним:
     *
     * - runtime cache;
     * - saved voices;
     * - temporary files;
     * - debug/log subdirectories.
     *
     * Метод только строит объект [File] и не создаёт директорию принудительно.
     * @since 0.2
     */
    fun getModelSubdirectory(
        modelNameAsDirectory: String,
        directoryName:        String,
    ): File {
        return File(
            getModelDirectory(modelNameAsDirectory),
            directoryName,
        )
    }

    /**
     * Удаляет главный файл модели, если он существует.
     *
     * Отсутствующий файл считается успешным результатом, потому что конечное
     * состояние уже достигнуто: локального model artifact больше нет.
     *
     * @return `true`, если файл отсутствует или был успешно удалён;
     * `false`, если файл существует, но удалить его не удалось.
     * @since 0.2
     */
    fun deleteModelFile(
        modelNameAsDirectory: String,
        fileName:             String,
    ): Boolean {
        val modelFile = getModelFile(
            modelNameAsDirectory = modelNameAsDirectory,
            fileName             = fileName,
        )

        if (!modelFile.exists()) return true

        return modelFile.delete()
    }

    /**
     * Рекурсивно удаляет поддиректорию внутри директории модели.
     *
     * Используется для очистки связанных runtime-артефактов, например cache
     * или saved voices. Отсутствующая директория считается успешным результатом:
     * нужное конечное состояние уже достигнуто.
     *
     * @return `true`, если поддиректория отсутствует или была успешно удалена;
     * `false`, если удалить её полностью не удалось.
     *
     * @since 0.2
     */
    fun deleteModelSubdirectory(
        modelNameAsDirectory: String,
        directoryName:        String,
    ): Boolean {
        val subdirectory = getModelSubdirectory(
            modelNameAsDirectory = modelNameAsDirectory,
            directoryName        = directoryName,
        )

        if (!subdirectory.exists()) return true

        return subdirectory.deleteRecursively()
    }

    /**
     * Отменяет задачу загрузки по системному `downloadId`.
     *
     * Метод напрямую делегирует отмену в [DownloadManager.remove].
     * Если задача уже завершена, отсутствует или была удалена ранее, поведение
     * определяется системным [DownloadManager].
     *
     * @param downloadId идентификатор задачи, ранее полученный из [enqueueNew].
     * @since 0.2
     */
    fun cancelBy(downloadId: Long) {
        downloadManager.remove(downloadId)
    }

    /**
     * Читает текущее состояние задачи загрузки из [DownloadManager].
     *
     * Метод преобразует платформенный cursor/result в доменный
     * [KsenaxDownloadTaskSnapshot], чтобы верхние слои приложения не зависели
     * от Android column names, status constants и cursor API.
     *
     * Снапшот содержит:
     *
     * - [KsenaxDownloadTaskSnapshot.progress] — прогресс от `0f` до `1f`;
     * - [KsenaxDownloadTaskSnapshot.state] — доменное состояние загрузки;
     * - [KsenaxDownloadTaskSnapshot.reasonCode] — системный reason code,
     *   если он доступен.
     *
     * Если задача не найдена, cursor недоступен или в результате нет обязательной
     * status-колонки, возвращается `null`.
     *
     * Для успешной загрузки прогресс принудительно считается равным `1f`.
     * Если общий размер файла неизвестен, прогресс считается `0f`, пока задача
     * не перейдёт в успешное состояние.
     * @since 0.2
     */
    fun querySnapshotBy(downloadId: Long): KsenaxDownloadTaskSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            val statusColumnIndex =
                it.getColumnIndex(DownloadManager.COLUMN_STATUS)

            if (statusColumnIndex < 0) return null

            val status = it.getInt(statusColumnIndex)
            val downloadedBytes = it.getLongOrDefault(
                columnName = DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                defaultValue = 0L,
            )
            val totalBytes = it.getLongOrDefault(
                columnName = DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                defaultValue = -1L,
            )
            val reasonCode = it.getIntOrNull(DownloadManager.COLUMN_REASON)
            val currentState = status.toKsenaxDownloadState()
            val progress = when {
                currentState == KsenaxDownloadState.SUCCESSFUL -> 1f
                totalBytes > 0L -> {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                }
                else -> 0f
            }

            return KsenaxDownloadTaskSnapshot(
                progress   = progress,
                state      = currentState,
                reasonCode = reasonCode,
            )
        }
    }

    /**
     * Проверяет наличие непустого локального файла модели.
     *
     * Это мягкая проверка download candidate-а. Она отвечает только на вопрос:
     * "есть ли на диске файл, который потенциально может быть моделью?".
     *
     * Метод не проверяет точный размер и SHA-256. Для строгой проверки
     * установленной модели используется [hasValidModelFile].
     *
     * @return `true`, если файл существует и его размер больше нуля.
     * @since 0.2
     */
    fun hasModelCandidate(
        modelNameAsDirectory: String,
        fileName:             String,
    ): Boolean {
        val modelFile = getModelFile(
            modelNameAsDirectory = modelNameAsDirectory,
            fileName             = fileName,
        )

        return modelFile.exists() && modelFile.length() > 0L
    }

    /**
     * Выполняет строгую проверку локального файла модели.
     *
     * Файл считается валидным только если:
     *
     * 1. существует;
     * 2. является обычным файлом;
     * 3. имеет размер, равный [expectedSizeBytes];
     * 4. имеет SHA-256, совпадающий с [expectedSha256].
     *
     * Такая проверка защищает runtime от запуска неполных, битых,
     * случайно подменённых или устаревших model артефактов.
     *
     * @return `true`, если файл прошёл проверку размера и SHA-256.
     *
     * @throws IllegalArgumentException если [expectedSizeBytes] не положительный
     * или [expectedSha256] не является 64-символьной hexadecimal SHA-256 строкой.
     * @since 0.2
     */
    fun hasValidModelFile(
        modelNameAsDirectory: String,
        fileName:             String,
        expectedSizeBytes:    Long,
        expectedSha256:       String,
    ): Boolean {
        require(expectedSizeBytes > 0)
        require(expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$")))

        val modelFile = getModelFile(
            modelNameAsDirectory = modelNameAsDirectory,
            fileName             = fileName,
        )

        if (!modelFile.exists() || !modelFile.isFile) {
            return false
        }

        if (modelFile.length() != expectedSizeBytes) {
            return false
        }

        return calculateSha256(modelFile).equals(
            expectedSha256,
            ignoreCase = true,
        )
    }

    /**
     * Преобразует системный статус [DownloadManager] в доменное состояние загрузки.
     *
     * Такое преобразование удерживает Android-specific constants внутри backend-а.
     * ViewModel и domain/use-case слой работают только с [KsenaxDownloadState].
     */
    private fun Int.toKsenaxDownloadState(): KsenaxDownloadState {
        return when (this) {
            DownloadManager.STATUS_PENDING    -> KsenaxDownloadState.PENDING
            DownloadManager.STATUS_RUNNING    -> KsenaxDownloadState.RUNNING
            DownloadManager.STATUS_PAUSED     -> KsenaxDownloadState.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> KsenaxDownloadState.SUCCESSFUL
            DownloadManager.STATUS_FAILED     -> KsenaxDownloadState.FAILED
            else                              -> KsenaxDownloadState.UNKNOWN
        }
    }

    /**
     * Безопасно читает Long-значение из cursor-а.
     *
     * Если колонка отсутствует или значение в ней равно `NULL`, возвращает
     * [defaultValue]. Это защищает snapshot-сборку от различий в доступности
     * колонок [DownloadManager] на разных версиях Android или в нестандартных
     * состояниях загрузки.
     */
    private fun android.database.Cursor.getLongOrDefault(
        columnName: String,
        defaultValue: Long,
    ): Long {
        val columnIndex = getColumnIndex(columnName)
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getLong(columnIndex)
        } else {
            defaultValue
        }
    }

    /**
     * Безопасно читает Int-значение из cursor-а.
     *
     * Если колонка отсутствует или значение в ней равно `NULL`, возвращает `null`.
     * Используется для необязательных системных reason code-ов.
     */
    private fun android.database.Cursor.getIntOrNull(columnName: String): Int? {
        val columnIndex = getColumnIndex(columnName)
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getInt(columnIndex)
        } else {
            null
        }
    }

    /**
     * Вычисляет SHA-256 хэш файла потоковым чтением.
     *
     * Файл читается блоками по [DEFAULT_BUFFER_SIZE], поэтому метод подходит
     * для больших модельных файлов и не загружает весь artifact в память.
     *
     * @return шестнадцатеричный вид SHA-256 строки в lowercase.
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        file.inputStream().use { inputStream ->
            while (true) {
                val readBytes = inputStream.read(buffer)

                if (readBytes == -1) break

                digest.update(buffer, 0, readBytes)
            }
        }

        return digest.digest()
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }
}
