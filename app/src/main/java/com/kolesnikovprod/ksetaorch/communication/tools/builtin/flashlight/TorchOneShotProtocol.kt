package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotPromptInput
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

/**
 * FunctionGemma-протокол фонарика.
 *
 * Общий синтаксис function-call разбирается в `communication.work.oneshot`;
 * здесь остаются только declarations и инвариант пустых аргументов.
 *
 * @since 0.2
 */
object TorchOneShotProtocol : KsenaxOneShotToolProtocol {

    override val declarations: List<KsenaxOneShotDeclaration> =
        listOf(
            TorchToolOneShot.On,
            TorchToolOneShot.Off,
            TorchToolOneShot.Toggle,
        )

    override val acceptsOnlyEmptyArguments: Boolean = true

    fun buildPrompt(
        userMessage: String,
        declaration: KsenaxOneShotDeclaration,
    ): String =
        promptParser.parseLike(
            declarations = listOf(declaration),
            input = KsenaxOneShotPromptInput(userMessage = userMessage),
        )
}
