package com.kolesnikovprod.ksetaorch.live

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import java.time.ZonedDateTime
import kotlinx.serialization.json.*

/** Два экспериментальных контракта Qwen; рабочий маршрутизатор не меняют.
 * Каталог строится из реальных схем, а не из ожидаемых ответов корпуса.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal object QwenDiagnosticPrompts {
    fun englishSystem(actions: List<KsenaxOneShotDeclaration>, now: ZonedDateTime) = buildString {
        appendLine("You are an Android intent router. Output exactly one JSON object, without markdown or explanations.")
        appendLine("For a complete supported action, route is FAST_TOOL. Include tool (one exact catalog name) and arguments (an object containing that tool's fields only).")
        appendLine("For writing/analyzing notes or text, or multiple different actions: {\"route\":\"LLM_BOUND\"}.")
        appendLine("For a command with missing or ambiguous required arguments: {\"route\":\"NEEDS_CLARIFICATION\"}.")
        appendLine("For questions, facts, negated/quoted actions or unavailable actions: {\"route\":\"UNSUPPORTED\"}.")
        appendLine("No other root fields. Never invent missing values. Repeated identical alarms use count, not LLM_BOUND. Numeric arguments are numbers. count defaults to 1 only when declared. Empty schema means arguments:{}.")
        appendLine("Local date/time: ${now.toLocalDateTime().withNano(0)}. Clock time is HH:mm; date-time is yyyy-MM-dd'T'HH:mm.")
        appendLine("Catalog (optional fields have ?):")
        actions.forEach { action ->
            val schema = action.parameters?.let { Json.parseToJsonElement(it).jsonObject }
            val required = schema?.get("required")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val fields = (schema?.get("properties") as? JsonObject)?.entries?.joinToString(",") { (name, property) ->
                "$name${if (name in required) "" else "?"}:${property.jsonObject["type"]?.jsonPrimitive?.content}"
            }.orEmpty()
            appendLine("${action.codeName}: ${action.description.substringBefore("Use for").trim()} ${fields.ifEmpty { "{}" }}")
        }
    }

    fun nativeSystem(now: ZonedDateTime) = "Choose exactly one declared function for the user's intent. Never answer with prose. " +
        "For writing/analyzing notes or text, or different actions, choose route_planned_work. " +
        "For missing or ambiguous required values choose route_clarification; never invent values. " +
        "For questions, facts, negated/quoted actions or unavailable actions choose route_unsupported. " +
        "Identical alarms use count, not planning. Local date/time: ${now.toLocalDateTime().withNano(0)}."

    fun envelopeSystem(actions: List<KsenaxOneShotDeclaration>, now: ZonedDateTime) =
        "Call route_request exactly once to classify the user's intent. Do not answer with prose or standalone JSON. " +
            "The following JSON shapes specify the function arguments:\n" + englishSystem(actions, now).lineSequence().drop(1).joinToString("\n")

    fun nativeDeclarations(actions: List<KsenaxOneShotDeclaration>) = actions.map { action ->
        KsenaxModelFunctionDeclaration(action.codeName, action.description.substringBefore("Use for").trim(), action.parameters)
    } + listOf(
        KsenaxModelFunctionDeclaration("route_planned_work", "Generate or analyze notes/text, or perform different actions."),
        KsenaxModelFunctionDeclaration("route_clarification", "A command is missing required values or is ambiguous."),
        KsenaxModelFunctionDeclaration("route_unsupported", "Question, fact, negated/quoted action or unavailable action."),
    )

    fun envelopeDeclaration(actions: List<KsenaxOneShotDeclaration>) = listOf(KsenaxModelFunctionDeclaration(
        "route_request", "Report the semantic routing decision; this does not execute any action.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("route", buildJsonObject {
                    put("type", "string")
                    put("enum", JsonArray(listOf("FAST_TOOL", "LLM_BOUND", "NEEDS_CLARIFICATION", "UNSUPPORTED").map(::JsonPrimitive)))
                })
                put("tool", buildJsonObject {
                    put("type", "string"); put("enum", JsonArray(actions.map { JsonPrimitive(it.codeName) }))
                })
                put("arguments", buildJsonObject { put("type", "object") })
            })
            put("required", JsonArray(listOf(JsonPrimitive("route"))))
            put("additionalProperties", false)
        }.toString(),
    ))
}
