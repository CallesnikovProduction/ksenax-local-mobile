package com.kolesnikovprod.ksetaorch.storage.resolve.text

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat.FileNameFormatResolution
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat.Companion.readBufferedUtf8Text
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver.Companion.ensureWorkspaceZoneMarkerFile
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileLocation
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileReadResult
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileStorageKind
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileWriteResult
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Менеджер видимой пользовательской папки `Documents\ksenax-workspace`.
 *
 * Класс реализует публичное текстовое хранилище для заметок и артефактов
 * Ksenax. В отличие от app-private fallback-хранилища, эти файлы должны
 * оставаться доступными пользователю через обычный файловый менеджер, даже
 * если приложение закрыто.
 *
 * Реализация выбирает Android API по версии системы:
 * - Android 10+ работает через MediaStore и возвращает `content://Uri`;
 * - Android 9- пишет напрямую в публичную Documents-папку через legacy File API.
 *
 * Сейчас директория задана константой. Целевая SAF-модель должна заменить этот
 * жёсткий путь пользовательским выбором папки и persistable URI permission.
 * Перед успешной записью storage создаёт `there.ksenaxzone`, чтобы папка имела
 * общий marker-файл рабочей области Ksenax.
 * Перезапись существующих пользовательских файлов проходит через temporary
 * файл или temporary MediaStore-документ, чтобы не портить старое содержимое
 * при сбое записи.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class DocumentsFolderTextFileManager(
    private val context: Context
) : KsenaxTextFileResolver {

    private val appContext              = context.applicationContext
    override val destinationDescription = VISIBLE_DOCUMENTS_DISPLAY_PATH

    /**
     * Создаёт marker default-workspace до первого обращения tool-а.
     *
     * Agentic-контур вызывает этот метод при подготовке coordinator-а, поэтому
     * Documents-папка готова до первого чтения или записи заметки.
     *
     * @since 0.2
     */
    fun initializeWorkspaceZone(): KsenaxTextFileWriteResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ensureWorkspaceZoneFileInMediaStore()?.let { failure ->
                    failure
                } ?: findMediaStoreFileUri(KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME)
                    ?.let { markerUri ->
                        KsenaxTextFileWriteResult.Success(
                            location = KsenaxTextFileLocation(
                                displayPath = "$VISIBLE_DOCUMENTS_DISPLAY_PATH\\" +
                                        KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME,
                                storageKind = KsenaxTextFileStorageKind.MEDIA_STORE,
                                uri = markerUri.toString(),
                            )
                        )
                    }
                    ?: KsenaxTextFileWriteResult.Failure(
                        message = "Не удалось найти marker-файл в " +
                                VISIBLE_DOCUMENTS_DISPLAY_PATH,
                    )
            } else {
                val directory = resolvePublicDocumentsDirectory()
                directory.mkdirs()
                require(directory.isDirectory) {
                    "Не удалось создать $VISIBLE_DOCUMENTS_DISPLAY_PATH."
                }
                directory.ensureWorkspaceZoneMarkerFile()
                val markerFile = File(
                    directory,
                    KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME,
                )
                KsenaxTextFileWriteResult.Success(
                    location = KsenaxTextFileLocation(
                        displayPath = markerFile.absolutePath,
                        storageKind = KsenaxTextFileStorageKind.PUBLIC_DOCUMENTS,
                        uri = null,
                    )
                )
            }
        } catch (exception: Exception) {
            KsenaxTextFileWriteResult.Failure(
                message = exception.message
                    ?: "Не удалось инициализировать $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
            )
        }
    }

    override suspend fun readText(
        fileName: String
    ): KsenaxTextFileReadResult {

        if (!fileName.isSafeSingleFileName())
            return KsenaxTextFileReadResult.Failure(
                message = "Название файла не является простым (одиночным)"
            )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readTextFromMediaStore(                   // Android 10+ => MediaStore
                fileName = fileName
            )
        } else {
            readTextFromPublicDocumentsFile(fileName) // Android 9-  => File API
        }
    }

    /**
     * Читает существующий файл из публичной Documents-папки через MediaStore.
     *
     * Метод используется на Android 10+ и работает через `content://Uri`, а не
     * через прямой filesystem path. Сначала ищет файл по имени и
     * [mediaStoreRelativePath], затем открывает input stream и возвращает
     * [KsenaxTextFileReadResult.Success] с [KsenaxTextFileStorageKind.MEDIA_STORE].
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readTextFromMediaStore(
        fileName: String
    ): KsenaxTextFileReadResult {
        val resolver = appContext.contentResolver
        val existingUri = findMediaStoreFileUri(fileName)
            ?: return KsenaxTextFileReadResult.NotFound(fileName)

        return try {
            val text = resolver.openInputStream(existingUri)?.use { inputStream ->
                inputStream.readBufferedUtf8Text()
            } ?: return KsenaxTextFileReadResult.Failure(
                message = "Не удалось открыть поток чтения файла в $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
            )

            KsenaxTextFileReadResult.Success(
                text = text,
                location = KsenaxTextFileLocation(
                    displayPath = "$VISIBLE_DOCUMENTS_DISPLAY_PATH\\$fileName",
                    storageKind = KsenaxTextFileStorageKind.MEDIA_STORE,
                    uri = existingUri.toString(),
                )
            )
        } catch (exception: Exception) {
            KsenaxTextFileReadResult.Failure(
                message = exception.message
                    ?: "Не удалось прочитать файл из $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
            )
        }
    }

    /**
     * Читает существующий файл из публичной Documents-папки через legacy File API.
     *
     * Метод используется только на Android 9-: он строит обычный [File] внутри
     * [resolvePublicDocumentsDirectory], читает его buffered UTF-8 reader-ом и
     * возвращает [KsenaxTextFileStorageKind.PUBLIC_DOCUMENTS].
     *
     * @since 0.2
     */
    private fun readTextFromPublicDocumentsFile(fileName: String): KsenaxTextFileReadResult {
        val targetFile = File(resolvePublicDocumentsDirectory(), fileName)

        return try {
            if (targetFile.isFile) {
                KsenaxTextFileReadResult.Success(
                    text = targetFile.readBufferedUtf8Text(),
                    location = KsenaxTextFileLocation(
                        displayPath = targetFile.absolutePath,
                        storageKind = KsenaxTextFileStorageKind.PUBLIC_DOCUMENTS,
                        uri = null,
                    )
                )
            } else {
                KsenaxTextFileReadResult.NotFound(
                    fileName = fileName
                )
            }
        } catch (exception: Exception) {
            KsenaxTextFileReadResult.Failure(
                message = exception.message
                    ?: "Не удалось прочитать файл из публичной Documents-папки.",
            )
        }
    }

    override suspend fun writeText(
        fileName: String,
        text:     String,
        format:   KsenaxTextFileFormat,
    ): KsenaxTextFileWriteResult {
        val targetFileName = normalizeTargetFileName(
            fileName = fileName,
            format   = format,
        ) ?: return KsenaxTextFileWriteResult.Failure(
            message = "Неподдерживаемое или небезопасное имя текстового файла: $fileName",
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeTextToMediaStore(          // Android 10+ => MediaStore
                fileName = targetFileName,
                text     = text,
                mimeType = format.mimeType,
            )
        } else {
            writeTextToPublicDocumentsFile( // Android 9-  => File API
                fileName = targetFileName,
                text     = text,
            )
        }
    }

    /**
     * Записывает файл в публичную Documents-папку через MediaStore.
     *
     * [mimeType] должен приходить из [KsenaxTextFileFormat.mimeType], чтобы Android
     * и другие приложения видели файл как markdown/plain text, а не как
     * безымянный бинарный артефакт. Новый файл создаётся с `IS_PENDING = 1`, а
     * после успешной записи помечается как готовый через `IS_PENDING = 0`.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeTextToMediaStore(
        fileName: String,
        text:     String,
        mimeType: String,
    ): KsenaxTextFileWriteResult {
        val existingUri = findMediaStoreFileUri(fileName)

        return try {
            ensureWorkspaceZoneFileInMediaStore()?.let { failure ->
                return failure
            }

            val targetUri = if (existingUri == null) {
                createPendingMediaStoreFile(
                    fileName = fileName,
                    mimeType = mimeType,
                )?.also { newUri ->
                    writeTextToMediaStoreUri(newUri, text)
                    markMediaStoreFileCompleted(newUri)
                } ?: return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось создать файл в $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
                )
            } else {
                replaceExistingMediaStoreFileThroughTemporaryDocument(
                    existingUri = existingUri,
                    fileName    = fileName,
                    text        = text,
                    mimeType    = mimeType,
                ) ?: return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось безопасно перезаписать файл в $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
                )
            }

            KsenaxTextFileWriteResult.Success(
                location = KsenaxTextFileLocation(
                    displayPath = "$VISIBLE_DOCUMENTS_DISPLAY_PATH\\$fileName",
                    storageKind = KsenaxTextFileStorageKind.MEDIA_STORE,
                    uri         = targetUri.toString(),
                )
            )
        } catch (exception: Exception) {
            KsenaxTextFileWriteResult.Failure(
                message = exception.message
                    ?: "Не удалось записать файл в $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
            )
        }
    }

    /**
     * Записывает файл в публичную Documents-папку через legacy File API.
     *
     * Метод используется только на Android 9-: создаёт целевую директорию при
     * необходимости, пишет UTF-8 текст в обычный [File] и возвращает
     * [KsenaxTextFileStorageKind.PUBLIC_DOCUMENTS].
     *
     * @since 0.2
     */
    private fun writeTextToPublicDocumentsFile(
        fileName: String,
        text:     String,
    ): KsenaxTextFileWriteResult {
        val directory = resolvePublicDocumentsDirectory()
        val targetFile = File(directory, fileName)

        return try {
            directory.mkdirs()

            if (!directory.isDirectory) {
                return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось создать $VISIBLE_DOCUMENTS_DISPLAY_PATH.",
                )
            }

            directory.ensureWorkspaceZoneMarkerFile()

            writeTextToTemporaryFileAndReplace(
                targetFile = targetFile,
                text       = text,
            )

            KsenaxTextFileWriteResult.Success(
                location = KsenaxTextFileLocation(
                    displayPath = targetFile.absolutePath,
                    storageKind = KsenaxTextFileStorageKind.PUBLIC_DOCUMENTS,
                    uri         = null,
                )
            )
        } catch (exception: Exception) {
            KsenaxTextFileWriteResult.Failure(
                message = exception.message
                    ?: "Не удалось записать файл в публичную Documents-папку.",
            )
        }
    }

    /**
     * Создаёт новый pending-документ в MediaStore без записи контента.
     *
     * Pending-состояние скрывает незавершённый файл от других приложений до тех
     * пор, пока backend не запишет текст и не вызовет [markMediaStoreFileCompleted].
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createPendingMediaStoreFile(
        fileName: String,
        mimeType: String,
    ): Uri? {
        return appContext.contentResolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, mediaStoreRelativePath())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        )
    }

    /**
     * Пишет UTF-8 текст в MediaStore-документ через `content://Uri`.
     *
     * Режим `wt` используется только для нового temporary-документа либо нового
     * pending-файла. Существующий пользовательский файл этим методом напрямую
     * не усекается.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeTextToMediaStoreUri(
        targetUri: Uri,
        text:      String,
    ) {
        appContext.contentResolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
            outputStream.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException(
            "Не удалось открыть поток записи файла в $VISIBLE_DOCUMENTS_DISPLAY_PATH."
        )
    }

    /**
     * Помечает новый MediaStore-файл как готовый для других приложений.
     *
     * Метод вызывается только после успешной записи контента.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun markMediaStoreFileCompleted(targetUri: Uri) {
        appContext.contentResolver.update(
            targetUri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
    }

    /**
     * Безопасно заменяет существующий MediaStore-файл через temporary-документ.
     *
     * Сначала backend пишет новое содержимое во временный документ. После этого
     * старый файл получает backup-имя, temporary-документ получает исходное имя,
     * а backup удаляется. Если promotion temporary-документа падает, backend
     * пытается вернуть backup обратно под исходным именем.
     *
     * @return Uri нового документа с исходным именем или `null`, если replace не
     * удалось завершить.
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun replaceExistingMediaStoreFileThroughTemporaryDocument(
        existingUri: Uri,
        fileName:    String,
        text:        String,
        mimeType:    String,
    ): Uri? {
        val temporaryName = fileName.toTemporarySiblingFileName()
        val backupName = fileName.toBackupSiblingFileName()
        val temporaryUri = createPendingMediaStoreFile(
            fileName = temporaryName,
            mimeType = mimeType,
        ) ?: return null
        var backupCreated = false

        return try {
            writeTextToMediaStoreUri(temporaryUri, text)
            markMediaStoreFileCompleted(temporaryUri)

            if (!renameMediaStoreFile(existingUri, backupName)) {
                deleteMediaStoreFile(temporaryUri)
                return null
            }
            backupCreated = true

            if (!renameMediaStoreFile(temporaryUri, fileName)) {
                renameMediaStoreFile(existingUri, fileName)
                deleteMediaStoreFile(temporaryUri)
                return null
            }

            deleteMediaStoreFile(existingUri)
            temporaryUri
        } catch (exception: Exception) {
            if (backupCreated) {
                runCatching {
                    renameMediaStoreFile(existingUri, fileName)
                }
            }
            deleteMediaStoreFile(temporaryUri)
            null
        }
    }

    /**
     * Переименовывает MediaStore-документ через update `DISPLAY_NAME`.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameMediaStoreFile(
        uri:      Uri,
        newName:  String,
    ): Boolean {
        val updatedRows = appContext.contentResolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            },
            null,
            null,
        )

        return updatedRows > 0
    }

    /**
     * Удаляет MediaStore-документ, если он ещё существует.
     *
     * Cleanup не должен маскировать основную ошибку replace-процедуры, поэтому
     * вызывающий код не зависит от результата удаления.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteMediaStoreFile(uri: Uri): Boolean {
        return appContext.contentResolver.delete(uri, null, null) > 0
    }

    /**
     * Записывает текст во временный sibling-файл и затем заменяет им target.
     *
     * Legacy File API получает тот же защитный порядок, что и app-private
     * backend: старый файл остаётся нетронутым, пока новый текст не записан во
     * временный файл.
     *
     * @since 0.2
     */
    private fun writeTextToTemporaryFileAndReplace(
        targetFile: File,
        text:       String,
    ) {
        val temporaryFile = File(
            targetFile.parentFile,
            targetFile.name.toTemporarySiblingFileName(),
        )

        try {
            temporaryFile.writeText(
                text = text,
                charset = Charsets.UTF_8,
            )
            moveTemporaryFileToTarget(
                temporaryFile = temporaryFile,
                targetFile    = targetFile,
            )
        } finally {
            if (temporaryFile.isFile) {
                temporaryFile.delete()
            }
        }
    }

    /**
     * Перемещает prepared temporary-файл на место целевого файла.
     *
     * Backend сначала пробует atomic move, затем делает обычный replace, если
     * filesystem не поддерживает атомарное перемещение.
     *
     * @since 0.2
     */
    private fun moveTemporaryFileToTarget(
        temporaryFile: File,
        targetFile:    File,
    ) {
        try {
            Files.move(
                temporaryFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    /**
     * Создаёт имя временного sibling-файла или temporary-документа.
     *
     * @since 0.2
     */
    private fun String.toTemporarySiblingFileName(): String {
        return "$this.ksenax-tmp-${System.currentTimeMillis()}-${System.nanoTime()}"
    }

    /**
     * Создаёт имя backup sibling-файла или backup-документа.
     *
     * @since 0.2
     */
    private fun String.toBackupSiblingFileName(): String {
        return "$this.ksenax-backup-${System.currentTimeMillis()}-${System.nanoTime()}"
    }

    /**
     * Создаёт `there.ksenaxzone` в MediaStore-папке, если marker-файла ещё нет.
     *
     * MediaStore не даёт работать с директорией как с обычным [File], поэтому
     * marker создаётся как отдельный документ с тем же [mediaStoreRelativePath],
     * что и пользовательские текстовые артефакты.
     *
     * Legacy marker-ы с добавленным provider-ом расширением `.txt` мигрируют в
     * один документ с точным именем `there.ksenaxzone`.
     *
     * @return `null`, если marker уже есть или успешно создан; иначе DTO failure.
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ensureWorkspaceZoneFileInMediaStore(): KsenaxTextFileWriteResult.Failure? =
        synchronized(KsenaxTextFileResolver.workspaceZoneInitializationLock) {
            val markerDocuments = findMediaStoreWorkspaceZoneDocuments()
            val canonicalMarker = markerDocuments.firstOrNull { document ->
                document.displayName == KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME
            }

            canonicalMarker?.let { marker ->
                val existingText = appContext.contentResolver
                    .openInputStream(marker.uri)
                    ?.use { inputStream -> inputStream.readBufferedUtf8Text() }
                    ?: return@synchronized KsenaxTextFileWriteResult.Failure(
                        message = "Не удалось прочитать marker-файл в " +
                                VISIBLE_DOCUMENTS_DISPLAY_PATH,
                    )
                val updatedText = KsenaxTextFileResolver.ensureWorkspaceDirectoryEntry(
                    existingText = existingText,
                    destinationDescription = VISIBLE_DOCUMENTS_DISPLAY_PATH,
                )
                if (updatedText != existingText) {
                    writeTextToMediaStoreUri(marker.uri, updatedText)
                }

                val duplicateDocuments = markerDocuments.filterNot { it.uri == marker.uri }
                val duplicatesDeleted = duplicateDocuments
                    .map { deleteMediaStoreFile(it.uri) }
                    .all { it }
                if (!duplicatesDeleted) {
                    return@synchronized KsenaxTextFileWriteResult.Failure(
                        message = "Не удалось удалить дубликаты marker-файла в " +
                                VISIBLE_DOCUMENTS_DISPLAY_PATH,
                    )
                }
                return@synchronized null
            }

            val legacyText = markerDocuments.firstOrNull()?.let { legacyMarker ->
                appContext.contentResolver
                    .openInputStream(legacyMarker.uri)
                    ?.use { inputStream -> inputStream.readBufferedUtf8Text() }
                    ?: return@synchronized KsenaxTextFileWriteResult.Failure(
                        message = "Не удалось прочитать legacy marker-файл в " +
                                VISIBLE_DOCUMENTS_DISPLAY_PATH,
                    )
            }
            val markerText = if (legacyText == null) {
                KsenaxTextFileResolver.workspaceZoneDocumentText(
                    VISIBLE_DOCUMENTS_DISPLAY_PATH
                )
            } else {
                KsenaxTextFileResolver.ensureWorkspaceDirectoryEntry(
                    existingText = legacyText,
                    destinationDescription = VISIBLE_DOCUMENTS_DISPLAY_PATH,
                )
            }
            val markerUri = createPendingMediaStoreFile(
                fileName = KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME,
                mimeType = KsenaxTextFileResolver.WORKSPACE_ZONE_MIME_TYPE,
            ) ?: return@synchronized KsenaxTextFileWriteResult.Failure(
                message = "Не удалось создать marker-файл рабочей области в " +
                        VISIBLE_DOCUMENTS_DISPLAY_PATH,
            )

            try {
                writeTextToMediaStoreUri(markerUri, markerText)
                markMediaStoreFileCompleted(markerUri)
            } catch (exception: Exception) {
                deleteMediaStoreFile(markerUri)
                throw exception
            }

            if (findMediaStoreFileUri(KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME) != markerUri) {
                deleteMediaStoreFile(markerUri)
                return@synchronized KsenaxTextFileWriteResult.Failure(
                    message = "MediaStore изменил имя marker-файла в " +
                            VISIBLE_DOCUMENTS_DISPLAY_PATH,
                )
            }

            val legacyDocumentsDeleted = markerDocuments
                .map { deleteMediaStoreFile(it.uri) }
                .all { it }
            if (!legacyDocumentsDeleted) {
                return@synchronized KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось удалить дубликаты marker-файла в " +
                            VISIBLE_DOCUMENTS_DISPLAY_PATH,
                )
            }

            null
        }

    /**
     * Нормализует имя файла для записи в Documents backend.
     *
     * Если расширения нет, добавляется каноническое расширение из [format]. Если
     * расширение есть, оно должно совпадать с [format]. Неподдерживаемые
     * расширения получают отказ, чтобы `note.pdf` не превратился в `note.pdf.md`.
     *
     * @return итоговое имя файла или `null`, если имя небезопасно либо формат
     * конфликтует с расширением.
     * @since 0.2
     */
    private fun normalizeTargetFileName(
        fileName: String,
        format:   KsenaxTextFileFormat,
    ): String? {
        val normalizedFileName = fileName.trim()

        if (!normalizedFileName.isSafeSingleFileName()) {
            return null
        }

        return when (val resolution = KsenaxTextFileFormat.resolveFileName(normalizedFileName)) {
            FileNameFormatResolution.MissingExtension ->
                "$normalizedFileName.${format.canonicalExtension}"

            is FileNameFormatResolution.Supported ->
                if (resolution.format == format) normalizedFileName else null

            is FileNameFormatResolution.UnsupportedExtension ->
                null
        }
    }

    /**
     * Ищет уже существующий файл в MediaStore и возвращает его `content://Uri`.
     *
     * Поиск идёт строго по паре `DISPLAY_NAME + RELATIVE_PATH`, чтобы не
     * перепутать одинаковые имена файлов из разных Documents-подпапок.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreFileUri(
        fileName: String
    ): Uri? {
        return findMediaStoreFileUris(fileName).firstOrNull()
    }

    /**
     * Возвращает все MediaStore-документы с заданным именем в workspace.
     *
     * Обычные чтения используют первый URI. Marker-инициализация дополнительно
     * удаляет дубликаты, чтобы `there.ksenaxzone` оставался единственным.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreFileUris(
        fileName: String
    ): List<Uri> {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, mediaStoreRelativePath())
        val matches = mutableListOf<Uri>()

        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idIndex)
                matches += ContentUris.withAppendedId(collection, id)
            }
        }

        return matches
    }

    /**
     * Возвращает канонический marker и legacy-дубликаты из текущего workspace.
     *
     * В отличие от обычного поиска файла, запрос ограничивается директорией, а
     * display name фильтруется через
     * [KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName]. Так storage
     * видит имена, которым Android раньше добавил `.txt` и числовой суффикс.
     *
     * @since 0.2
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreWorkspaceZoneDocuments(): List<MediaStoreWorkspaceZoneDocument> {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(mediaStoreRelativePath())
        val matches = mutableListOf<MediaStoreWorkspaceZoneDocument>()

        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)
                if (KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(displayName)) {
                    matches += MediaStoreWorkspaceZoneDocument(
                        uri = ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(idIndex),
                        ),
                        displayName = displayName,
                    )
                }
            }
        }

        return matches
    }

    /**
     * Возвращает legacy Documents-директорию для Android 9-.
     *
     * Путь строится как публичная системная Documents-папка плюс
     * [VISIBLE_DOCUMENTS_CHILD_PATH]. На Android 10+ этот путь не должен быть
     * основным способом доступа; там используется MediaStore.
     *
     * @since 0.2
     */
    private fun resolvePublicDocumentsDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS
            ),
            VISIBLE_DOCUMENTS_CHILD_PATH,
        )
    }

    /**
     * Возвращает MediaStore-relative путь целевой Documents-папки.
     *
     * Значение должно заканчиваться `/`, потому что MediaStore хранит
     * `RELATIVE_PATH` именно как путь директории, а не как путь файла.
     *
     * @since 0.2
     */
    private fun mediaStoreRelativePath(): String {
        return "${Environment.DIRECTORY_DOCUMENTS}/$VISIBLE_DOCUMENTS_CHILD_PATH/"
    }

    private data class MediaStoreWorkspaceZoneDocument(
        val uri: Uri,
        val displayName: String,
    )

    private companion object {
        const val VISIBLE_DOCUMENTS_DISPLAY_PATH = "Documents\\ksenax-workspace"

        const val VISIBLE_DOCUMENTS_CHILD_PATH = "ksenax-workspace"
    }
}
