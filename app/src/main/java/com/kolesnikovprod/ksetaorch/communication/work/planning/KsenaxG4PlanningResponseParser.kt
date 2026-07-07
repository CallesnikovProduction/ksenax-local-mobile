package com.kolesnikovprod.ksetaorch.communication.work.planning

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Парсер компактного JSON-плана от G4.
 *
 * Parser строг к смыслу и терпим к лишнему тексту вокруг JSON: локальная модель
 * иногда добавляет фразу до/после объекта, но рабочий pipeline должен брать
 * первый полный JSON-object и валидировать уже его.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal object KsenaxG4PlanningResponseParser {

    fun parse(rawText: String): KsenaxPlanningParseResult {
        if (rawText.isBlank()) {
            return failure(rawText, "Planner response is blank.")
        }

        val root = try {
            JSONObject(rawText.extractJsonObject())
        } catch (jsonException: JSONException) {
            return failure(rawText, "Planner response is not valid JSON: ${jsonException.message}")
        } catch (argumentException: IllegalArgumentException) {
            return failure(rawText, argumentException.message ?: "Planner response does not contain JSON object.")
        }

        return try {
            when (root.optNonBlankString("type").normalizeType()) {
                "PLAN" -> parseActionPlan(root)
                "CLARIFICATION" -> parseClarification(root)
                "REFUSAL" -> parseRefusal(root)
                "ASSISTANT_MESSAGE" -> parseAssistantMessage(root)
                else -> failure(rawText, "Unknown planner response type: ${root.optString("type")}.")
            }
        } catch (error: IllegalArgumentException) {
            failure(rawText, error.message ?: "Planner response violates contract.")
        }
    }

    private fun parseActionPlan(root: JSONObject): KsenaxPlanningParseResult {
        val stepsJson = root.optJSONArray("steps")
            ?: throw IllegalArgumentException("Plan response must contain `steps` array.")
        require(stepsJson.length() > 0) {
            "Plan response must contain at least one step."
        }

        val steps = (0 until stepsJson.length()).map { index ->
            val step = stepsJson.optJSONObject(index)
                ?: throw IllegalArgumentException("Plan step must be a JSON object.")
            val actionName = step.optNonBlankString("action")
                ?: step.optNonBlankString("action_name")
                ?: throw IllegalArgumentException("Plan step action must not be blank.")

            KsenaxWorkPlanStep(
                id = step.optNonBlankString("id") ?: "step_${index + 1}",
                actionName = actionName,
                instruction = step.optNonBlankString("instruction")
                    ?: step.optNonBlankString("command")
                    ?: actionName,
                plannerInputJson = step.optJSONObject("input")?.toString(),
                comment = step.optNonBlankString("comment"),
            )
        }

        return success(
            KsenaxWorkPlan.ActionPlan(
                steps = steps,
                plannerComment = root.optNonBlankString("comment"),
            )
        )
    }

    private fun parseClarification(root: JSONObject): KsenaxPlanningParseResult =
        success(
            KsenaxWorkPlan.Clarification(
                question = root.optNonBlankString("question")
                    ?: throw IllegalArgumentException("Clarification must contain question."),
                plannerComment = root.optNonBlankString("comment"),
            )
        )

    private fun parseRefusal(root: JSONObject): KsenaxPlanningParseResult =
        success(
            KsenaxWorkPlan.Refusal(
                reason = root.optNonBlankString("reason")
                    ?: throw IllegalArgumentException("Refusal must contain reason."),
                code = root.optNonBlankString("code") ?: "UNSUPPORTED_ACTION",
                plannerComment = root.optNonBlankString("comment"),
            )
        )

    private fun parseAssistantMessage(root: JSONObject): KsenaxPlanningParseResult =
        success(
            KsenaxWorkPlan.AssistantMessage(
                message = root.optNonBlankString("message")
                    ?: throw IllegalArgumentException("Assistant message must contain message."),
                plannerComment = root.optNonBlankString("comment"),
            )
        )

    private fun success(plan: KsenaxWorkPlan): KsenaxPlanningParseResult =
        KsenaxPlanningParseResult.Success(plan)

    private fun failure(rawText: String, reason: String): KsenaxPlanningParseResult =
        KsenaxPlanningParseResult.Failure(
            rawText = rawText,
            reason = reason,
        )
}

private fun JSONObject.optNonBlankString(name: String): String? =
    (opt(name) as? String)
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun String?.normalizeType(): String =
    orEmpty()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')

private fun String.extractJsonObject(): String {
    val start = indexOf('{')
    require(start >= 0) {
        "Planner response does not contain JSON object."
    }

    var depth = 0
    var insideString = false
    var escaped = false

    for (index in start until length) {
        val char = this[index]

        if (escaped) {
            escaped = false
            continue
        }
        if (insideString && char == '\\') {
            escaped = true
            continue
        }
        if (char == '"') {
            insideString = !insideString
            continue
        }
        if (!insideString) {
            when (char) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return substring(start, index + 1)
                    }
                }
            }
        }
    }

    throw IllegalArgumentException("Planner response does not contain complete JSON object.")
}
