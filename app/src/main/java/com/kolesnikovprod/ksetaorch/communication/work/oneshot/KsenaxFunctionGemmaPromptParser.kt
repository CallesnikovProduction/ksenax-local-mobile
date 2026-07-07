package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Общий chat-template для FunctionGemma Mobile Actions.
 *
 * Prompt намеренно держится коротким: declaration block + один user turn.
 * G4 planner может передать компактный JSON input, но не должен раздувать
 * FunctionGemma-запрос рассуждениями.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
object KsenaxFunctionGemmaPromptParser : KsenaxOneShotPromptParser {

    override fun parseLike(
        declarations: List<KsenaxOneShotDeclaration>,
        input: KsenaxOneShotPromptInput,
    ): String {
        require(input.userMessage.isNotBlank()) {
            "One-shot prompt userMessage must not be blank."
        }
        require(declarations.isNotEmpty()) {
            "One-shot prompt must contain at least one declaration."
        }

        return buildString {
            appendLine("<bos><start_of_turn>developer")
            appendLine("You are a model that can do function calling with the following functions")
            declarations.forEach { declaration ->
                appendLine("<start_function_declaration>")
                appendLine("declaration:${declaration.codeName}{")
                appendLine("description:<escape>${declaration.description}<escape>,")
                appendLine("parameters:${declaration.parameters ?: "null"}")
                appendLine("<end_function_declaration>")
            }
            appendLine("<end_of_turn>")
            appendLine()
            appendLine("<start_of_turn>user")
            appendLine("User request:")
            appendLine(input.userMessage)
            input.preferredActionName
                ?.takeIf(String::isNotBlank)
                ?.let { preferredActionName ->
                    appendLine()
                    appendLine("Target function:")
                    appendLine(preferredActionName)
                }
            appendLine()
            appendLine("Action instruction:")
            appendLine(input.stepInstruction)
            input.plannerInputJson
                ?.takeIf(String::isNotBlank)
                ?.let { plannerInputJson ->
                    appendLine()
                    appendLine("Planner input JSON:")
                    appendLine(plannerInputJson)
                }
            appendLine("<end_of_turn>")
            appendLine()
            append("<start_of_turn>model")
        }
    }
}
