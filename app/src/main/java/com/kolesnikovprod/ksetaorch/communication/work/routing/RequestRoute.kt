package com.kolesnikovprod.ksetaorch.communication.work.routing

/**
 * Первичное решение FunctionGemma для одного агентного запроса.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal sealed interface RequestRoute {
    data class FastTool(val call: com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionCall) : RequestRoute
    data object PlannedWork : RequestRoute
    data class Clarification(val question: String) : RequestRoute
    data class Unsupported(val reason: String) : RequestRoute
}
