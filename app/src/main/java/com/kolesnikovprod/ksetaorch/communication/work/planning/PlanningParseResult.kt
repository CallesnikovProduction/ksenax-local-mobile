package com.kolesnikovprod.ksetaorch.communication.work.planning

/**
 * Результат разбора JSON, который вернула G4 planning-модель.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal sealed interface PlanningParseResult {

    data class Success(
        val plan: KsenaxWorkPlan,
    ) : PlanningParseResult

    data class Failure(
        val rawText: String,
        val reason: String,
    ) : PlanningParseResult
}
