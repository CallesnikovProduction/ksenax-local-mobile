package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Собирает короткий FunctionGemma prompt для набора declaration-ов.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
fun interface KsenaxOneShotPromptParser {

    fun parseLike(
        declarations: List<KsenaxOneShotDeclaration>,
        input: KsenaxOneShotPromptInput,
    ): String
}
