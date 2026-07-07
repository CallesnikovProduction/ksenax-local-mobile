package com.kolesnikovprod.ksetaorch.communication.work.planning

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import java.time.ZonedDateTime

/**
 * Компактный prompt builder для G4 planner-а.
 *
 * Он показывает модели только список атомарных FG-actions и JSON-контракт
 * плана. Полные FunctionGemma declarations сюда не попадают, чтобы G4 не
 * занималась чужим протоколом и не раздувала prompt.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxG4PlanningPromptFactory(
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
            appendLine("You are Ksenax G4 planner.")
            appendLine("Plan Android actions, but never execute them.")
            appendLine("FG is the only action compiler. Return only compact JSON.")
            appendLine("Every action step must be atomic and must use one listed FG action name.")
            appendLine("Generate content only when the action input needs content, for example note markdown or analysis.")
            appendLine("Use exact field names from input hints. Never invent misspelled keys like tilte.")
            appendLine("Preserve concrete user details: names, dates, emotions, facts, and requested wording.")
            appendLine("Do not chat in agentic mode. Use assistant_message only when no action is needed.")
            appendLine("If required data is missing, return clarification.")
            appendLine()
            appendLine("Current time: $nowIso")
            appendLine()
            appendLine("Available FG actions:")
            actionSpecs.forEach { spec ->
                appendLine("- ${spec.name}: ${spec.description}")
                appendLine("  input: ${spec.inputHint}")
                spec.examples.take(2).forEach { example ->
                    appendLine("  example: $example")
                }
            }
            appendLine()
            appendLine("JSON contract:")
            appendLine("""{"type":"plan","comment":"internal short Russian comment","steps":[{"id":"step_1","action":"fg_action_name","instruction":"short command for FG","comment":"internal action comment","input":{}}]}""")
            appendLine("""{"type":"clarification","question":"short Russian question","comment":"internal reason"}""")
            appendLine("""{"type":"refusal","reason":"short Russian reason","code":"UNSUPPORTED_ACTION","comment":"internal reason"}""")
            appendLine("""{"type":"assistant_message","message":"short Russian message","comment":"internal reason"}""")
            appendLine()
            appendLine("Rules:")
            appendLine("- For note save plus analysis, create two steps: note write, then note analysis append.")
            appendLine("- For note write input, always include title and markdown_body. The markdown_body must preserve every concrete detail from the user request, not merely restate the command.")
            appendLine("- For note title, use a short semantic title without date. The executor may append it into today's daily note.")
            appendLine("- For note analysis input, always include title and analysis_markdown. Put your generated analysis there, with concrete references to the note content.")
            appendLine("- For calendar input, prefer start_local_date_time in yyyy-MM-dd'T'HH:mm. Do not calculate epoch millis.")
            appendLine("- For Russian calendar text like 'восьмое июля' normalize to numeric date. For 'семнадцать часов вечера' use 17:00.")
            appendLine("- Interpret date words relative to Current time. If today is 2026-07-07, '8 июля' means 2026-07-08.")
            appendLine("- Keep input JSON small and only with fields needed by the selected action. Do not pass empty input for actions with required input.")
            appendLine("- Do not include markdown fences around JSON.")
            appendLine()
            appendLine("User request:")
            append(userText)
        }

        return KsenaxModelRequest(
            prompt = prompt,
            systemInstruction = "Return only a JSON object for the Ksenax agentic planner.",
            profile = KsenaxModelTaskProfile.ROUTER,
        )
    }
}
