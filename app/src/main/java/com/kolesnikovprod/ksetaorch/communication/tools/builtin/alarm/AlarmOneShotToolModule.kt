package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionInputDraft
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxDirectActionRoute
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.scheduling.LocalScheduleParser
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.MissingActionArgument
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.serialization.json.*

/**
 * Связывает alarm-actions, быстрый маршрут и Android-исполнитель.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class AlarmOneShotToolModule(
    override val executor: KsenaxToolExecutor,
) : KsenaxOneShotActionKit {

    override val id: String = "system.alarm.oneshot"

    override val namespace: String = "system"

    override val supportsFastPath = true

    override val directRoute: KsenaxDirectActionRoute =
        KsenaxDirectActionRoute(
            description = "one alarm create or dismiss-all command",
            keywords = AlarmOneShotKeywords,
        )

    override val actionSpecs: List<KsenaxWorkActionSpec> =
        listOf(
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AtTime.codeName,
                description = "Creates Android alarms at a local HH:mm clock time.",
                inputHint = """{"time":"HH:mm","count":optional integer,"label":optional string}""",
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AfterHours.codeName,
                description = "Creates Android alarms after a number of hours. Do not convert hours to clock time.",
                inputHint = """{"hours":number,"count":optional integer,"label":optional string}""",
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AfterMinutes.codeName,
                description = "Creates Android alarms after a number of minutes.",
                inputHint = """{"minutes":integer,"count":optional integer,"label":optional string}""",
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AtDateTime.codeName,
                description = "Creates Android alarms at exact local date-time.",
                inputHint = """{"date_time":"yyyy-MM-dd'T'HH:mm","count":optional integer,"label":optional string}""",
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.ClearAll.codeName,
                description = "Requests dismissal of all alarms through the installed Android Clock app.",
                inputHint = "No input object is needed.",
            ),
        )

    override val protocol: KsenaxOneShotToolProtocol = AlarmOneShotProtocol

    override fun buildDirectActionDraft(userMessage: String): KsenaxActionInputDraft? =
        AlarmUserPromptDraft.build(userMessage)

    override fun buildFastActionDraft(userMessage: String, actionName: String, now: ZonedDateTime): KsenaxActionInputDraft? {
        if (actionName == AlarmToolOneShot.ClearAll.codeName) {
            val normalized = LocalScheduleParser.normalize(userMessage)
            val clearing = Regex("удал|очист|отключ|убер|снес").containsMatchIn(normalized)
            val all = Regex("(?<![\\p{L}\\p{N}])(все|всех|полностью)(?![\\p{L}\\p{N}])").containsMatchIn(normalized)
            if (!clearing || !all || !normalized.contains("будильник")) throw MissingActionArgument("явную команду отключения всех будильников")
            return null
        }
        val minutes = LocalScheduleParser.amount(userMessage, "мин")
        val hours = LocalScheduleParser.amount(userMessage, "час|ч\\b")
        val normalizedTime = LocalScheduleParser.normalize(userMessage)
        if ("через" in normalizedTime && Regex("(?<![\\p{L}])(?:час(?:а|ов)?|ч)(?![\\p{L}])").containsMatchIn(normalizedTime) && "мин" in normalizedTime) {
            throw MissingActionArgument("интервал одной величиной: часы или минуты")
        }
        val explicitCount = LocalScheduleParser.alarmCount(userMessage)
        if (explicitCount == null && Regex("несколько|много|пару|штук").containsMatchIn(LocalScheduleParser.normalize(userMessage))) {
            throw MissingActionArgument("точное количество будильников")
        }
        val count = explicitCount ?: 1
        val date = LocalScheduleParser.date(userMessage, now.toLocalDate())
        val time = LocalScheduleParser.clock(userMessage)
        val (expected, input) = when {
            minutes != null -> AlarmToolOneShot.AfterMinutes.codeName to buildJsonObject { put("minutes", minutes) }
            hours != null -> AlarmToolOneShot.AfterHours.codeName to buildJsonObject { put("hours", hours) }
            time != null && date != null -> AlarmToolOneShot.AtDateTime.codeName to buildJsonObject { put("date_time", date.atTime(time).toString()) }
            time != null -> AlarmToolOneShot.AtTime.codeName to buildJsonObject { put("time", time.toString()) }
            else -> throw MissingActionArgument("время будильника")
        }
        val args = JsonObject(input + mapOf("count" to JsonPrimitive(count)))
        return KsenaxActionInputDraft(expectedActionName = expected, argumentsJson = args.toString(), instruction = userMessage)
    }

    override fun validateExecutableCall(call: KsenaxToolCall, now: ZonedDateTime) {
        if (call.name == AlarmToolOneShot.ClearAll.codeName) return
        val args = Json.parseToJsonElement(call.arguments.JSONtoString()).jsonObject
        val count = args["count"]?.jsonPrimitive?.int ?: 1
        require(count in 1..50) { "Количество будильников должно быть от 1 до 50." }
        val start = when (call.name) {
            AlarmToolOneShot.AfterMinutes.codeName -> now.plusMinutes(args.getValue("minutes").jsonPrimitive.long)
            AlarmToolOneShot.AfterHours.codeName -> {
                val hours = args.getValue("hours").jsonPrimitive.double
                require(hours > 0 && hours < 24) { "Интервал будильника должен быть больше нуля и меньше 24 часов." }
                now.plusSeconds((hours * 3600).toLong())
            }
            AlarmToolOneShot.AtDateTime.codeName -> LocalDateTime.parse(args.getValue("date_time").jsonPrimitive.content).atZone(now.zone)
            AlarmToolOneShot.AtTime.codeName -> {
                val time = LocalTime.parse(args.getValue("time").jsonPrimitive.content)
                val candidate = now.toLocalDate().atTime(time).atZone(now.zone)
                if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
            }
            else -> throw IllegalArgumentException("Неизвестное действие будильника.")
        }
        require(start.isAfter(now)) { "Время будильника должно быть в будущем." }
        require(Duration.between(now, start.plusMinutes((count - 1) * 5L)).toMinutes() < 1440) {
            "Системные Часы поддерживают только ближайшие 24 часа, включая все будильники серии."
        }
    }

    override fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall =
        step.plannerInputJson
            ?.takeIf(String::isNotBlank)
            ?.let { plannerInputJson ->
                compiledCall.copy(
                    arguments = KsenaxRawToolArgumentsObject(plannerInputJson),
                )
            }
            ?: compiledCall
}
