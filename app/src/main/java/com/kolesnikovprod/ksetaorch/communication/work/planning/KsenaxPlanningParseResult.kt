package com.kolesnikovprod.ksetaorch.communication.work.planning

/**
 * Результат разбора JSON, который вернула G4 planning-модель.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal sealed interface KsenaxPlanningParseResult {

    data class Success(
        val plan: KsenaxWorkPlan,
    ) : KsenaxPlanningParseResult

    data class Failure(
        val rawText: String,
        val reason: String,
    ) : KsenaxPlanningParseResult
}
