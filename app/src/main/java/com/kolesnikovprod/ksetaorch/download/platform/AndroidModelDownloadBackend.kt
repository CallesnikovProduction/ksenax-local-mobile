package com.kolesnikovprod.ksetaorch.download.platform

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import com.kolesnikovprod.ksetaorch.download.domain.DownloadTransferMetricsEstimator
import com.kolesnikovprod.ksetaorch.download.domain.FileIntegrityVerifier
import com.kolesnikovprod.ksetaorch.download.domain.FileTreeDeleter
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadWaitReason
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files

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
     * Окно фактических byte-снапшотов для сглаживания скорости и ETA.
     */
    private val transferMetricsEstimator = DownloadTransferMetricsEstimator()

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
        expectedSizeBytes: Long = 0L,
        displayName: String = fileName,
        staging: Boolean = false,
        extraRequiredBytes: Long = 0L,
    ): Long {
        require(URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }) {
            "Model-URL must be HTTPS without credentials"
        }
        val directory = if (staging) {
            stagingDirectory(modelNameAsDirectory)
        } else {
            getModelDirectory(modelNameAsDirectory)
        }
        if ((!directory.isDirectory && !directory.mkdirs()) || !directory.canWrite()) {
            throw IOException("Model destination is not writable")
        }
        if (expectedSizeBytes > 0L && directory.usableSpace < expectedSizeBytes + extraRequiredBytes) {
            throw InsufficientModelStorageException()
        }
        val relativePath = if (staging) {
            "${modelDirectoryPath("$STAGING_DIRECTORY_NAME-$modelNameAsDirectory")}/" +
                requireSafePathSegment(fileName, "fileName")
        } else {
            modelFilePath(modelNameAsDirectory, fileName)
        }

        val request = DownloadManager.Request(url.toUri())
            .setTitle(displayName)
            .setDescription("OpenKsenax: downloading model")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(allowOverMeteredNetwork)
            .setAllowedOverRoaming(allowOverRoaming)
            .setDestinationInExternalFilesDir(
                appContext,
                null,
                relativePath,
            )

        return downloadManager.enqueue(request)
    }

    fun downloadFile(downloadId: Long, sourceUrl: String, directoryName: String, legacyName: String): File {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            check(cursor != null && cursor.moveToFirst()) { "Download task missing" }
            check(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)) == sourceUrl) {
                "Download source changed"
            }
            val uri = URI(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)))
            check(uri.scheme == "file") { "Download destination must be a file" }
            val file = File(uri).canonicalFile
            val stagingDirectory = stagingDirectory(directoryName)
            val legacyFile = getModelFile(directoryName, legacyName)
            check(file.parentFile == stagingDirectory || file == legacyFile) {
                "Download destination outside model storage"
            }
            return file
        }
    }

    fun findExistingDownload(sourceUrl: String, directoryName: String, legacyName: String): Long? {
        downloadManager.query(DownloadManager.Query()).use { cursor ->
            if (cursor == null) throw IOException("Download provider unavailable")
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)) != sourceUrl) continue
                val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (state == DownloadManager.STATUS_FAILED) continue
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val candidate = runCatching { downloadFile(id, sourceUrl, directoryName, legacyName) }.getOrNull()
                    ?: continue
                // Legacy runtime rows are not resumable staging after a successful install.
                if (candidate.parentFile != stagingDirectory(directoryName)) {
                    if (state != DownloadManager.STATUS_SUCCESSFUL) return id
                    continue
                }
                if (state == DownloadManager.STATUS_SUCCESSFUL && !candidate.isFile) continue
                return id
            }
        }
        return null
    }

    fun deleteDownloadArtifacts(downloadId: Long, sourceUrl: String, directoryName: String, legacyName: String): Boolean {
        val file = runCatching { downloadFile(downloadId, sourceUrl, directoryName, legacyName) }.getOrNull()
            ?: return true
        if (file.parentFile != stagingDirectory(directoryName)) return true
        // remove(id) also removes the destination; only staging rows may be removed here.
        downloadManager.remove(downloadId)
        return FileTreeDeleter.deleteIfExists(file)
    }

    private fun stagingDirectory(directoryName: String): File =
        getModelDirectory("$STAGING_DIRECTORY_NAME-" + requireSafePathSegment(directoryName, "directoryName"))

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
        val safeFileName = requireSafePathSegment(fileName, "fileName")
        return "${modelDirectoryPath(modelNameAsDirectory)}/$safeFileName"
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
        val safeDirectoryName = requireSafePathSegment(
            value = modelNameAsDirectory,
            argumentName = "modelNameAsDirectory",
        )
        return "models/$safeDirectoryName"
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
        val safeFileName = requireSafePathSegment(fileName, "fileName")
        return resolveSafeChild(
            parent = getModelDirectory(modelNameAsDirectory),
            childName = safeFileName,
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
        val externalFilesDirectory = checkNotNull(appContext.getExternalFilesDir(null)) {
                "App-specific external storage is unavailable"
            }
        val modelsDirectory = resolveSafeChild(
            parent = externalFilesDirectory,
            childName = MODELS_DIRECTORY_NAME,
        )

        return resolveSafeChild(
            parent = modelsDirectory,
            childName = requireSafePathSegment(
                modelNameAsDirectory,
                "modelNameAsDirectory",
            ),
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
        val safeDirectoryName = requireSafePathSegment(
            value = directoryName,
            argumentName = "directoryName",
        )
        return resolveSafeChild(
            parent = getModelDirectory(modelNameAsDirectory),
            childName = safeDirectoryName,
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

        return FileTreeDeleter.deleteIfExists(subdirectory)
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
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor == null) throw IOException("Download provider unavailable")
            if (cursor.moveToFirst() &&
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
            ) {
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val parent = runCatching { File(URI(uri)).parentFile?.name }.getOrNull()
                // A pre-SPEC-002 completed task may point at an installed runtime file.
                if (parent?.startsWith("$STAGING_DIRECTORY_NAME-") != true) return
            }
        }
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
     * - [KsenaxDownloadTaskSnapshot.transferMetrics] — реальные счётчики байтов,
     *   средняя скорость по короткому окну и ETA.
     *
     * `null` означает, что доступный provider не нашёл строку задачи.
     * Недоступный cursor/отсутствующая status-колонка вызывают IOException;
     * такую ошибку нельзя трактовать как разрешение удалить живую загрузку.
     *
     * Для успешной загрузки прогресс принудительно считается равным `1f`.
     * Если общий размер файла неизвестен, прогресс считается `0f`, пока задача
     * не перейдёт в успешное состояние.
     * @since 0.2
     */
    fun querySnapshotBy(downloadId: Long, expectedSourceUrl: String? = null): KsenaxDownloadTaskSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: throw IOException("Download provider unavailable")

        cursor.use {
            if (!it.moveToFirst()) return null
            if (expectedSourceUrl != null &&
                it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)) != expectedSourceUrl
            ) {
                return KsenaxDownloadTaskSnapshot(0f, KsenaxDownloadState.FAILED, reasonCode = -2)
            }

            val statusColumnIndex =
                it.getColumnIndex(DownloadManager.COLUMN_STATUS)

            if (statusColumnIndex < 0) throw IOException("Download provider status unavailable")

            val status = it.getInt(statusColumnIndex)
            val rawDownloadedBytes = it.getLongOrDefault(
                columnName = DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                defaultValue = 0L,
            )
            val rawTotalBytes = it.getLongOrDefault(
                columnName = DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                defaultValue = -1L,
            )
            val reasonCode = it.getIntOrNull(DownloadManager.COLUMN_REASON)
            val currentState = status.toKsenaxDownloadState()
            val waitReason = if (
                currentState == KsenaxDownloadState.PAUSED
            ) {
                reasonCode.toKsenaxDownloadWaitReason()
            } else {
                null
            }
            val totalBytes = when {
                rawTotalBytes > 0L -> rawTotalBytes
                currentState == KsenaxDownloadState.SUCCESSFUL &&
                    rawDownloadedBytes > 0L -> rawDownloadedBytes
                else -> null
            }
            val downloadedBytes = when {
                currentState == KsenaxDownloadState.SUCCESSFUL && totalBytes != null ->
                    totalBytes
                else -> rawDownloadedBytes.coerceAtLeast(0L)
            }
            val progress = when {
                currentState == KsenaxDownloadState.SUCCESSFUL -> 1f
                totalBytes != null -> {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                }
                else -> 0f
            }
            val transferMetrics = transferMetricsEstimator.estimate(
                downloadId = downloadId,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
            )

            return KsenaxDownloadTaskSnapshot(
                progress        = progress,
                state           = currentState,
                reasonCode      = reasonCode,
                waitReason      = waitReason,
                transferMetrics = transferMetrics,
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
        checkActive: () -> Unit = {},
    ): Boolean {
        val modelFile = getModelFile(
            modelNameAsDirectory = modelNameAsDirectory,
            fileName             = fileName,
        )

        return FileIntegrityVerifier.matches(
            file = modelFile,
            expectedSizeBytes = expectedSizeBytes,
            expectedSha256 = expectedSha256,
            checkActive = checkActive,
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

    private fun Int?.toKsenaxDownloadWaitReason():
        KsenaxDownloadWaitReason {
        return when (this) {
            DownloadManager.PAUSED_WAITING_TO_RETRY ->
                KsenaxDownloadWaitReason.WAITING_TO_RETRY
            DownloadManager.PAUSED_WAITING_FOR_NETWORK ->
                KsenaxDownloadWaitReason.WAITING_FOR_NETWORK
            DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
                KsenaxDownloadWaitReason.WAITING_FOR_UNMETERED_NETWORK
            else ->
                KsenaxDownloadWaitReason.PAUSED_BY_SYSTEM
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

    private fun requireSafePathSegment(
        value: String,
        argumentName: String,
    ): String {
        require(
            value.isNotBlank() &&
                value != "." &&
                value != ".." &&
                '/' !in value &&
                '\\' !in value &&
                '\u0000' !in value
        ) {
            "$argumentName must be a single safe path segment"
        }

        return value
    }

    private fun resolveSafeChild(
        parent: File,
        childName: String,
    ): File {
        val canonicalParent = parent.canonicalFile
        val child = File(canonicalParent, childName)
        require(!Files.isSymbolicLink(child.toPath())) { "Model path must not be a symbolic link" }
        val canonicalChild = child.canonicalFile
        val childPrefix = canonicalParent.path + File.separator

        require(canonicalChild.path.startsWith(childPrefix)) {
            "Resolved path must stay inside ${canonicalParent.path}"
        }

        return canonicalChild
    }

    private companion object {
        const val MODELS_DIRECTORY_NAME = "models"
        const val STAGING_DIRECTORY_NAME = ".downloads"
    }
}

/** Storage preflight failure, separate from network failure. @since 0.4 */
internal class InsufficientModelStorageException : IOException("Insufficient model storage")
