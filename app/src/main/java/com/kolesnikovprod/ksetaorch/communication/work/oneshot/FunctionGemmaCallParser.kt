package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Разбирает единичный вызов функции из ответа FunctionGemma.
 *
 * Некавыченные ключи и `<escape>`-строки преобразуются в корректный JSON.
 * Несколько вызовов в одном ответе отклоняются.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal object FunctionGemmaCallParser {

    fun parse(rawResponse: String): FunctionGemmaCall {
        val start = rawResponse.indexOf(START_FUNCTION_CALL)
        require(start >= 0) {
            "FunctionGemma response does not contain a function call."
        }
        require(rawResponse.indexOf(START_FUNCTION_CALL, start + START_FUNCTION_CALL.length) < 0) {
            "FunctionGemma one-shot response must contain exactly one function call."
        }

        val bodyStart = start + START_FUNCTION_CALL.length
        val end = rawResponse.indexOf(END_FUNCTION_CALL, bodyStart)
        require(end >= 0) {
            "FunctionGemma response does not contain a complete function call."
        }
        require(rawResponse.indexOf(END_FUNCTION_CALL, end + END_FUNCTION_CALL.length) < 0) {
            "FunctionGemma one-shot response must contain exactly one function call."
        }

        val body = rawResponse.substring(bodyStart, end).trim()
        require(body.startsWith(CALL_PREFIX)) {
            "FunctionGemma response does not contain a function call."
        }

        val callBody = body.removePrefix(CALL_PREFIX).trim()
        val argumentsStart = callBody.indexOf('{')
        require(argumentsStart > 0 && callBody.endsWith('}')) {
            "FunctionGemma response arguments must be a JSON object."
        }

        val name = callBody.substring(0, argumentsStart).trim()
        require(name.isValidFunctionName()) {
            "FunctionGemma returned invalid function name: $name."
        }

        return FunctionGemmaCall(
            name = name,
            argumentsJson = callBody.substring(argumentsStart).toCanonicalJsonObject(),
        )
    }

    private fun String.toCanonicalJsonObject(): String {
        val trimmed = trim()
        require(trimmed.startsWith('{') && trimmed.endsWith('}')) {
            "FunctionGemma response arguments must be a JSON object."
        }
        val body = trimmed.substring(1, trimmed.length - 1).trim()
        if (body.isEmpty()) return "{}"

        return body.splitTopLevelArguments()
            .map { argument ->
                val separator = argument.indexOfTopLevel(':')
                require(separator > 0) {
                    "FunctionGemma argument must use key:value format."
                }
                val key = argument.substring(0, separator).trim().removeSurrounding("\"")
                require(key.isNotBlank()) {
                    "FunctionGemma argument key must not be blank."
                }
                val value = argument.substring(separator + 1).trim()
                key.toJsonString() + ":" + value.toCanonicalJsonValue()
            }
            .joinToString(separator = ",", prefix = "{", postfix = "}")
    }

    private fun String.splitTopLevelArguments(): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideEscape = false
        var insideQuotes = false
        var escaped = false
        var nestedDepth = 0
        var index = 0

        while (index < length) {
            if (!insideQuotes && startsWith(ESCAPE_TOKEN, index)) {
                insideEscape = !insideEscape
                current.append(ESCAPE_TOKEN)
                index += ESCAPE_TOKEN.length
                continue
            }

            val symbol = this[index]
            when {
                escaped -> escaped = false
                insideQuotes && symbol == '\\' -> escaped = true
                !insideEscape && symbol == '"' -> insideQuotes = !insideQuotes
                !insideEscape && !insideQuotes && symbol in "[{" -> nestedDepth += 1
                !insideEscape && !insideQuotes && symbol in "]}" -> nestedDepth -= 1
                !insideEscape && !insideQuotes && nestedDepth == 0 && symbol == ',' -> {
                    result += current.toString().trim()
                    current.clear()
                    index += 1
                    continue
                }
            }
            current.append(symbol)
            index += 1
        }

        require(!insideEscape && !insideQuotes && nestedDepth == 0) {
            "FunctionGemma arguments contain an unterminated value."
        }
        current.toString().trim().takeIf(String::isNotEmpty)?.let(result::add)
        return result
    }

    private fun String.indexOfTopLevel(target: Char): Int {
        var insideEscape = false
        var insideQuotes = false
        var escaped = false
        var nestedDepth = 0
        var index = 0
        while (index < length) {
            if (!insideQuotes && startsWith(ESCAPE_TOKEN, index)) {
                insideEscape = !insideEscape
                index += ESCAPE_TOKEN.length
                continue
            }
            val symbol = this[index]
            when {
                escaped -> escaped = false
                insideQuotes && symbol == '\\' -> escaped = true
                !insideEscape && symbol == '"' -> insideQuotes = !insideQuotes
                !insideEscape && !insideQuotes && symbol in "[{" -> nestedDepth += 1
                !insideEscape && !insideQuotes && symbol in "]}" -> nestedDepth -= 1
                !insideEscape && !insideQuotes && nestedDepth == 0 && symbol == target -> return index
            }
            index += 1
        }
        return -1
    }

    private fun String.toCanonicalJsonValue(): String {
        val value = trim()
        if (value.startsWith(ESCAPE_TOKEN) && value.endsWith(ESCAPE_TOKEN)) {
            return value
                .removePrefix(ESCAPE_TOKEN)
                .removeSuffix(ESCAPE_TOKEN)
                .toJsonString()
        }
        if (value.startsWith('"') && value.endsWith('"')) return value
        if (value.equals("true", ignoreCase = true)) return "true"
        if (value.equals("false", ignoreCase = true)) return "false"
        if (value.equals("null", ignoreCase = true)) return "null"
        if (value.toLongOrNull() != null || value.toDoubleOrNull() != null) return value
        if ((value.startsWith('{') && value.endsWith('}')) ||
            (value.startsWith('[') && value.endsWith(']'))
        ) {
            return value
        }
        return value.toJsonString()
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

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || this in '0'..'9'

    private const val START_FUNCTION_CALL = "<start_function_call>"
    private const val END_FUNCTION_CALL = "<end_function_call>"
    private const val CALL_PREFIX = "call:"
    private const val ESCAPE_TOKEN = "<escape>"
}

/**
 * Разобранный ответ FunctionGemma.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal data class FunctionGemmaCall(
    val name: String,
    val argumentsJson: String,
)
