package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import org.json.JSONObject

/**
 * Adapter между маленькими FG note-functions и существующим writer executor.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class ObsidianNoteOneShotExecutor(
    private val writerExecutor: ObsidianWriterToolExecutor,
) : KsenaxToolExecutor {

    override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult {
        val legacyArguments = runCatching {
            translate(call)
        }.getOrElse { error ->
            return KsenaxToolResult.Failure(
                callId = call.id,
                toolName = call.name,
                reason = error.message ?: "Неверные one-shot аргументы заметки.",
                errorCode = "INVALID_ARGUMENTS",
            )
        }

        val legacyCall = call.copy(
            name = ObsidianWriterToolExecutor.CREATE_OR_EDIT_MARKDOWN_NOTE,
            arguments = KsenaxRawToolArgumentsObject(legacyArguments.toString()),
        )

        return writerExecutor.execute(legacyCall).withToolName(call.name)
    }

    private fun translate(call: KsenaxToolCall): JSONObject {
        val input = JSONObject(call.arguments.JSONtoString())
        val title = input.requiredString("title")
        val body = when (call.name) {
            ObsidianNoteOneShot.Write.codeName ->
                input.requiredString("markdown_body")
            ObsidianNoteOneShot.AppendAnalysis.codeName ->
                "## Анализ\n\n" + input.requiredString("analysis_markdown")
            else -> error("ObsidianNoteOneShotExecutor cannot execute tool: ${call.name}.")
        }
        return JSONObject()
            .put("title", title)
            .put("markdown_body", body)
    }

    private fun JSONObject.requiredString(name: String): String =
        optString(name).trim().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Argument `$name` must be a non-empty string.")

    private fun KsenaxToolResult.withToolName(toolName: String): KsenaxToolResult =
        when (this) {
            is KsenaxToolResult.Success -> copy(toolName = toolName)
            is KsenaxToolResult.Failure -> copy(toolName = toolName)
        }
}
