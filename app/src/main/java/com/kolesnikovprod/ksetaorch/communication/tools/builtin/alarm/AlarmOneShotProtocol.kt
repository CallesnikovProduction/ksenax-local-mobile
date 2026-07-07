package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

object AlarmOneShotProtocol : KsenaxOneShotToolProtocol {

    override val declarations: List<KsenaxOneShotDeclaration> =
        listOf(
            AlarmToolOneShot.AtTime,
            AlarmToolOneShot.AfterHours,
            AlarmToolOneShot.AfterMinutes,
            AlarmToolOneShot.AtDateTime,
            AlarmToolOneShot.ClearAll,
        )

    override fun parseOneShotResponse(rawResponse: String): KsenaxToolCall {
        val functionCall = parseFunctionCall(rawResponse)
        require(functionCall.name in SUPPORTED_TOOL_NAMES) {
            "FunctionGemma returned unsupported alarm function: ${functionCall.name}."
        }

        return KsenaxToolCall(
            id = "call_1",
            name = functionCall.name,
            arguments = KsenaxToolArgumentsObject { functionCall.argumentsJson },
            requiresConfirmation = false,
            riskLevel = KsenaxToolRiskLevel.MEDIUM,
        )
    }

    private fun parseFunctionCall(rawResponse: String): ParsedFunctionCall {
        val normalized = rawResponse.trim()
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

        val toolName = callBody.substring(0, argumentsStart).trim()
        require(toolName.isValidFunctionName()) {
            "FunctionGemma returned invalid alarm function name: $toolName."
        }

        val argumentsText = callBody.substring(argumentsStart).trim()
        require(argumentsText.startsWith("{") && argumentsText.endsWith("}")) {
            "FunctionGemma alarm arguments must be an object."
        }

        return ParsedFunctionCall(
            name = toolName,
            argumentsJson = argumentsText.toCanonicalJsonObject(),
        )
    }

    private fun String.toCanonicalJsonObject(): String {
        if (looksLikeStrictJsonObject()) {
            return this
        }

        val body = removePrefix("{").removeSuffix("}").trim()
        if (body.isEmpty()) return "{}"

        val pairs = body.splitTopLevelArguments().map { argument ->
            val separator = argument.indexOf(':')
            require(separator > 0) {
                "FunctionGemma alarm argument must use key:value format."
            }
            val key = argument.substring(0, separator).trim().trim('"')
            val value = argument.substring(separator + 1).trim().decodeFunctionGemmaValue()
            require(key.isNotBlank()) {
                "FunctionGemma alarm argument key must not be blank."
            }
            key.toJsonString() + ":" + value.toJsonScalarString()
        }
        return pairs.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        )
    }

    private fun String.looksLikeStrictJsonObject(): Boolean {
        val trimmed = trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        val body = trimmed.removePrefix("{").removeSuffix("}").trim()
        if (body.isEmpty()) return true
        return body.first() == '"'
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

        val tail = current.toString().trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }

    private fun String.decodeFunctionGemmaValue(): String =
        trim()
            .removeSurrounding(ESCAPE_TOKEN)
            .removeSurrounding("\"")
            .trim()

    private fun String.toJsonScalarString(): String =
        when {
            toIntOrNull() != null -> this
            toDoubleOrNull() != null -> this
            else -> toJsonString()
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

    private fun String.isValidFunctionName(): Boolean {
        if (isEmpty()) return false
        if (!first().isAsciiLetter()) return false
        return all { symbol -> symbol.isAsciiLetterOrDigit() || symbol == '_' }
    }

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
