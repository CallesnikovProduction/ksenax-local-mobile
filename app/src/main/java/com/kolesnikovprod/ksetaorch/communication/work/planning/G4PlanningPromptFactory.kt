package com.kolesnikovprod.ksetaorch.communication.work.planning

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import java.time.ZonedDateTime

/**
 * Строит компактный строгий prompt для G4 planner-а.
 *
 * G4 видит только кодовые имена атомарных actions и их input contract. Полные
 * FunctionGemma declarations и Android-детали остаются ниже этой границы.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class G4PlanningPromptFactory(
    private val actionSpecs: List<KsenaxWorkActionSpec>,
) {

    init {
        require(actionSpecs.isNotEmpty()) {
            "G4 planning requires at least one FG action spec."
        }
        require(actionSpecs.map(KsenaxWorkActionSpec::name).toSet().size == actionSpecs.size) {
            "FG action names must be unique for G4 planning."
        }
    }

    fun buildPlanningRequest(
        userText: String,
        nowIso: String = ZonedDateTime.now().toString(),
    ): KsenaxModelRequest {
        require(userText.isNotBlank()) {
            "Planning prompt userText must not be blank."
        }

        val prompt = buildString {
            appendLine("Plan the requested Android actions. Do not execute, chat, or explain the plan.")
            appendLine("Return one compact raw JSON object, starting with { and ending with }. No Markdown fences. Escape newlines inside strings as \\n.")
            appendLine("Current local date-time: $nowIso")
            appendLine()
            appendLine("Available actions (code | input | meaning):")
            actionSpecs.forEach { spec ->
                appendLine("- ${spec.name} | ${spec.inputHint} | ${spec.description}")
            }
            appendLine()
            appendLine("Formats: plan={\"type\":\"plan\",\"steps\":[{\"action\":\"listed_code\",\"input\":{}}]}; " +
                "clarification={\"type\":\"clarification\",\"question\":\"Russian question\"}; " +
                "refusal={\"type\":\"refusal\",\"reason\":\"Russian reason\"}.")
            appendLine("Optional comment is short Russian progress metadata, not an extra step.")
            appendLine("Each step MUST contain its action AND input together. Input is never a separate step.")
            if (actionSpecs.any { it.name == "alarm_after_minutes" }) {
                appendLine("Complete example, values are unrelated to the user request: " +
                    "{\"type\":\"plan\",\"steps\":[{\"action\":\"alarm_after_minutes\",\"input\":{\"minutes\":12,\"count\":2}}]}")
            }
            appendLine()
            appendLine("Hard rules:")
            appendLine("- Only listed actions and their fields. Preserve ALL requested actions and every explicit fact, name, date, time, quantity, unit and emotion.")
            appendLine("- Questions, facts, quotations, negations and discussion of a tool are NOT commands to execute it.")
            appendLine("- For alarms, system Clock supports only the next 24 hours (including count N at 5-minute gaps); refuse later dates.")
            appendLine("- Keep numeric JSON fields numeric. Never swap hours, minutes, count, date, or clock time.")
            appendLine("- A bulk alarm request with count N is one alarm action with exact count N, not N plan steps.")
            appendLine("- Use multiple steps only for multiple actions or a real dependency; order them by dependency.")
            appendLine("- If a required value cannot be derived, return clarification instead of guessing.")
            appendLine("- For note write include title and markdown_body with finished Markdown now: not a command, template, placeholder or promise. For a topic without source text, write 3-6 specific sentences; for provided text preserve its details.")
            appendLine("- For note analysis include title and analysis_markdown with concrete generated analysis.")
            appendLine("- For calendar use one start_local_date_time as yyyy-MM-dd'T'HH:mm; never calculate epoch millis or alternative delay/all_day fields. Missing exact time requires clarification.")
            appendLine("- Resolve Russian date/time words against Current local date-time: 'восьмое июля' means YYYY-07-08, 'семнадцать часов вечера' means 17:00.")
            appendLine()
            appendLine("User request:")
            append(userText)
        }

        return KsenaxModelRequest(
            prompt = prompt,
            systemInstruction =
                "You are a deterministic Android action planner. Return one raw JSON object without Markdown fences. Generate finished Russian note content inside markdown_body; do not defer writing it.",
            profile = KsenaxModelTaskProfile.ROUTER,
        )
    }
}
