package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotPromptInput
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import org.json.JSONObject

/**
 * Самодостаточный FunctionGemma-протокол фонарика.
 *
 * Объект не требует старого registry/router слоя: он объявляет короткие FG
 * functions и парсит ответ вида `<start_function_call>...`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
object TorchOneShotProtocol : KsenaxOneShotToolProtocol {

    override val declarations: List<KsenaxOneShotDeclaration> =
        listOf(
            TorchToolOneShot.On,
            TorchToolOneShot.Off,
            TorchToolOneShot.Toggle,
        )

    fun buildPrompt(
        userMessage: String,
        declaration: KsenaxOneShotDeclaration,
    ): String =
        buildPrompt(
            userMessage = userMessage,
            declarations = listOf(declaration),
        )

    override fun parseOneShotResponse(rawResponse: String): KsenaxToolCall {
        val functionCall = parseFunctionCall(rawResponse)
        val toolName = functionCall.name
        require(toolName in SUPPORTED_TOOL_NAMES) {
            "FunctionGemma returned unsupported torch function: $toolName."
        }

        val argumentsJson = functionCall.argumentsJson
        val arguments = try {
            JSONObject(argumentsJson)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "FunctionGemma returned invalid torch arguments JSON.",
                error,
            )
        }
        require(arguments.length() == 0) {
            "One-shot torch function must return an empty arguments object."
        }

        return KsenaxToolCall(
            id = "call_1",
            name = toolName,
            arguments = KsenaxRawToolArgumentsObject(argumentsJson),
            requiresConfirmation = false,
            riskLevel = KsenaxToolRiskLevel.LOW,
        )
    }

    private fun buildPrompt(
        userMessage: String,
        declarations: List<KsenaxOneShotDeclaration>,
    ): String =
        promptParser.parseLike(
            declarations = declarations,
            input = KsenaxOneShotPromptInput(userMessage = userMessage),
        )

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
        require(toolName.isValidOneShotToolName()) {
            "FunctionGemma returned invalid function name: $toolName."
        }

        val argumentsJson = callBody.substring(argumentsStart).trim()
        require(argumentsJson.startsWith("{") && argumentsJson.endsWith("}")) {
            "FunctionGemma response arguments must be a JSON object."
        }

        return ParsedFunctionCall(
            name = toolName,
            argumentsJson = argumentsJson,
        )
    }

    private fun String.isValidOneShotToolName(): Boolean {
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
        setOf(
            TorchToolOneShot.On.codeName,
            TorchToolOneShot.Off.codeName,
            TorchToolOneShot.Toggle.codeName,
        )

    private const val START_FUNCTION_CALL = "<start_function_call>"
    private const val END_FUNCTION_CALL = "<end_function_call>"
    private const val CALL_PREFIX = "call:"
}
