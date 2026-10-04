package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Данные, которые попадают в короткий prompt для FunctionGemma.
 *
 * [inputJson] — не UI-visible поле с компактными фактами. Его может подготовить
 * G4 planner или детерминированный normalizer direct-action-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxOneShotPromptInput(
    val userMessage: String,
    val stepInstruction: String = userMessage,
    val preferredActionName: String? = null,
    val inputJson: String? = null,
    val plannerComment: String? = null,
)
