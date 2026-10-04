package com.kolesnikovprod.ksetaorch.communication.work.actions

/**
 * Локально извлечённый черновик аргументов для маленького OneShot action.
 *
 * Это не решение о запуске tool-а: конкретную функцию всё равно выбирает
 * FunctionGemma. Draft хранит детерминированно извлечённые факты, чтобы после
 * модельного выбора проверить семантику и не потерять числа или единицы.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxActionInputDraft(
    val expectedActionName: String? = null,
    val argumentsJson: String? = null,
    val instruction: String? = null,
)
