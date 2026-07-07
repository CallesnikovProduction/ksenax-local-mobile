package com.kolesnikovprod.ksetaorch.communication.work.actions

/**
 * Локально извлечённый черновик аргументов для маленького OneShot action.
 *
 * Это не замена FunctionGemma: runtime всё равно отправляет prompt в FG и
 * ждёт function-call. Draft нужен, чтобы не заставлять 270M-модель стабильно
 * считать числа, падежи и русские единицы времени.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxActionInputDraft(
    val preferredActionName: String? = null,
    val plannerInputJson: String? = null,
    val instruction: String? = null,
)
