package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

object ObsidianNoteOneShotProtocol : KsenaxOneShotToolProtocol {

    override val declarations: List<KsenaxOneShotDeclaration> =
        listOf(
            ObsidianNoteOneShot.Write,
            ObsidianNoteOneShot.AppendAnalysis,
        )

    override fun parseOneShotResponse(rawResponse: String): KsenaxToolCall {
        val call = rawResponse.parseFunctionGemmaCall()
        require(call.name in SUPPORTED_TOOL_NAMES) {
            "FunctionGemma returned unsupported Obsidian note function: ${call.name}."
        }
        return KsenaxToolCall(
            id = "call_1",
            name = call.name,
            arguments = KsenaxToolArgumentsObject { call.argumentsJson },
            requiresConfirmation = false,
            riskLevel = KsenaxToolRiskLevel.MEDIUM,
        )
    }

    private fun String.parseFunctionGemmaCall(): ParsedFunctionCall {
        val normalized = trim()
        require(
            normalized.startsWith(START_FUNCTION_CALL) &&
                normalized.endsWith(END_FUNCTION_CALL)
        ) {
            "FunctionGemma response does not match one-shot function-call format."
        }
        val body = normalized
            .removePrefix(START_FUNCTION_CALL)
            .removeSuffix(END_FUNCTION_CALL)
            .trim()
        require(body.startsWith(CALL_PREFIX)) {
            "FunctionGemma response does not contain a function call."
        }
        val callBody = body.removePrefix(CALL_PREFIX).trim()
        val argumentsStart = callBody.indexOf('{')
        require(argumentsStart > 0) {
            "FunctionGemma response does not contain function arguments."
        }
        val name = callBody.substring(0, argumentsStart).trim()
        require(name.isValidFunctionName()) {
            "FunctionGemma returned invalid note function name: $name."
        }
        val argumentsText = callBody.substring(argumentsStart).trim()
        require(argumentsText.startsWith("{") && argumentsText.endsWith("}")) {
            "FunctionGemma note arguments must be an object."
        }
        return ParsedFunctionCall(
            name = name,
            argumentsJson = argumentsText.toCanonicalJsonObject(),
        )
    }

    private fun String.toCanonicalJsonObject(): String {
        if (looksLikeStrictJsonObject()) return this
        val body = removePrefix("{").removeSuffix("}").trim()
        if (body.isEmpty()) return "{}"
        val pairs = body.splitTopLevelArguments().map { argument ->
            val separator = argument.indexOf(':')
            require(separator > 0) {
                "FunctionGemma note argument must use key:value format."
            }
            val key = argument.substring(0, separator).trim().trim('"')
            val value = argument.substring(separator + 1).trim()
                .removeSurrounding(ESCAPE_TOKEN)
                .removeSurrounding("\"")
                .trim()
            key.toJsonString() + ":" + value.toJsonString()
        }
        return pairs.joinToString(separator = ",", prefix = "{", postfix = "}")
    }

    private fun String.looksLikeStrictJsonObject(): Boolean {
        val trimmed = trim()
        val body = trimmed.removePrefix("{").removeSuffix("}").trim()
        return trimmed.startsWith("{") && trimmed.endsWith("}") &&
            (body.isEmpty() || body.first() == '"')
    }

    private fun String.splitTopLevelArguments(): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideEscape = false
        var insideQuotes = false
        var index = 0
        while (index < length) {
            when {
                startsWith(ESCAPE_TOKEN, index) -> {
                    insideEscape = !insideEscape
                    current.append(ESCAPE_TOKEN)
                    index += ESCAPE_TOKEN.length
                }
                this[index] == '"' && !insideEscape -> {
                    insideQuotes = !insideQuotes
                    current.append(this[index])
                    index++
                }
                this[index] == ',' && !insideEscape && !insideQuotes -> {
                    result += current.toString().trim()
                    current.clear()
                    index++
                }
                else -> {
                    current.append(this[index])
                    index++
                }
            }
        }
        current.toString().trim().takeIf(String::isNotEmpty)?.let(result::add)
        return result
    }

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            this@toJsonString.forEach { symbol ->
                when (symbol) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(symbol)
                }
            }
            append('"')
        }

    private fun String.isValidFunctionName(): Boolean =
        isNotEmpty() &&
            first().isAsciiLetter() &&
            all { symbol -> symbol.isAsciiLetterOrDigit() || symbol == '_' }

    private fun Char.isAsciiLetter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z'

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        isAsciiLetter() || this in '0'..'9'

    private data class ParsedFunctionCall(
        val name: String,
        val argumentsJson: String,
    )

    private val SUPPORTED_TOOL_NAMES: Set<String> =
        declarations.map(KsenaxOneShotDeclaration::codeName).toSet()

    private const val START_FUNCTION_CALL = "<start_function_call>"
    private const val END_FUNCTION_CALL = "<end_function_call>"
    private const val CALL_PREFIX = "call:"
    private const val ESCAPE_TOKEN = "<escape>"
}
