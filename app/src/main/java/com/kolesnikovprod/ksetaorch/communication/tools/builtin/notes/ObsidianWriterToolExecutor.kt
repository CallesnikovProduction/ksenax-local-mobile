package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteAction
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteNaming.DEFAULT_NOTE_TITLE
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteNaming.buildNoteFileName
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteNaming.formatBlockTime
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteNaming.toSafeNoteTitle
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteProperties.buildObsidianPropertiesBlock
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteProperties.buildMarkdownDocument
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils.ObsidianNoteProperties.updateObsidianProperties
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileReadResult
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileWriteResult
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Исполнитель инструмента для Obsidian-совместимых markdown-заметок.
 *
 * Класс получает [KsenaxToolCall], читает `title` и `markdown_body` из
 * `arguments`, собирает markdown-блок и передаёт запись в
 * [KsenaxTextFileResolver]. Если файл с такой датой и заголовком уже есть,
 * исполнитель дописывает новый блок в конец файла.
 *
 * Resolver создаётся лениво: отсутствие или потеря SAF workspace не должно
 * блокировать MODEL VERIFICATION и actions, которым файловая система не нужна.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class ObsidianWriterToolExecutor(
    private val fileResolverProvider: () -> KsenaxTextFileResolver,
) : KsenaxToolExecutor {

    constructor(fileResolver: KsenaxTextFileResolver) : this({ fileResolver })

    private val fileResolver: KsenaxTextFileResolver by lazy(
        mode = LazyThreadSafetyMode.SYNCHRONIZED,
        initializer = fileResolverProvider,
    )

    override suspend fun execute(
        call: KsenaxToolCall,
    ): KsenaxToolResult = withContext(Dispatchers.IO) {
        if (call.name != CREATE_OR_EDIT_MARKDOWN_NOTE) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "ObsidianWriterToolExecutor cannot execute tool: ${call.name}.",
                errorCode = "INVALID_TOOL",
            )
        }

        val arguments = parseArguments(call.arguments.JSONtoString())
            ?: return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Arguments for $CREATE_OR_EDIT_MARKDOWN_NOTE must be a JSON object.",
                errorCode = "INVALID_ARGUMENTS",
            )

        val rawTitle = arguments.optString("title").takeIf { title -> title.isNotBlank() }
            ?: DEFAULT_NOTE_TITLE

        val markdownBody = arguments.optString("markdown_body").trim()

        if (markdownBody.isBlank()) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Нельзя сохранить пустую markdown-заметку.",
                errorCode = "EMPTY_NOTE_ERROR",
            )
        }

        saveMarkdownNote(
            call         = call,
            createdAt    = ZonedDateTime.now(),
            title        = rawTitle,
            markdownBody = markdownBody,
        )
    }

    /**
     * Возвращает человекочитаемое описание места, куда сохраняются заметки.
     *
     * Интерфейс может показать эту строку пользователю перед включением
     * Obsidian-инструмента или после успешного сохранения.
     *
     * @since 0.2
     */
    fun getNotesDestinationDescription(): String =
        fileResolver.destinationDescription

    /**
     * Превращает JSON-строку arguments в [JSONObject].
     *
     * Метод не валидирует доменные поля. Он проверяет только, что arguments
     * можно прочитать как JSON-объект. Поля `title` и `markdown_body`
     * проверяются дальше в [execute].
     *
     * @since 0.2
     */
    private fun parseArguments(arguments: String): JSONObject? =
        try {
            JSONObject(arguments)
        } catch (_: JSONException) {
            null
        }

    /**
     * Сохраняет markdown-заметку или дописывает новый блок в существующий файл.
     *
     * Имя файла строится из даты и безопасного заголовка. Если файловый слой
     * находит файл с таким именем, исполнитель не перезаписывает его, а добавляет новый
     * раздел через горизонтальную черту.
     *
     * @since 0.2
     */
    private suspend fun saveMarkdownNote(
        call:         KsenaxToolCall,
        createdAt:    ZonedDateTime,
        title:        String,
        markdownBody: String,
    ): KsenaxToolResult {
        val safeTitle = title.toSafeNoteTitle()
        val fileName = buildNoteFileName(createdAt = createdAt, safeTitle = safeTitle)
        val newFileMarkdownBlock = buildMarkdownBlock(
            title                = safeTitle,
            markdownBody         = markdownBody,
            createdAt            = createdAt,
            includeDocumentTitle = true,
        )
        val appendMarkdownBlock = buildMarkdownBlock(
            title                = safeTitle,
            markdownBody         = markdownBody,
            createdAt            = createdAt,
            includeDocumentTitle = false,
        )

        return try {
            val existingText = when (val readResult = fileResolver.readText(fileName)) {
                is KsenaxTextFileReadResult.Success  -> readResult.text
                is KsenaxTextFileReadResult.NotFound -> null
                is KsenaxTextFileReadResult.Failure  -> return KsenaxToolResult.Failure(
                    callId    = call.id,
                    toolName  = call.name,
                    reason    = readResult.message,
                    errorCode = "NOTE_READ_FAILED",
                )
            }
            val shouldAppend = !existingText.isNullOrBlank()
            val action = if (shouldAppend) {
                ObsidianNoteAction.EDITED
            } else {
                ObsidianNoteAction.CREATED
            }
            val finalText = buildFinalMarkdownText(
                existingText     = existingText,
                newMarkdownBlock = if (shouldAppend) appendMarkdownBlock else newFileMarkdownBlock,
                performedAt      = createdAt,
            )
            val writeResult = fileResolver.writeText(
                fileName = fileName,
                text     = finalText,
                format   = KsenaxTextFileFormat.MARKDOWN,
            )

            buildResultFromWriteResult(
                call         = call,
                writeResult  = writeResult,
                safeTitle    = safeTitle,
                shouldAppend = shouldAppend,
                action       = action,
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = error.message ?: "Не удалось сохранить Obsidian markdown-заметку.",
                errorCode = "NOTE_WRITE_EXCEPTION",
            )
        }
    }

    /**
     * Превращает результат записи файла в результат выполнения инструмента.
     *
     * Успешный ответ содержит путь, заголовок, тип хранилища и флаг дописывания
     * в `payloadJson`. Ошибка файлового слоя возвращается как сбой инструмента.
     *
     * @since 0.2
     */
    private fun buildResultFromWriteResult(
        call:         KsenaxToolCall,
        writeResult:  KsenaxTextFileWriteResult,
        safeTitle:    String,
        shouldAppend: Boolean,
        action:       ObsidianNoteAction,
    ): KsenaxToolResult =
        when (writeResult) {
            is KsenaxTextFileWriteResult.Success -> KsenaxToolResult.Success(
                callId   = call.id,
                toolName = call.name,
                message  = if (shouldAppend) {
                    "Заметка дописана в существующий markdown-файл."
                } else {
                    "Заметка сохранена в новый markdown-файл."
                },

                // Создаём пустой JSON: {}, а затем заполняем
                payloadJson = JSONObject()
                    .put("note_path", writeResult.displayPath)
                    .put("note_title", safeTitle)
                    .put("storage", writeResult.storageKind.name)
                    .put("was_appended", shouldAppend)
                    .put("action", action.propertyValue)
                    .also { payload ->
                        writeResult.uri?.let { uri -> payload.put("note_uri", uri) }
                    }
                    .toString(),
            )

            is KsenaxTextFileWriteResult.Failure -> KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = writeResult.message,
                errorCode = "NOTE_WRITE_FAILED",
            )
        }

    /**
     * Собирает финальный текст файла.
     *
     * Для нового файла возвращает свежий markdown-блок. Для существующего файла
     * добавляет разделитель и новый блок в конец.
     *
     * @since 0.2
     */
    private fun buildFinalMarkdownText(
        existingText:     String?,
        newMarkdownBlock: String,
        performedAt:      ZonedDateTime,
    ): String =
        if (existingText.isNullOrBlank()) {
            buildMarkdownDocument(
                propertiesBlock  = buildObsidianPropertiesBlock(
                    createdAt   = performedAt,
                    action      = ObsidianNoteAction.CREATED,
                    performedAt = performedAt,
                ),
                markdownContent  = newMarkdownBlock,
            )
        } else {
            val existingTextWithUpdatedProperties = updateObsidianProperties(
                markdown    = existingText,
                performedAt = performedAt,
            )
            "${existingTextWithUpdatedProperties.trimEnd()}\n\n---\n\n$newMarkdownBlock"
        }

    /**
     * Собирает markdown-блок одной записи.
     *
     * Первый блок в новом файле получает заголовок документа. Последующие блоки
     * получают только время и тело заметки.
     *
     * @since 0.2
     */
    private fun buildMarkdownBlock(
        title:                String,
        markdownBody:         String,
        createdAt:            ZonedDateTime,
        includeDocumentTitle: Boolean,
    ): String = buildString {
        if (includeDocumentTitle) {
            appendLine("# $title")
            appendLine()
        }

        appendLine("## ${formatBlockTime(createdAt)}")
        appendLine()
        appendLine(markdownBody)
        appendLine()
    }

    /**
     * Метаданные Obsidian-инструмента для модели и реестра.
     *
     * Здесь лежат имя инструмента и JSON-схема `arguments`, которую маршрутизатор
     * показывает локальной модели.
     *
     * @since 0.2
     */
    companion object {
        /**
         * Имя инструмента, которое модель должна вернуть в `tool_calls[0].name`.
         *
         * @since 0.2
         */
        const val CREATE_OR_EDIT_MARKDOWN_NOTE = "create_or_edit_markdown_note"

    }
}
