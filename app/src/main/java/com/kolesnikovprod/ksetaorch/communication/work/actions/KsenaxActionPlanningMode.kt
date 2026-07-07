package com.kolesnikovprod.ksetaorch.communication.work.actions

/**
 * Нужно ли action прогонять через G4 planner.
 *
 * NonPlanable — маленькие действия, где FunctionGemma сама способна выбрать
 * function-call по user prompt: фонарик, простые будильники.
 *
 * Planable — действия, которым нужен G4-контент, нормализация даты/смысла или
 * несколько шагов: заметки, анализ, календарь.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxActionPlanningMode {
    Planable,
    NonPlanable,
}
