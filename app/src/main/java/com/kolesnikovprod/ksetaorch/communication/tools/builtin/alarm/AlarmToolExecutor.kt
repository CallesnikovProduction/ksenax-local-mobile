package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolDefinition
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.ceil


/**
 * Исполнитель системных Android-будильников.
 *
 * Класс создаёт один или несколько будильников через приложение часов Android.
 * Это не внутренние события приложения: executor использует
 * [AlarmClock.ACTION_SET_ALARM], поэтому будильники остаются в системе после
 * выхода из Ksenax.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class AlarmToolExecutor(context: Context) : KsenaxToolExecutor {

    private val appContext: Context = context.applicationContext
    private val zoneId: ZoneId = ZoneId.systemDefault()

    override suspend fun execute(
        call: KsenaxToolCall,
    ): KsenaxToolResult = withContext(Dispatchers.Default) {
        if (call.name != TOOL_NAME) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "AlarmToolExecutor cannot execute tool: ${call.name}.",
                errorCode = "INVALID_TOOL",
            )
        }

        val alarms = parseAlarms(
            arguments = call.arguments.JSONtoString(),
            now       = ZonedDateTime.now(zoneId),
        ).getOrElse { error ->
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = error.message ?: "Неверные аргументы будильника.",
                errorCode = "INVALID_ARGUMENTS",
            )
        }

        try {
            alarms.forEachIndexed { index, alarm ->
                appContext.startActivity(alarm.toAlarmClockIntent())
                if (index < alarms.lastIndex) {
                    delay(ALARM_REQUEST_DISPATCH_DELAY_MILLIS)
                }
            }
        } catch (_: SecurityException) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Android не разрешил создать системный будильник.",
                errorCode = "SET_ALARM_PERMISSION_DENIED",
            )
        } catch (_: ActivityNotFoundException) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Не удалось открыть приложение часов для создания будильника.",
                errorCode = "ALARM_CLOCK_NOT_FOUND",
            )
        } catch (error: RuntimeException) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = error.message ?: "Не удалось создать системные будильники.",
                errorCode = "ALARM_START_FAILED",
            )
        }

        KsenaxToolResult.Success(
            callId      = call.id,
            toolName    = call.name,
            message     = buildSuccessMessage(alarms.size),
            payloadJson = buildPayloadJson(alarms),
        )
    }

    private fun parseAlarms(
        arguments: String,
        now:       ZonedDateTime,
    ): Result<List<ScheduledAlarm>> =
        runCatching {
            val json = try {
                JSONObject(arguments)
            } catch (_: JSONException) {
                throw IllegalArgumentException("Arguments for $TOOL_NAME must be a JSON object.")
            }

            val count = json.optIntByNames(
                "count",
                "alarm_count",
                "alarmCount",
            ) ?: DEFAULT_ALARM_COUNT

            require(count in 1..MAX_ALARM_COUNT) {
                "Количество будильников должно быть от 1 до $MAX_ALARM_COUNT."
            }

            val firstAlarmAt = json.parseFirstAlarmAt(now)

            val alarms = List(count) { index ->
                ScheduledAlarm(
                    triggerAt = firstAlarmAt.plusMinutes(
                        index * ALARM_INTERVAL_MINUTES.toLong()
                    ),
                    label     = json.optStringByNames(
                        "label",
                        "message",
                        "reason",
                    )?.takeIf { label -> label.isNotBlank() } ?: DEFAULT_ALARM_LABEL,
                )
            }

            require(alarms.all { alarm -> alarm.triggerAt.isAfter(now) }) {
                "Время будильников должно быть в будущем."
            }
            require(alarms.all { alarm -> Duration.between(now, alarm.triggerAt).toMinutes() < MINUTES_IN_DAY }) {
                "Системный AlarmClock принимает ближайшее срабатывание по часам и минутам. " +
                        "Для дат дальше 24 часов нужен отдельный календарный или app-alarm контур."
            }

            alarms
        }

    private fun JSONObject.parseFirstAlarmAt(now: ZonedDateTime): ZonedDateTime {
        val localTime = optStringByNames(
            "start_local_time",
            "startLocalTime",
            "local_time",
            "localTime",
        )
        val localDateTime = optStringByNames(
            "start_local_date_time",
            "startLocalDateTime",
            "local_date_time",
            "localDateTime",
        )
        val timestamp = optLongByNames(
            "start_at_millis",
            "startAtMillis",
            "trigger_at_millis",
            "triggerAtMillis",
        )
        val delayMinutes = optLongByNames(
            "start_delay_minutes",
            "startDelayMinutes",
            "delay_minutes",
            "delayMinutes",
        )
        val delayHours = optDoubleByNames(
            "start_delay_hours",
            "startDelayHours",
            "delay_hours",
            "delayHours",
        )

        require(
            listOfNotNull(
                localTime,
                localDateTime,
                timestamp,
                delayMinutes,
                delayHours,
            ).size == 1
        ) {
            "Нужно передать ровно один способ задания времени: start_local_time, " +
                    "start_local_date_time, start_delay_minutes или start_delay_hours."
        }

        localTime?.let { value ->
            val parsedTime = value.parseLocalTime()
            val candidate = now.toLocalDate()
                .atTime(parsedTime)
                .atZone(zoneId)
            return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
        }

        localDateTime?.let { value ->
            return value.parseLocalDateTime().atZone(zoneId)
        }

        timestamp?.let { value ->
            val timestamp = value
            require(timestamp > 0L) {
                "start_at_millis должен быть положительным Unix timestamp в миллисекундах."
            }
            return Instant.ofEpochMilli(timestamp)
                .atZone(zoneId)
                .roundUpToMinute()
        }

        delayMinutes?.let { value ->
            require(value >= 0L) {
                "start_delay_minutes не может быть отрицательным."
            }
            return now.plusMinutes(value).roundUpToMinute()
        }

        delayHours?.let { value ->
            require(value >= 0.0) {
                "start_delay_hours не может быть отрицательным."
            }
            return now.plusMinutes(ceil(value * 60).toLong()).roundUpToMinute()
        }

        error("Alarm start-time validation completed without a selected value.")
    }

    private fun String.parseLocalTime(): LocalTime {
        val match = LOCAL_TIME_PATTERN.matchEntire(trim())
            ?: throw IllegalArgumentException(
                "start_local_time должен соответствовать 24-часовому формату HH:mm."
            )
        return LocalTime.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
        )
    }

    private fun String.parseLocalDateTime(): LocalDateTime {
        val match = LOCAL_DATE_TIME_PATTERN.matchEntire(trim())
            ?: throw IllegalArgumentException(
                "start_local_date_time должен соответствовать формату yyyy-MM-dd'T'HH:mm."
            )
        return try {
            LocalDateTime.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
                match.groupValues[5].toInt(),
            )
        } catch (error: RuntimeException) {
            throw IllegalArgumentException(
                "start_local_date_time содержит несуществующую дату или время.",
                error,
            )
        }
    }

    private fun ZonedDateTime.roundUpToMinute(): ZonedDateTime {
        val minutePrecision = withSecond(0).withNano(0)
        return if (second == 0 && nano == 0) {
            minutePrecision
        } else {
            minutePrecision.plusMinutes(1)
        }
    }

    private fun ScheduledAlarm.toAlarmClockIntent(): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, triggerAt.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, triggerAt.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun buildSuccessMessage(alarmCount: Int): String =
        if (alarmCount == 1) {
            "Запрос на создание системного будильника передан приложению часов."
        } else {
            "Приложению часов передано запросов на создание будильников: $alarmCount."
        }

    private fun buildPayloadJson(alarms: List<ScheduledAlarm>): String =
        JSONObject()
            .put("alarm_count", alarms.size)
            .put("interval_minutes", ALARM_INTERVAL_MINUTES)
            .put("creation_verified", false)
            .put(
                "alarms",
                JSONArray().also { array ->
                    alarms.forEach { alarm ->
                        array.put(
                            JSONObject()
                                .put("trigger_at", alarm.triggerAt.toString())
                                .put("hour", alarm.triggerAt.hour)
                                .put("minute", alarm.triggerAt.minute)
                                .put("label", alarm.label)
                        )
                    }
                }
            )
            .toString()

    private fun JSONObject.optIntByNames(vararg names: String): Int? =
        names.firstNotNullOfOrNull { name ->
            when (val value = opt(name)) {
                null,
                JSONObject.NULL -> null
                is Number -> value.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
        }

    private fun JSONObject.optLongByNames(vararg names: String): Long? =
        names.firstNotNullOfOrNull { name ->
            when (val value = opt(name)) {
                null,
                JSONObject.NULL -> null
                is Number -> value.toLong()
                is String -> value.trim().toLongOrNull()
                else -> null
            }
        }

    private fun JSONObject.optDoubleByNames(vararg names: String): Double? =
        names.firstNotNullOfOrNull { name ->
            when (val value = opt(name)) {
                null,
                JSONObject.NULL -> null
                is Number       -> value.toDouble()
                is String       -> value.trim().toDoubleOrNull()
                else            -> null
            }
        }

    private fun JSONObject.optStringByNames(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            optString(name).trim().takeIf { value -> value.isNotBlank() }
        }

    private data class ScheduledAlarm(
        val triggerAt: ZonedDateTime,
        val label:     String,
    )

    companion object {
        const val TOOL_NAME = "alarm_tool"

        private const val MAX_ALARM_COUNT = 50
        private const val DEFAULT_ALARM_COUNT = 1
        internal const val ALARM_INTERVAL_MINUTES = 5
        private const val ALARM_REQUEST_DISPATCH_DELAY_MILLIS = 200L
        private const val DEFAULT_ALARM_LABEL = "OpenKsenax Scheduled Alarm"
        private const val MINUTES_IN_DAY = 24 * 60
        private val LOCAL_TIME_PATTERN = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
        private val LOCAL_DATE_TIME_PATTERN =
            Regex("^(\\d{4})-(\\d{2})-(\\d{2})T([01]\\d|2[0-3]):([0-5]\\d)$")

        fun toolNames(): List<String> =
            listOf(TOOL_NAME)

        fun definitions(): List<KsenaxToolDefinition> =
            listOf(
                KsenaxToolDefinition(
                    name                          = TOOL_NAME,
                    description                   =
                        "Creates one or several persistent Android alarms. Multiple alarms are always spaced 5 minutes apart.",
                    arguments                     = KsenaxRawToolArgumentsObject(ALARM_ARGUMENT_SCHEMA),
                    riskLevel                     = KsenaxToolRiskLevel.MEDIUM,
                    requiresConfirmationByDefault = false,
                )
            )

        private val ALARM_ARGUMENT_SCHEMA: String =
            """
            {
              "type": "object",
              "properties": {
                "start_local_time": {
                  "type": "string",
                  "pattern": "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$",
                  "description": "Next occurrence of a local device clock time in 24-hour HH:mm format. Use for requests such as 'на 19:00'."
                },
                "start_local_date_time": {
                  "type": "string",
                  "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]$",
                  "description": "Exact local device date and time in yyyy-MM-dd'T'HH:mm format."
                },
                "start_delay_minutes": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Minutes from now to the first alarm. Use for relative requests."
                },
                "start_delay_hours": {
                  "type": "number",
                  "minimum": 0,
                  "description": "Hours from now to the first alarm. Use for phrases like 'через 9 часов'."
                },
                "count": {
                  "type": "integer",
                  "minimum": 1,
                  "maximum": 50,
                  "default": 1,
                  "description": "How many system alarms to create in one tool call."
                },
                "label": {
                  "type": "string",
                  "description": "Short alarm label or reason."
                }
              },
              "oneOf": [
                { "required": ["start_local_time"] },
                { "required": ["start_local_date_time"] },
                { "required": ["start_delay_minutes"] },
                { "required": ["start_delay_hours"] }
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
