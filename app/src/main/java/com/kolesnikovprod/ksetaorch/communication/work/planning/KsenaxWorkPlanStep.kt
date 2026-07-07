package com.kolesnikovprod.ksetaorch.communication.work.planning

/**
 * Один атомарный шаг плана, который G4 отдаёт в сторону FG.
 *
 * [comment] и [plannerInputJson] — non-UI-visible metadata. Они нужны для
 * трассировки и будущего красивого отображения, но текущий UI не обязан их
 * показывать пользователю.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxWorkPlanStep(
    val id: String,
    val actionName: String,
    val instruction: String,
    val plannerInputJson: String? = null,
    val comment: String? = null,
)
