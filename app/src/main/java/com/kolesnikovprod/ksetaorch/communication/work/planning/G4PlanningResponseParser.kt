package com.kolesnikovprod.ksetaorch.communication.work.planning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Разбирает и проверяет JSON-план G4 без зависимости от Android API.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal object G4PlanningResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(
        rawText: String,
        allowedActionNames: Set<String>,
    ): PlanningParseResult {
        if (rawText.isBlank()) return failure(rawText, "Planner response is blank.")
        if (allowedActionNames.isEmpty()) return failure(rawText, "Planner has no allowed actions.")

        val root = try {
            json.parseToJsonElement(rawText.extractJsonObject()) as? JsonObject
                ?: return failure(rawText, "Planner response root must be a JSON object.")
        } catch (error: IllegalArgumentException) {
            return failure(rawText, error.message ?: "Planner response is not valid JSON.")
        }

        return try {
            when (root.nonBlankString("type").normalizeType()) {
                "PLAN" -> parseActionPlan(root, allowedActionNames)
                "CLARIFICATION" -> parseClarification(root)
                "REFUSAL" -> parseRefusal(root)
                else -> failure(
                    rawText,
                    "Unknown planner response type: ${root.nonBlankString("type").orEmpty()}.",
                )
            }
        } catch (error: IllegalArgumentException) {
            failure(rawText, error.message ?: "Planner response violates contract.")
        }
    }

    private fun parseActionPlan(
        root: JsonObject,
        allowedActionNames: Set<String>,
    ): PlanningParseResult {
        val stepsJson = root["steps"] as? JsonArray
            ?: throw IllegalArgumentException("Plan response must contain `steps` array.")
        require(stepsJson.isNotEmpty()) {
            "Plan response must contain at least one step."
        }
        require(stepsJson.size <= MAX_PLAN_STEPS) {
            "Plan response exceeds the $MAX_PLAN_STEPS step limit."
        }

        val steps = stepsJson.mapIndexed { index, element ->
            val step = element as? JsonObject
                ?: throw IllegalArgumentException("Plan step must be a JSON object.")
            val actionName = step.nonBlankString("action")
                ?: step.nonBlankString("action_name")
                ?: throw IllegalArgumentException("Plan step action must not be blank.")
            require(actionName in allowedActionNames) {
                "Plan step uses unsupported action: $actionName."
            }

            val input = step["input"]?.let { inputElement ->
                inputElement as? JsonObject
                    ?: throw IllegalArgumentException("Plan step input must be a JSON object.")
            }
            KsenaxWorkPlanStep(
                id = step.nonBlankString("id") ?: "step_${index + 1}",
                actionName = actionName,
                instruction = step.nonBlankString("instruction")
                    ?: step.nonBlankString("command")
                    ?: actionName,
                plannerInputJson = input?.toString(),
                comment = step.nonBlankString("comment"),
            )
        }
        require(steps.map(KsenaxWorkPlanStep::id).toSet().size == steps.size) {
            "Plan step ids must be unique."
        }

        return success(
            KsenaxWorkPlan.ActionPlan(
                steps = steps,
                plannerComment = root.nonBlankString("comment"),
            )
        )
    }

    private fun parseClarification(root: JsonObject): PlanningParseResult =
        success(
            KsenaxWorkPlan.Clarification(
                question = root.nonBlankString("question")
                    ?: throw IllegalArgumentException("Clarification must contain question."),
                plannerComment = root.nonBlankString("comment"),
            )
        )

    private fun parseRefusal(root: JsonObject): PlanningParseResult =
        success(
            KsenaxWorkPlan.Refusal(
                reason = root.nonBlankString("reason")
                    ?: throw IllegalArgumentException("Refusal must contain reason."),
                code = root.nonBlankString("code") ?: "UNSUPPORTED_ACTION",
                plannerComment = root.nonBlankString("comment"),
            )
        )

    private fun success(plan: KsenaxWorkPlan): PlanningParseResult =
        PlanningParseResult.Success(plan)

    private fun failure(rawText: String, reason: String): PlanningParseResult =
        PlanningParseResult.Failure(
            rawText = rawText,
            reason = reason,
        )

    private const val MAX_PLAN_STEPS = 32
}

private fun JsonObject.nonBlankString(name: String): String? =
    get(name)
        ?.let { element ->
            val primitive = element as? kotlinx.serialization.json.JsonPrimitive
                ?: throw IllegalArgumentException("Planner field '$name' must be a string.")
            require(primitive.isString) { "Planner field '$name' must be a string." }
            primitive.contentOrNull
        }
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun String?.normalizeType(): String =
    orEmpty()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')

private fun String.extractJsonObject(): String {
    val trimmed = trim()
    // Единственная допустимая оболочка не исправляет и не дополняет сам JSON.
    val normalized = if (trimmed.startsWith("```json\n") && trimmed.endsWith("\n```")) {
        trimmed.removePrefix("```json\n").removeSuffix("\n```").trim()
    } else trimmed
    require(normalized.startsWith('{') && normalized.endsWith('}')) {
        "Planner response must be exactly one JSON object without surrounding prose."
    }
    return normalized
}
