package com.kolesnikovprod.ksetaorch.communication.work.oneshot

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionCall
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionDeclaration
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionRequest

/**
 * Полный one-shot протокол одного FunctionGemma action kit-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxOneShotToolProtocol {

    /**
     * Native-вызов одного запланированного действия без ручного chat-template.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun buildFunctionRequest(actionName: String, input: KsenaxOneShotPromptInput): KsenaxModelFunctionRequest {
        val declaration = declarations.single { it.codeName == actionName }
        return KsenaxModelFunctionRequest(
            userMessage = buildString {
                append(input.stepInstruction)
                input.inputJson?.let { append("\nInput: $it") }
            },
            functions = listOf(KsenaxModelFunctionDeclaration(declaration.codeName, declaration.description, declaration.parameters)),
        )
    }

    /**
     * Переводит предложение SDK в проверенный контракт вызова приложения.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun parseFunctionCall(call: KsenaxModelFunctionCall): KsenaxToolCall {
        val declaration = declarations.singleOrNull { it.codeName == call.name }
            ?: throw IllegalArgumentException("Unsupported FunctionGemma function: ${call.name}.")
        ActionArgumentsValidator.validate(declaration, call.argumentsJson)
        return KsenaxToolCall("call_1", call.name, KsenaxRawToolArgumentsObject(call.argumentsJson), requiresConfirmation, riskLevel)
    }

    val declarations: List<KsenaxOneShotDeclaration>

    val promptParser: KsenaxOneShotPromptParser
        get() = KsenaxFunctionGemmaPromptParser

    val riskLevel: KsenaxToolRiskLevel
        get() = KsenaxToolRiskLevel.LOW

    val requiresConfirmation: Boolean
        get() = false

    val acceptsOnlyEmptyArguments: Boolean
        get() = false

    fun buildOneShotPrompt(input: KsenaxOneShotPromptInput): String =
        promptParser.parseLike(
            declarations = declarations,
            input = input,
        )

    fun buildOneShotPrompt(userMessage: String): String =
        buildOneShotPrompt(KsenaxOneShotPromptInput(userMessage = userMessage))

    fun buildOneShotPromptForAction(
        actionName: String,
        input: KsenaxOneShotPromptInput,
    ): String {
        val declaration = declarations.singleOrNull { item -> item.codeName == actionName }
            ?: throw IllegalArgumentException("Unknown FunctionGemma action: $actionName.")
        return promptParser.parseLike(
            declarations = listOf(declaration),
            input = input,
        )
    }

    fun parseOneShotResponse(rawResponse: String): KsenaxToolCall {
        val parsed = FunctionGemmaCallParser.parse(rawResponse)
        require(declarations.any { declaration -> declaration.codeName == parsed.name }) {
            "FunctionGemma returned unsupported function: ${parsed.name}."
        }
        if (acceptsOnlyEmptyArguments) {
            require(parsed.argumentsJson == "{}") {
                "FunctionGemma function `${parsed.name}` must return an empty arguments object."
            }
        }
        return KsenaxToolCall(
            id = "call_1",
            name = parsed.name,
            arguments = KsenaxRawToolArgumentsObject(parsed.argumentsJson),
            requiresConfirmation = requiresConfirmation,
            riskLevel = riskLevel,
        )
    }
}
