package com.kolesnikovprod.ksetaorch.communication.work.oneshot

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall

/**
 * Полный one-shot протокол одного FunctionGemma action kit-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxOneShotToolProtocol {

    val declarations: List<KsenaxOneShotDeclaration>

    val promptParser: KsenaxOneShotPromptParser
        get() = KsenaxFunctionGemmaPromptParser

    fun buildOneShotPrompt(input: KsenaxOneShotPromptInput): String =
        promptParser.parseLike(
            declarations = declarations,
            input = input,
        )

    fun buildOneShotPrompt(userMessage: String): String =
        buildOneShotPrompt(KsenaxOneShotPromptInput(userMessage = userMessage))

    fun parseOneShotResponse(rawResponse: String): KsenaxToolCall
}
