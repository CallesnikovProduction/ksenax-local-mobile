package com.kolesnikovprod.ksetaorch.communication.work.routing

import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import kotlinx.serialization.json.*
import java.time.ZonedDateTime

/**
 * FG выбирает известную атомарную функцию или явный неисполняемый маршрут.
 * Keywords не участвуют в выборе. Первый вызов выбирает код без аргументов;
 * параметризованное действие получает свою схему отдельным коротким запросом.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class FunctionGemmaRoutingProtocol(private val actions: List<KsenaxOneShotDeclaration>) {
    private val actionNames = actions.map { it.codeName }.toSet()

    fun buildRequest(userMessage: String, now: ZonedDateTime = ZonedDateTime.now()): KsenaxModelFunctionRequest {
        if (userMessage.toByteArray(Charsets.UTF_8).size > MAX_ROUTING_INPUT_BYTES) {
            // Никакой части длинного UP не исполняем напрямую. G4 получит оригинал.
            return KsenaxModelFunctionRequest(
                userMessage = "User content exceeds the small action-router budget. Call route_planned_work.",
                functions = listOf(KsenaxModelFunctionDeclaration("route_planned_work", "Process the complete user request in the planner.")),
            )
        }
        val controls = listOf(
            KsenaxModelFunctionDeclaration("route_planned_work", "Generate/write/analyze notes or text, reason, or perform multiple actions."),
            KsenaxModelFunctionDeclaration("route_clarification", "Action requested but time, title or meaning is missing or ambiguous.", MESSAGE_SCHEMA.replace("message", "question")),
            KsenaxModelFunctionDeclaration("route_unsupported", "No supported operation, or user merely states a fact without asking an action.", MESSAGE_SCHEMA.replace("message", "reason")),
        )
        return KsenaxModelFunctionRequest(
            userMessage = "Local now: $now\nRequest: $userMessage",
            functions = actions.map { declaration ->
                KsenaxModelFunctionDeclaration(declaration.codeName, declaration.description.substringBefore("Use for").substringBefore("Arguments are"))
            } + controls,
            systemInstruction = "You are a model that can do function calling with the following functions. " +
                "Call exactly one. Use an action only for one explicit complete command. " +
                "Notes, generation, analysis and compound requests MUST use route_planned_work; preserve ALL intents. " +
                "Missing or ambiguous values: route_clarification. Never invent values or intent. " +
                "Resolve relative dates against Local now. Russian paraphrases have the same meaning.",
        )
    }

    fun parseResponse(response: KsenaxModelFunctionResponse): RequestRoute {
        require(response.calls.size == 1) { "FunctionGemma must return exactly one routing call." }
        val call = response.calls.single()
        val args = Json.parseToJsonElement(call.argumentsJson) as? JsonObject
            ?: throw IllegalArgumentException("Routing arguments must be an object.")
        return when (call.name) {
            "route_planned_work" -> { require(args.isEmpty()); RequestRoute.PlannedWork }
            "route_clarification" -> RequestRoute.Clarification(args.message("question"))
            "route_unsupported" -> RequestRoute.Unsupported(args.message("reason"))
            in actionNames -> { require(args.isEmpty()) { "Selection call must have empty arguments." }; RequestRoute.FastTool(call) }
            else -> throw IllegalArgumentException("Unsupported FunctionGemma action: ${call.name}.")
        }
    }

    private fun JsonObject.message(key: String): String {
        require(keys == setOf(key)) { "Invalid routing message fields." }
        val value = get(key) as? JsonPrimitive
        require(value?.isString == true && value.content.isNotBlank()) { "Routing message must be a non-empty string." }
        return value.content
    }

    companion object {
        const val MAX_ROUTING_INPUT_BYTES = 256
        private const val MESSAGE_SCHEMA = """{"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}"""
    }
}
