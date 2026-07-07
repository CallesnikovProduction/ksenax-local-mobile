package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Данные, которые попадают в короткий prompt для FunctionGemma.
 *
 * [plannerInputJson] — не UI-visible поле. Его пишет G4 planner, чтобы FG
 * скомпилировала конкретный function-call без повторного тяжёлого reasoning.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxOneShotPromptInput(
    val userMessage: String,
    val stepInstruction: String = userMessage,
    val preferredActionName: String? = null,
    val plannerInputJson: String? = null,
    val plannerComment: String? = null,
)
