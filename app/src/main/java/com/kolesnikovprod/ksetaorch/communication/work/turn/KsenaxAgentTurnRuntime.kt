package com.kolesnikovprod.ksetaorch.communication.work.turn

import com.kolesnikovprod.ksetaorch.communication.tools.policy.KsenaxPolicyContext

/**
 * UI-facing runtime одного agentic-чата.
 *
 * Agentic-режим не является обычной перепиской с моделью: каждый пользовательский
 * запрос обрабатывается как отдельный fresh turn без наследования прошлого
 * LiteRT conversation state.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxAgentTurnRuntime {

    suspend fun prepare()

    suspend fun handleUserText(
        text: String,
        policyContext: KsenaxPolicyContext = KsenaxPolicyContext(),
        onStage: suspend (KsenaxAgentTurnStage) -> Unit = {},
    ): KsenaxAgentTurnResult
}
