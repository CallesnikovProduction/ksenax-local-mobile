package com.kolesnikovprod.ksetaorch.communication.work.routing

import kotlinx.serialization.json.JsonObject

/** Неавторизованное решение маршрутизатора, без Android-исполнения.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
sealed interface KsenaxRoutingDecision {
    data class FastTool(val toolName: String, val arguments: JsonObject) : KsenaxRoutingDecision
    data object PlannedWork : KsenaxRoutingDecision
    data class Clarification(val question: String?) : KsenaxRoutingDecision
    data class Unsupported(val reason: String?) : KsenaxRoutingDecision
}
