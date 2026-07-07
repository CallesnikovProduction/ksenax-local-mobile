package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

object CalendarEventOneShotProtocol : KsenaxOneShotToolProtocol {

    override val declarations: List<KsenaxOneShotDeclaration> = listOf(CalendarEventOneShot)

    override fun parseOneShotResponse(rawResponse: String): KsenaxToolCall {
        val call = rawResponse.parseFunctionGemmaCall()
        require(call.name == CalendarEventOneShot.codeName) {
            "FunctionGemma returned unsupported calendar function: ${call.name}."
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
        require(normalized.startsWith(START_FUNCTION_CALL) && normalized.endsWith(END_FUNCTION_CALL)) {
            "FunctionGemma response does not match one-shot function-call format."
        }
        val body = normalized.removePrefix(START_FUNCTION_CALL).removeSuffix(END_FUNCTION_CALL).trim()
        require(body.startsWith(CALL_PREFIX)) {
            "FunctionGemma response does not contain a function call."
        }
        val callBody = body.removePrefix(CALL_PREFIX).trim()
        val argumentsStart = callBody.indexOf('{')
        require(argumentsStart > 0) {
            "FunctionGemma response does not contain function arguments."
        }
        val name = callBody.substring(0, argumentsStart).trim()
        val argumentsText = callBody.substring(argumentsStart).trim()
        return ParsedFunctionCall(
            name = name,
            argumentsJson = argumentsText.toCanonicalJsonObject(),
        )
    }

    private fun String.toCanonicalJsonObject(): String {
        if (trim().removePrefix("{").removeSuffix("}").trim().firstOrNull() == '"') {
            return this
        }
        val body = removePrefix("{").removeSuffix("}").trim()
        if (body.isEmpty()) return "{}"
        return body.split(",")
            .map { argument ->
                val separator = argument.indexOf(':')
                require(separator > 0) {
                    "FunctionGemma calendar argument must use key:value format."
                }
                val key = argument.substring(0, separator).trim().trim('"')
                val value = argument.substring(separator + 1).trim()
                    .removeSurrounding(ESCAPE_TOKEN)
                    .removeSurrounding("\"")
                    .trim()
                key.toJsonString() + ":" + value.toJsonScalarString()
            }
            .joinToString(separator = ",", prefix = "{", postfix = "}")
    }

    private fun String.toJsonScalarString(): String =
        when {
            equals("true", ignoreCase = true) -> "true"
            equals("false", ignoreCase = true) -> "false"
            toLongOrNull() != null -> this
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

    private data class ParsedFunctionCall(
        val name: String,
        val argumentsJson: String,
    )

    private const val START_FUNCTION_CALL = "<start_function_call>"
    private const val END_FUNCTION_CALL = "<end_function_call>"
    private const val CALL_PREFIX = "call:"
    private const val ESCAPE_TOKEN = "<escape>"
}
