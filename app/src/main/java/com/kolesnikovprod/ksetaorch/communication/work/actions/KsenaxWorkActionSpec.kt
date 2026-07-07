package com.kolesnikovprod.ksetaorch.communication.work.actions

/**
 * Короткая карточка FG-action для G4 planner-а.
 *
 * Это не JSON-schema для FunctionGemma. Это компактное описание, чтобы G4
 * выбрала правильную атомарную функцию и подготовила input для следующего
 * one-shot шага.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxWorkActionSpec(
    val name: String,
    val description: String,
    val inputHint: String,
    val examples: List<String> = emptyList(),
)
