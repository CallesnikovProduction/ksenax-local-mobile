package com.kolesnikovprod.ksetaorch.communication.work.routing

import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import kotlinx.serialization.json.*
import java.time.ZonedDateTime

/**
 * FG выбирает известную атомарную функцию или явный неисполняемый маршрут.
 * Keywords не участвуют в выборе. Первый вызов выбирает функцию из каталога;
 * параметризованное действие получает свою схему отдельным коротким запросом.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class FunctionGemmaRoutingProtocol(private val actions: List<KsenaxOneShotDeclaration>) {
    private val actionNames = actions.map { it.codeName }.toSet()
    private val shortDeclarations = actions.map {
        KsenaxModelFunctionDeclaration(it.codeName,
            it.description.substringBefore("Use for").substringBefore("Arguments are").trim())
    }

    fun buildRequest(userMessage: String, now: ZonedDateTime = ZonedDateTime.now()): KsenaxModelFunctionRequest {
        if (userMessage.toByteArray(Charsets.UTF_8).size > MAX_ROUTING_INPUT_BYTES) {
            // Никакой части длинного UP не исполняем напрямую. G4 получит оригинал.
            return KsenaxModelFunctionRequest(
                userMessage = "User content exceeds the small action-router budget. Call route_planned_work.",
                functions = listOf(KsenaxModelFunctionDeclaration("route_planned_work", "Process the complete user request in the planner.")),
            )
        }
        return KsenaxModelFunctionRequest(
            userMessage = userMessage,
            functions = shortDeclarations + listOf(
                KsenaxModelFunctionDeclaration("route_planned_work", "Generate/write/analyze notes or text, reason, or perform multiple actions."),
                KsenaxModelFunctionDeclaration("route_clarification", "Command missing required time or meaning."),
                KsenaxModelFunctionDeclaration("route_unsupported", "No supported command, a question or a fact."),
            ),
        )
    }

    fun buildRefinementRequest(userMessage: String, allowedActions: Set<String>): KsenaxModelFunctionRequest {
        require(allowedActions.isNotEmpty() && actionNames.containsAll(allowedActions))
        return KsenaxModelFunctionRequest(userMessage, shortDeclarations.filter { it.name in allowedActions })
    }

    fun parseResponse(response: KsenaxModelFunctionResponse): RequestRoute {
        require(response.calls.size == 1) { "FunctionGemma must return exactly one routing call." }
        val call = response.calls.single()
        val args = Json.parseToJsonElement(call.argumentsJson) as? JsonObject
            ?: throw IllegalArgumentException("Routing arguments must be an object.")
        require(args.isEmpty()) { "Routing calls must not contain action arguments." }
        return when (call.name) {
            "route_planned_work" -> RequestRoute.PlannedWork
            "route_clarification" -> RequestRoute.Clarification("Уточни действие и необходимые параметры, например время.")
            "route_unsupported" -> RequestRoute.Unsupported("Запрос не соответствует доступным действиям.")
            in actionNames -> RequestRoute.FastTool(call)
            else -> throw IllegalArgumentException("Unsupported FunctionGemma action: ${call.name}.")
        }
    }

    companion object {
        const val MAX_ROUTING_INPUT_BYTES = 256
    }
}
