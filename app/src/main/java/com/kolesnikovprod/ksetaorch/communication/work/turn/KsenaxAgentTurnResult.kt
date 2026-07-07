package com.kolesnikovprod.ksetaorch.communication.work.turn

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.driver.KsenaxToolOrchestrationResult
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlan

/**
 * Наружный результат одного agentic turn-а.
 *
 * Внутри новый pipeline может проходить через G4 planning, несколько FG
 * one-shot вызовов и Android executors. UI получает только итоговый безопасный
 * тип и не разбирает внутренние JSON/FunctionGemma payload-ы.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxAgentTurnResult {

    data class ToolExecution(
        val toolCalls: List<KsenaxToolCall>,
        val toolResult: KsenaxToolOrchestrationResult,
        val plan: KsenaxWorkPlan.ActionPlan? = null,
    ) : KsenaxAgentTurnResult

    data class Refusal(
        val message: String,
        val plan: KsenaxWorkPlan.Refusal? = null,
    ) : KsenaxAgentTurnResult

    data class Clarification(
        val question: String,
        val plan: KsenaxWorkPlan.Clarification? = null,
    ) : KsenaxAgentTurnResult

    data class AssistantMessage(
        val message: String,
        val plan: KsenaxWorkPlan.AssistantMessage? = null,
    ) : KsenaxAgentTurnResult

    data class ModelFailure(
        val reason: String,
        val rawText: String?,
    ) : KsenaxAgentTurnResult
}
