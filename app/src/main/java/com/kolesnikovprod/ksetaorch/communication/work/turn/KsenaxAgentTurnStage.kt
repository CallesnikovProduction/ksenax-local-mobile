package com.kolesnikovprod.ksetaorch.communication.work.turn

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall

/**
 * Стадия одного agentic turn-а.
 *
 * Тип остаётся маленьким и UI-friendly: ViewModel может показать прогресс, но
 * не получает доступ к внутреннему G4/FG prompt-у, planner JSON или сырым
 * function-call ответам.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxAgentTurnStage {

    data object RequestReceived : KsenaxAgentTurnStage

    data object Planning : KsenaxAgentTurnStage

    data object CompilingAction : KsenaxAgentTurnStage

    data class ExecutingTools(
        val calls: List<KsenaxToolCall>,
    ) : KsenaxAgentTurnStage
}
