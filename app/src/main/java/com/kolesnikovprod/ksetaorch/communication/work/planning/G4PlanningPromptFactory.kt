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
            appendLine("Role: deterministic Ksenax action planner. Never execute actions.")
            appendLine("Output: exactly one compact JSON object, without Markdown or prose.")
            appendLine("Each plan step is one atomic FG action and executes in array order.")
            appendLine("Current local date-time: $nowIso")
            appendLine()
            appendLine("Atomic actions (code | input | meaning):")
            actionSpecs.forEach { spec ->
                appendLine("- ${spec.name} | ${spec.inputHint} | ${spec.description}")
            }
            appendLine()
            appendLine("Allowed outputs:")
            appendLine(
                "{\"type\":\"plan\",\"comment\":\"short hidden Russian progress comment\"," +
                    "\"steps\":[{\"id\":\"step_1\",\"action\":\"exact_action_code\"," +
                    "\"instruction\":\"short FG command\",\"comment\":\"short hidden Russian action comment\"," +
                    "\"input\":{}}]}"
            )
            appendLine(
                "{\"type\":\"clarification\",\"question\":\"short Russian question\"," +
                    "\"comment\":\"short hidden reason\"}"
            )
            appendLine(
                "{\"type\":\"refusal\",\"reason\":\"short Russian reason\"," +
                    "\"code\":\"UNSUPPORTED_ACTION\",\"comment\":\"short hidden reason\"}"
            )
            appendLine()
            appendLine("Hard rules:")
            appendLine("- Use only action codes listed above; never invent a tool or field name.")
            appendLine("- Preserve ALL requested actions, including mixed note + alarm/calendar requests. Never silently drop an intent.")
            appendLine("- A fact such as 'I must be at work at seven' is NOT a command to create an alarm.")
            appendLine("- For alarms, system Clock supports only the next 24 hours (including count N at 5-minute gaps); refuse later dates.")
            appendLine("- Preserve every explicit name, fact, date, time, number, unit, emotion, and requested wording.")
            appendLine("- Keep numeric JSON fields numeric. Never swap hours, minutes, count, date, or clock time.")
            appendLine("- A bulk alarm request with count N is one alarm action with exact count N, not N plan steps.")
            appendLine("- Use multiple steps only for multiple actions or a real dependency; order them by dependency.")
            appendLine("- Every input is a JSON object containing only fields needed by that action.")
            appendLine("- If a required value cannot be derived, return clarification instead of guessing.")
            appendLine("- For note write include title and markdown_body with the complete requested content, not a restatement of the command.")
            appendLine("- For note analysis include title and analysis_markdown with concrete generated analysis.")
            appendLine("- For calendar use one start_local_date_time as yyyy-MM-dd'T'HH:mm; never calculate epoch millis or alternative delay/all_day fields. Missing exact time requires clarification.")
            appendLine("- Resolve Russian date/time words against Current local date-time: 'восьмое июля' means YYYY-07-08, 'семнадцать часов вечера' means 17:00.")
            appendLine("- Agentic mode does not chat. If no action is possible, return clarification or refusal.")
            appendLine()
            appendLine("User request:")
            append(userText)
        }

        return KsenaxModelRequest(
            prompt = prompt,
            systemInstruction =
                "You are a deterministic Android action planner. Return exactly one allowed JSON object.",
            profile = KsenaxModelTaskProfile.ROUTER,
        )
    }
}
