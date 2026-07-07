package com.kolesnikovprod.ksetaorch.communication.work.planning

/**
 * Решение G4 planner-а для одного fresh agentic turn-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxWorkPlan {

    val plannerComment: String?

    data class ActionPlan(
        val steps: List<KsenaxWorkPlanStep>,
        override val plannerComment: String? = null,
    ) : KsenaxWorkPlan

    data class Clarification(
        val question: String,
        override val plannerComment: String? = null,
    ) : KsenaxWorkPlan

    data class Refusal(
        val reason: String,
        val code: String = "UNSUPPORTED_ACTION",
        override val plannerComment: String? = null,
    ) : KsenaxWorkPlan

    data class AssistantMessage(
        val message: String,
        override val plannerComment: String? = null,
    ) : KsenaxWorkPlan
}
