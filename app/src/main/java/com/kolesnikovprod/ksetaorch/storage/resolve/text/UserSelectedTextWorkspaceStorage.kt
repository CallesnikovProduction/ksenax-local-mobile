package com.kolesnikovprod.ksetaorch.storage.resolve.text

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat.Companion.readBufferedUtf8Text
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat.FileNameFormatResolution
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileLocation
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileReadResult
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileStorageKind
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileWriteResult

/**
 * SAF *(SAF — Storage Access Framework)* хранилище для директории, которую пользователь выбрал вручную.
 *
 * Класс не открывает picker сам во время чтения или записи. Он работает с уже
 * выбранным `treeUri`, для которого UI-слой должен получить и сохранить
 * persistable read/write permission через Storage Access Framework.
 *
 * Внутри выбранной директории storage создаёт `there.ksenaxzone`. Этот marker-файл
 * фиксирует, что папка инициализирована как рабочая область Ksenax, а обычные
 * текстовые артефакты можно читать и записывать рядом с ним.
 * Перезапись существующего документа идёт через temporary-документ и rename,
 * чтобы не усекать старый файл до успешной записи нового текста.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class UserSelectedTextWorkspaceStorage(
    context: Context,
    private val workspaceTreeUri: Uri,
    workspaceDisplayName: String = "Selected-Ksenax-workspace",
) : KsenaxTextFileResolver {

    private val appContext = context.applicationContext
    override val destinationDescription: String = workspaceDisplayName

    override suspend fun readText(
        fileName: String
    ): KsenaxTextFileReadResult {
        val targetFileName = normalizeExistingFileName(fileName)
            ?: return KsenaxTextFileReadResult.Failure(
                message = "Неподдерживаемое или небезопасное имя текстового файла: $fileName",
            )

        return try {
            val documentUri = findSafDocumentUri(targetFileName)
                ?: return KsenaxTextFileReadResult.NotFound(targetFileName)
            val text = appContext.contentResolver.openInputStream(documentUri)?.use { inputStream ->
                inputStream.readBufferedUtf8Text()
            } ?: return KsenaxTextFileReadResult.Failure(
                message = "Не удалось открыть поток чтения SAF-файла.",
            )

            KsenaxTextFileReadResult.Success(
                text = text,
                location = toTextFileLocation(
                    fileName = targetFileName,
                    documentUri = documentUri,
                )
            )
        } catch (exception: Exception) {
            KsenaxTextFileReadResult.Failure(
                message = exception.message
                    ?: "Не удалось прочитать файл из выбранной пользователем директории.",
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

        return try {
            ensureWorkspaceZoneDocument()
                ?: return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось создать marker-файл рабочей области.",
                )

            val existingUri = findSafDocumentUri(targetFileName)
            val documentUri = if (existingUri != null) {
                replaceExistingSafDocumentThroughTemporaryDocument(
                    existingUri = existingUri,
                    fileName    = targetFileName,
                    text        = text,
                    mimeType    = format.mimeType,
                ) ?: return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось безопасно перезаписать SAF-файл: $targetFileName",
                )
            } else {
                val createdUri = createSafDocument(
                    fileName = targetFileName,
                    mimeType = format.mimeType,
                )

                if (createdUri == null) {
                    return KsenaxTextFileWriteResult.Failure(
                        message = "Не удалось создать SAF-файл: $targetFileName",
                    )
                }

                if (!writeTextToSafDocument(createdUri, text)) {
                    deleteSafDocument(createdUri)
                    return KsenaxTextFileWriteResult.Failure(
                        message = "Не удалось открыть поток записи SAF-файла.",
                    )
                }

                createdUri
            }

            KsenaxTextFileWriteResult.Success(
                location = toTextFileLocation(
                    fileName = targetFileName,
                    documentUri = documentUri,
                )
            )
        } catch (exception: Exception) {
            KsenaxTextFileWriteResult.Failure(
                message = exception.message
                    ?: "Не удалось записать файл в выбранную пользователем директорию.",
            )
        }
    }

    /**
     * Безопасно заменяет существующий SAF-документ через temporary-документ.
     *
     * Backend сначала пишет новый текст во временный документ. Затем старый
     * документ получает backup-имя, temporary-документ получает исходное имя, а
     * backup удаляется. Если provider не даёт переименовать temporary-документ,
     * backend пытается вернуть backup под исходным именем.
     *
     * @return Uri документа с исходным именем после replace или `null`, если
     * операцию не удалось завершить.
     * @since 0.2
     */
    private fun replaceExistingSafDocumentThroughTemporaryDocument(
        existingUri: Uri,
        fileName:    String,
        text:        String,
        mimeType:    String,
    ): Uri? {
        val temporaryUri = createSafDocument(
            fileName = fileName.toTemporarySiblingFileName(),
            mimeType = mimeType,
        ) ?: return null
        var backupUri: Uri? = null

        return try {
            if (!writeTextToSafDocument(temporaryUri, text)) {
                deleteSafDocument(temporaryUri)
                return null
            }

            val backupName = fileName.toBackupSiblingFileName()
            backupUri = renameSafDocument(existingUri, backupName)
                ?: run {
                    deleteSafDocument(temporaryUri)
                    return null
                }
            val backupDocumentUri = backupUri
                ?: return null
            val promotedUri = renameSafDocument(temporaryUri, fileName)

            if (promotedUri != null) {
                deleteSafDocument(backupDocumentUri)
                promotedUri
            } else {
                renameSafDocument(backupDocumentUri, fileName)
                deleteSafDocument(temporaryUri)
                null
            }
        } catch (exception: Exception) {
            backupUri?.let { backupDocumentUri ->
                runCatching {
                    renameSafDocument(backupDocumentUri, fileName)
                }
            }
            deleteSafDocument(temporaryUri)
            null
        }
    }

    /**
     * Инициализирует выбранную директорию как рабочую область Ksenax.
     *
     * Метод создаёт `there.ksenaxzone`, если marker-файла ещё нет, и возвращает DTO
     * результата для UI. Его можно вызвать сразу после выбора папки, чтобы
     * показать пользователю, что рабочая область готова.
     *
     * @since 0.2
     */
    fun initializeWorkspaceZone(): KsenaxTextFileWriteResult {
        return try {
            val markerUri = ensureWorkspaceZoneDocument()
                ?: return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось создать marker-файл рабочей области.",
                )

            KsenaxTextFileWriteResult.Success(
                location = toTextFileLocation(
                    fileName = KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME,
                    documentUri = markerUri,
                )
            )
        } catch (exception: Exception) {
            KsenaxTextFileWriteResult.Failure(
                message = exception.message
                    ?: "Не удалось инициализировать выбранную пользователем директорию.",
            )
        }
    }

    /**
     * Нормализует имя файла для чтения из выбранной SAF-директории.
     *
     * Чтение требует явного поддерживаемого расширения, чтобы `readText("note")`
     * не угадывал между `note.md` и `note.txt`.
     *
     * По смыслу отвечает на вопрос: «Можно ли безопасно читать файл с таким именем?»
     *
     * @return безопасное имя файла или `null`, если имя небезопасно либо формат
     * не поддерживается.
     * @since 0.2
     */
    private fun normalizeExistingFileName(fileName: String): String? {
        val normalizedFileName = fileName.trim()

        return if (
            normalizedFileName.isSafeSingleFileName() &&
            KsenaxTextFileFormat.resolveFileName(normalizedFileName) is FileNameFormatResolution.Supported
        ) {
            normalizedFileName
        } else {
            null
        }
    }

    /**
     * Нормализует имя файла для записи в выбранную SAF-директорию.
     *
     * Если расширения нет, добавляется каноническое расширение из [format].
     * Если расширение есть, оно должно совпадать с [format]. Неподдерживаемые
     * расширения получают отказ, чтобы `note.pdf` не превратился в `note.pdf.md`.
     *
     * По смыслу отвечает на вопрос: «какое итоговое безопасное имя файла использовать для записи?»
     *
     * @return итоговое безопасное имя файла или `null`, если имя небезопасно либо
     * расширение конфликтует с запрошенным форматом.
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
     * Гарантирует один `there.ksenaxzone` в выбранной SAF-директории.
     *
     * Существующему marker-у метод дописывает текущую директорию, сохраняя
     * остальной текст. Legacy-документы `there.ksenaxzone*.txt`, которые создал
     * provider для старого MIME type, мигрируют в один marker с точным именем.
     *
     * @return URI marker-файла или `null`, если SAF-провайдер не дал завершить
     * инициализацию.
     * @since 0.2
     */
    private fun ensureWorkspaceZoneDocument(): Uri? =
        synchronized(KsenaxTextFileResolver.workspaceZoneInitializationLock) {
            val markerDocuments = findSafWorkspaceZoneDocuments()
            val canonicalMarker = markerDocuments.firstOrNull { document ->
                document.displayName == KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME
            }

            canonicalMarker?.let { marker ->
                val existingText = readTextFromSafDocument(marker.uri)
                    ?: return@synchronized null
                val updatedText = KsenaxTextFileResolver.ensureWorkspaceDirectoryEntry(
                    existingText = existingText,
                    destinationDescription = destinationDescription,
                )
                if (
                    updatedText != existingText &&
                    !writeTextToSafDocument(marker.uri, updatedText)
                ) {
                    return@synchronized null
                }

                val duplicateDocuments = markerDocuments.filterNot { it.uri == marker.uri }
                val duplicatesDeleted = duplicateDocuments
                    .map { deleteSafDocument(it.uri) }
                    .all { it }
                if (!duplicatesDeleted) {
                    return@synchronized null
                }
                return@synchronized marker.uri
            }

            val legacyText = markerDocuments.firstOrNull()?.let { legacyMarker ->
                readTextFromSafDocument(legacyMarker.uri)
                    ?: return@synchronized null
            }
            val markerText = if (legacyText == null) {
                KsenaxTextFileResolver.workspaceZoneDocumentText(
                    destinationDescription
                )
            } else {
                KsenaxTextFileResolver.ensureWorkspaceDirectoryEntry(
                    existingText = legacyText,
                    destinationDescription = destinationDescription,
                )
            }
            val markerUri = createSafDocument(
                fileName = KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME,
                mimeType = KsenaxTextFileResolver.WORKSPACE_ZONE_MIME_TYPE,
            ) ?: return@synchronized null

            if (readSafDocumentDisplayName(markerUri) !=
                KsenaxTextFileResolver.WORKSPACE_ZONE_FILE_NAME
            ) {
                deleteSafDocument(markerUri)
                return@synchronized null
            }
            if (!writeTextToSafDocument(markerUri, markerText)) {
                deleteSafDocument(markerUri)
                return@synchronized null
            }

            val legacyDocumentsDeleted = markerDocuments
                .map { deleteSafDocument(it.uri) }
                .all { it }
            if (!legacyDocumentsDeleted) {
                return@synchronized null
            }

            markerUri
        }

    /**
     * Ищет прямого ребёнка выбранной SAF-директории по display name.
     *
     * SAF не даёт стабильный filesystem path. Поэтому поиск идёт через
     * `DocumentsContract` и список дочерних документов текущего tree URI.
     *
     * @since 0.2
     */
    private fun findSafDocumentUri(fileName: String): Uri? {
        return findSafDocumentUris(fileName).firstOrNull()
    }

    /**
     * Возвращает всех прямых SAF-потомков с указанным display name.
     *
     * Список нужен marker-инициализации, потому что некоторые
     * DocumentsProvider-ы допускают несколько документов с одинаковым именем.
     *
     * @since 0.2
     */
    private fun findSafDocumentUris(fileName: String): List<Uri> {
        val resolver = appContext.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            workspaceTreeUri,
            DocumentsContract.getTreeDocumentId(workspaceTreeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val matches = mutableListOf<Uri>()

        resolver.query(
            childrenUri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )

            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == fileName) {
                    matches += DocumentsContract.buildDocumentUriUsingTree(
                        workspaceTreeUri,
                        cursor.getString(idIndex),
                    )
                }
            }
        }

        return matches
    }

    /**
     * Возвращает канонический SAF marker и legacy-дубликаты с `.txt`.
     *
     * Имена фильтруются строго через
     * [KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName], поэтому cleanup
     * не затрагивает другие документы выбранной пользователем директории.
     *
     * @since 0.2
     */
    private fun findSafWorkspaceZoneDocuments(): List<SafWorkspaceZoneDocument> {
        val resolver = appContext.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            workspaceTreeUri,
            DocumentsContract.getTreeDocumentId(workspaceTreeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val matches = mutableListOf<SafWorkspaceZoneDocument>()

        resolver.query(
            childrenUri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)
                if (KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(displayName)) {
                    matches += SafWorkspaceZoneDocument(
                        uri = DocumentsContract.buildDocumentUriUsingTree(
                            workspaceTreeUri,
                            cursor.getString(idIndex),
                        ),
                        displayName = displayName,
                    )
                }
            }
        }

        return matches
    }

    /**
     * Читает фактическое display name документа после создания provider-ом.
     *
     * SAF имеет право изменить предложенное имя, поэтому marker нельзя считать
     * каноническим только по входному аргументу [createSafDocument].
     *
     * @since 0.2
     */
    private fun readSafDocumentDisplayName(documentUri: Uri): String? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        return appContext.contentResolver.query(
            documentUri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                )
            }
        }
    }


    /*
    * STORAGE ACCESS FRAMEWORK CRUD-OPERATIONS
    * */

    /**
     * Создаёт новый документ внутри выбранной SAF-директории.
     *
     * Вызывающий код должен заранее проверить, что документа с таким именем нет:
     * разные DocumentsProvider-ы могут по-своему обрабатывать дубликаты.
     *
     * @since 0.2
     */
    private fun createSafDocument(
        fileName: String,
        mimeType: String,
    ): Uri? {
        return DocumentsContract.createDocument(
            appContext.contentResolver,
            rootDocumentUri(),
            mimeType,
            fileName,
        )
    }

    /**
     * Переименовывает SAF-документ и возвращает новый Uri provider-а.
     *
     * Некоторые DocumentsProvider-ы меняют document URI при rename, поэтому
     * вызывающий код должен продолжать работу именно с возвращённым значением.
     *
     * @since 0.2
     */
    private fun renameSafDocument(
        documentUri: Uri,
        newName:     String,
    ): Uri? {
        return DocumentsContract.renameDocument(
            appContext.contentResolver,
            documentUri,
            newName,
        )
    }

    /**
     * Удаляет SAF-документ в cleanup-сценариях.
     *
     * Ошибка удаления не должна скрывать исходную ошибку записи или rename, так
     * что метод намеренно проглатывает исключение provider-а.
     *
     * @since 0.2
     */
    private fun deleteSafDocument(documentUri: Uri): Boolean {
        return runCatching {
            DocumentsContract.deleteDocument(
                appContext.contentResolver,
                documentUri,
            )
        }.getOrDefault(false)
    }

    private data class SafWorkspaceZoneDocument(
        val uri: Uri,
        val displayName: String,
    )

    /**
     * Читает UTF-8 текст служебного SAF-документа.
     *
     * @since 0.2
     */
    private fun readTextFromSafDocument(documentUri: Uri): String? {
        return appContext.contentResolver
            .openInputStream(documentUri)
            ?.use { inputStream -> inputStream.readBufferedUtf8Text() }
    }

    /**
     * Перезаписывает текст в SAF-документе через `content://Uri`.
     *
     * Режим `wt` просит provider открыть документ на запись с усечением старого
     * содержимого. Если provider не даёт output stream, метод возвращает `false`.
     *
     * @since 0.2
     */
    private fun writeTextToSafDocument(
        documentUri: Uri,
        text:        String,
    ): Boolean {
        appContext.contentResolver.openOutputStream(documentUri, "wt")?.use { outputStream ->
            outputStream.write(text.toByteArray(Charsets.UTF_8))
            return true
        }

        return false
    }

    /**
     * Создаёт имя temporary-документа рядом с исходным SAF-документом.
     *
     * @since 0.2
     */
    private fun String.toTemporarySiblingFileName(): String {
        return "$this.ksenax-tmp-${System.currentTimeMillis()}-${System.nanoTime()}"
    }

    /**
     * Создаёт имя backup-документа рядом с исходным SAF-документом.
     *
     * @since 0.2
     */
    private fun String.toBackupSiblingFileName(): String {
        return "$this.ksenax-backup-${System.currentTimeMillis()}-${System.nanoTime()}"
    }

    /**
     * Возвращает URI корневого документа выбранного tree URI.
     *
     * Этот URI нужен для создания новых документов через [DocumentsContract].
     * Он не является файловым путём и должен использоваться только как Android
     * capability внутри ContentResolver/Saf API.
     *
     * @since 0.2
     */
    private fun rootDocumentUri(): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(
            workspaceTreeUri,
            DocumentsContract.getTreeDocumentId(workspaceTreeUri),
        )
    }

    /**
     * Преобразует SAF-документ в общий DTO местоположения.
     *
     * [KsenaxTextFileLocation.displayPath] здесь остаётся человекочитаемым
     * описанием, а реальной capability является [KsenaxTextFileLocation.uri].
     *
     * @since 0.2
     */
    private fun toTextFileLocation(
        fileName:    String,
        documentUri: Uri,
    ): KsenaxTextFileLocation {
        return KsenaxTextFileLocation(
            displayPath = "$destinationDescription/$fileName",
            storageKind = KsenaxTextFileStorageKind.SAF,
            uri         = documentUri.toString(),
        )
    }

    companion object {
        /**
         * Создаёт intent для выбора рабочей директории через SAF.
         *
         * UI-слой должен запустить этот intent, получить `treeUri`, вызвать
         * [takePersistableReadWritePermission], сохранить URI и уже после этого
         * создать [UserSelectedTextWorkspaceStorage].
         *
         * @since 0.2
         */
        fun buildDirectoryPickerIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
        }

        /**
         * Фиксирует долгоживущее read/write-разрешение на выбранную SAF-папку.
         *
         * [resultFlags] нужно передавать из результата ActivityResult/API выбора
         * директории. Метод возвращает `false`, если Android не выдал нужные флаги
         * или permission нельзя сохранить.
         *
         * @since 0.2
         */
        fun takePersistableReadWritePermission(
            context:     Context,
            treeUri:     Uri,
            resultFlags: Int,
        ): Boolean {
            val persistableFlags = resultFlags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

            if (persistableFlags == 0) {
                return false
            }

            return try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    persistableFlags,
                )
                true
            } catch (exception: SecurityException) {
                false
            }
        }
    }
}
