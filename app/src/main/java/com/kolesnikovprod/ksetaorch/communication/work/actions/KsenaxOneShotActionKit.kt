package com.kolesnikovprod.ksetaorch.communication.work.actions

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep

/**
 * Один набор маленьких FG-actions.
 *
 * Kit не знает про UI, Room и конкретный LiteRT runtime. Он описывает actions
 * для G4 planner-а, умеет собрать FG prompt и имеет Android executor для
 * распарсенного function-call.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxOneShotActionKit {

    val id: String

    val namespace: String
        get() = "system"

    val actionSpecs: List<KsenaxWorkActionSpec>

    val planningMode: KsenaxActionPlanningMode
        get() = KsenaxActionPlanningMode.Planable

    val exposePlannerInputToFunctionGemma: Boolean
        get() = true

    val keywords: KsenaxOneShotKeywords

    val protocol: KsenaxOneShotToolProtocol

    val executor: KsenaxToolExecutor

    fun supports(userMessage: String): Boolean =
        keywords.matches(userMessage)

    fun preferredDirectActionName(userMessage: String): String? = null

    fun buildDirectActionDraft(userMessage: String): KsenaxActionInputDraft? =
        preferredDirectActionName(userMessage)
            ?.let { actionName ->
                KsenaxActionInputDraft(preferredActionName = actionName)
            }

    fun supportsAction(actionName: String): Boolean =
        actionSpecs.any { spec -> spec.name == actionName }

    fun buildFallbackPlannerInputJson(
        userMessage: String,
        step: KsenaxWorkPlanStep,
    ): String? = null

    /**
     * Последний seam перед Android executor-ом.
     *
     * По умолчанию executor получает аргументы, которые вернула FG. Planable
     * actions вроде заметок могут заменить arguments на G4 planner input, чтобы
     * не заставлять FunctionGemma переносить большие тексты.
     */
    fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall = compiledCall
}
