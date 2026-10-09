package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.scheduling.LocalScheduleParser
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionInputDraft
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionSourceText
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxPlannedInputValidator
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.MissingActionArgument
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlinx.serialization.json.*

/**
 * Связывает запланированное событие с FunctionGemma и Android Calendar.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class CalendarEventOneShotToolModule(
    override val executor: KsenaxToolExecutor,
) : KsenaxOneShotActionKit {

    override val id: String = "system.calendar.oneshot"

    override val namespace: String = "system"

    override val supportsFastPath: Boolean = true

    override val actionSpecs: List<KsenaxWorkActionSpec> =
        listOf(
            KsenaxWorkActionSpec(
                name = CalendarEventOneShot.codeName,
                description = "Prepares a calendar event through Android Calendar insert screen.",
                inputHint = """{"title":"short event title","start_local_date_time":"yyyy-MM-dd'T'HH:mm"}; optional fields: duration_minutes integer, location string, description string. Do not calculate epoch millis.""",
            )
        )

    override val protocol: KsenaxOneShotToolProtocol = CalendarEventOneShotProtocol

    override fun validateSourceRequest(userMessage: String) {
        if (requestedParts(userMessage).isEmpty()) {
            throw MissingActionArgument("явный запрос календарного события")
        }
    }

    override fun buildFastActionDraft(userMessage: String, actionName: String, now: ZonedDateTime): KsenaxActionInputDraft {
        val date = LocalScheduleParser.date(userMessage, now.toLocalDate()) ?: throw MissingActionArgument("дату события")
        val durations = durationMinutes.findAll(userMessage).map { match ->
            // Обе формы используют одну грамматику количества, а не ответ модели.
            LocalScheduleParser.amount("через ${match.groupValues[1]} минут", "мин")
                ?: throw MissingActionArgument("точную продолжительность события в минутах")
        }.toSet()
        if (durations.size > 1) throw MissingActionArgument("однозначную продолжительность события")
        val time = LocalScheduleParser.clock(durationMinutes.replace(userMessage, " ")) ?: throw MissingActionArgument("точное время события")
        // Название извлекает FG. Дата и время закрепляются только по явному UP.
        return KsenaxActionInputDraft(expectedActionName = CalendarEventOneShot.codeName,
            argumentsJson = buildJsonObject {
                put("start_local_date_time", date.atTime(time).toString())
                durations.singleOrNull()?.let { put("duration_minutes", it) }
            }.toString(),
            instruction = userMessage)
    }

    override fun validatePlanningSource(userMessage: String, requestTime: ZonedDateTime) {
        validateSourceRequest(userMessage)
        sourceDrafts(userMessage, requestTime)
    }

    private fun sourceDrafts(userMessage: String, requestTime: ZonedDateTime): List<KsenaxActionInputDraft> =
        requestedParts(userMessage).map { part -> buildFastActionDraft(part, CalendarEventOneShot.codeName, requestTime) }

    override fun validatePlannedInputs(userMessage: String, requestTime: ZonedDateTime, calls: List<KsenaxToolCall>) {
        val sources = sourceDrafts(userMessage, requestTime)
        val normalizedCalls = calls.map { call ->
            val input = Json.parseToJsonElement(call.arguments.JSONtoString()).jsonObject
            val dateTime = LocalDateTime.parse(input.getValue("start_local_date_time").jsonPrimitive.content)
            call.copy(arguments = KsenaxRawToolArgumentsObject(JsonObject(input +
                ("start_local_date_time" to JsonPrimitive(dateTime.toString()))).toString()))
        }
        KsenaxPlannedInputValidator.validate(normalizedCalls, sources)
    }

    private fun requestedParts(text: String): List<String> = KsenaxActionSourceText.executableParts(text).filter { part ->
        calendarDomain.containsMatchIn(part) && (calendarCommand.containsMatchIn(part) || KsenaxActionSourceText.hasExplicitNeed(part, calendarDomain))
    }

    private val calendarDomain = Regex("календар|событ|встреч|созвон|мероприят|calendar|event|meeting", RegexOption.IGNORE_CASE)
    private val calendarCommand = Regex("(?<![\\p{L}\\p{N}_])(?:создай(?:те)?|создать|добавь(?:те)?|добавить|запиши(?:те)?|записать|занеси(?:те)?|занести|(?:create|add|schedule)(?=\\s))(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
    private val durationMinutes = Regex("(?<![\\p{L}])(?:на|длительностью|продолжительностью)\\s+([\\p{L}\\d]+(?:\\s+[\\p{L}]+)?)\\s+мин\\p{L}*(?![\\p{L}])", RegexOption.IGNORE_CASE)

    override fun validateExecutableCall(call: KsenaxToolCall, now: ZonedDateTime) {
        val args = Json.parseToJsonElement(call.arguments.JSONtoString()).jsonObject
        val dateTime = args["start_local_date_time"]?.jsonPrimitive?.content
            ?: throw MissingActionArgument("дату и время события")
        val start = LocalDateTime.parse(dateTime).atZone(now.zone)
        require(!start.isBefore(now)) { "Время начала события не должно быть в прошлом." }
        args["duration_minutes"]?.jsonPrimitive?.long?.let {
            require(it in 1..527040) { "Продолжительность события должна быть от 1 до 527040 минут." }
        }
    }

    override fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall {
        val input = Json.parseToJsonElement(step.plannerInputJson ?: compiledCall.arguments.JSONtoString()).jsonObject
        val normalized = if ("start_local_date_time" !in input && "start_local_date" in input && "start_local_time" in input) {
            val date = input.getValue("start_local_date") as? JsonPrimitive
            val time = input.getValue("start_local_time") as? JsonPrimitive
            require(date?.isString == true && time?.isString == true) { "Дата и время события должны быть строками." }
            JsonObject(input.filterKeys { it !in setOf("start_local_date", "start_local_time") } +
                ("start_local_date_time" to JsonPrimitive("${date.content}T${time.content}")))
        } else input
        return compiledCall.copy(arguments = KsenaxRawToolArgumentsObject(normalized.toString()))
    }
}
