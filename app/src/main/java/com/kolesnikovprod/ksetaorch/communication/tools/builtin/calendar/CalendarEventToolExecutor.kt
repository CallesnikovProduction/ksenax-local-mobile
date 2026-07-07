package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolDefinition
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Исполнитель создания календарных событий через системное приложение календаря.
 *
 * Executor не пишет напрямую в CalendarProvider. Он подготавливает событие и
 * открывает Android Calendar insert screen через [CalendarContract.Events.CONTENT_URI].
 * Так приложение не требует прямого доступа к календарной базе, а пользователь
 * сохраняет событие уже в привычном системном интерфейсе.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class CalendarEventToolExecutor(context: Context) : KsenaxToolExecutor {

    private val appContext: Context = context.applicationContext
    private val zoneId: ZoneId = ZoneId.systemDefault()

    override suspend fun execute(
        call: KsenaxToolCall,
    ): KsenaxToolResult = withContext(Dispatchers.Default) {
        if (call.name != TOOL_NAME) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "CalendarEventToolExecutor cannot execute tool: ${call.name}.",
                errorCode = "INVALID_TOOL",
            )
        }

        val event = parseCalendarEvent(
            arguments = call.arguments.JSONtoString(),
            now       = ZonedDateTime.now(zoneId),
        ).getOrElse { error ->
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = error.message ?: "Неверные аргументы календарного события.",
                errorCode = "INVALID_ARGUMENTS",
            )
        }

        try {
            appContext.startActivity(event.toCalendarInsertIntent())
        } catch (_: ActivityNotFoundException) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Не удалось открыть системное приложение календаря.",
                errorCode = "CALENDAR_APP_NOT_FOUND",
            )
        } catch (_: SecurityException) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Android не разрешил открыть создание календарного события.",
                errorCode = "CALENDAR_INSERT_DENIED",
            )
        } catch (error: RuntimeException) {
            return@withContext KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = error.message ?: "Не удалось подготовить календарное событие.",
                errorCode = "CALENDAR_INSERT_FAILED",
            )
        }

        KsenaxToolResult.Success(
            callId      = call.id,
            toolName    = call.name,
            message     = "Календарное событие подготовлено в системном календаре.",
            payloadJson = buildPayloadJson(event),
        )
    }

    private fun parseCalendarEvent(
        arguments: String,
        now:       ZonedDateTime,
    ): Result<CalendarEventDraft> =
        runCatching {
            val json = try {
                JSONObject(arguments)
            } catch (_: JSONException) {
                throw IllegalArgumentException("Arguments for $TOOL_NAME must be a JSON object.")
            }

            val title = json.requiredStringByNames("title", "name")
                .take(MAX_TITLE_LENGTH)

            val allDay = json.optBooleanByNames("all_day", "allDay") ?: false
            val rawStartAt = json.parseStartAt(now)
            val startAt = if (allDay) {
                rawStartAt.toLocalDate().atStartOfDay(zoneId)
            } else {
                rawStartAt.withSecond(0).withNano(0)
            }

            if (allDay) {
                require(!startAt.toLocalDate().isBefore(now.toLocalDate())) {
                    "Дата календарного события не должна быть в прошлом."
                }
            } else {
                require(!startAt.isBefore(now.minusMinutes(1))) {
                    "Время начала события не должно быть в прошлом."
                }
            }

            val endAt = json.parseEndAt(
                startAt = startAt,
                allDay  = allDay,
            )

            require(endAt.isAfter(startAt)) {
                "Время окончания события должно быть позже времени начала."
            }
            require(Duration.between(startAt, endAt).toMinutes() <= MAX_EVENT_DURATION_MINUTES) {
                "Событие не должно длиться дольше 366 дней."
            }

            CalendarEventDraft(
                title        = title,
                startAt      = startAt,
                endAt        = endAt,
                allDay       = allDay,
                location     = json.optStringByNames("location", "place"),
                description  = json.optStringByNames("description", "notes", "details"),
                attendees    = json.optAttendees(),
                availability = CalendarEventAvailability.fromJson(json),
            )
        }

    private fun JSONObject.parseStartAt(now: ZonedDateTime): ZonedDateTime {
        optLongByNames(
            "start_at_millis",
            "startAtMillis",
            "starts_at_millis",
            "startsAtMillis",
        )?.let { timestamp ->
            require(timestamp > 0L) {
                "start_at_millis должен быть положительным Unix timestamp в миллисекундах."
            }
            return Instant.ofEpochMilli(timestamp).atZone(zoneId)
        }

        optLongByNames(
            "start_delay_minutes",
            "startDelayMinutes",
            "delay_minutes",
            "delayMinutes",
        )?.let { delayMinutes ->
            require(delayMinutes >= 0L) {
                "start_delay_minutes не может быть отрицательным."
            }
            return now.plusMinutes(delayMinutes)
        }

        optDoubleByNames(
            "start_delay_hours",
            "startDelayHours",
            "delay_hours",
            "delayHours",
        )?.let { delayHours ->
            require(delayHours >= 0.0) {
                "start_delay_hours не может быть отрицательным."
            }
            return now.plusMinutes((delayHours * 60).toLong())
        }

        throw IllegalArgumentException(
            "Нужно передать start_at_millis, start_delay_minutes или start_delay_hours."
        )
    }

    private fun JSONObject.parseEndAt(
        startAt: ZonedDateTime,
        allDay:  Boolean,
    ): ZonedDateTime {
        optLongByNames(
            "end_at_millis",
            "endAtMillis",
            "ends_at_millis",
            "endsAtMillis",
        )?.let { timestamp ->
            require(timestamp > 0L) {
                "end_at_millis должен быть положительным Unix timestamp в миллисекундах."
            }
            val endAt = Instant.ofEpochMilli(timestamp).atZone(zoneId)
            return if (allDay) {
                endAt.toLocalDate().atStartOfDay(zoneId)
            } else {
                endAt.withSecond(0).withNano(0)
            }
        }

        val fallbackDuration = if (allDay) {
            MIN_ALL_DAY_DURATION_MINUTES
        } else {
            DEFAULT_EVENT_DURATION_MINUTES
        }
        val durationMinutes = optLongByNames(
            "duration_minutes",
            "durationMinutes",
        ) ?: fallbackDuration

        require(durationMinutes > 0L) {
            "duration_minutes должен быть больше нуля."
        }

        return startAt.plusMinutes(durationMinutes)
    }

    private fun CalendarEventDraft.toCalendarInsertIntent(): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startAt.toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endAt.toInstant().toEpochMilli())
            .putExtra(CalendarContract.Events.ALL_DAY, allDay)
            .putExtra(CalendarContract.Events.AVAILABILITY, availability.androidValue)
            .also { intent ->
                location?.let { value ->
                    intent.putExtra(CalendarContract.Events.EVENT_LOCATION, value)
                }
                description?.let { value ->
                    intent.putExtra(CalendarContract.Events.DESCRIPTION, value)
                }
                if (attendees.isNotEmpty()) {
                    intent.putExtra(Intent.EXTRA_EMAIL, attendees.joinToString(","))
                }
            }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun buildPayloadJson(event: CalendarEventDraft): String =
        JSONObject()
            .put("title", event.title)
            .put("start_at", event.startAt.toString())
            .put("end_at", event.endAt.toString())
            .put("all_day", event.allDay)
            .put("location", event.location ?: JSONObject.NULL)
            .put("description", event.description ?: JSONObject.NULL)
            .put("availability", event.availability.jsonValue)
            .put(
                "attendees",
                JSONArray().also { array ->
                    event.attendees.forEach { attendee ->
                        array.put(attendee)
                    }
                }
            )
            .toString()

    private fun JSONObject.requiredStringByNames(vararg names: String): String =
        names.firstNotNullOfOrNull { name ->
            optStringByNames(name)
        } ?: throw IllegalArgumentException(
            "Нужно передать непустое поле ${names.first()}."
        )

    private fun JSONObject.optStringByNames(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            when (val value = opt(name)) {
                null,
                JSONObject.NULL -> null
                is String -> value.trim().takeIf { text -> text.isNotBlank() }
                else -> value.toString().trim().takeIf { text -> text.isNotBlank() }
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
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
        }

    private fun JSONObject.optBooleanByNames(vararg names: String): Boolean? =
        names.firstNotNullOfOrNull { name ->
            when (val value = opt(name)) {
                null,
                JSONObject.NULL -> null
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> when (value.trim().lowercase(Locale.ROOT)) {
                    "true", "yes", "1", "on", "enabled" -> true
                    "false", "no", "0", "off", "disabled" -> false
                    else -> null
                }
                else -> null
            }
        }

    private fun JSONObject.optAttendees(): List<String> {
        val rawAttendees = opt("attendees") ?: opt("emails") ?: return emptyList()
        val attendees = when (rawAttendees) {
            is JSONArray -> (0 until rawAttendees.length())
                .mapNotNull { index ->
                    rawAttendees.optString(index).trim().takeIf { value -> value.isNotBlank() }
                }
            is String -> rawAttendees
                .split(",", ";")
                .mapNotNull { value -> value.trim().takeIf { text -> text.isNotBlank() } }
            else -> emptyList()
        }

        require(attendees.size <= MAX_ATTENDEE_COUNT) {
            "Нельзя добавить больше $MAX_ATTENDEE_COUNT участников."
        }

        return attendees
    }

    private data class CalendarEventDraft(
        val title:        String,
        val startAt:      ZonedDateTime,
        val endAt:        ZonedDateTime,
        val allDay:       Boolean,
        val location:     String?,
        val description:  String?,
        val attendees:    List<String>,
        val availability: CalendarEventAvailability,
    )

    private enum class CalendarEventAvailability(
        val jsonValue:    String,
        val androidValue: Int,
    ) {
        Busy(
            jsonValue    = "busy",
            androidValue = CalendarContract.Events.AVAILABILITY_BUSY,
        ),
        Free(
            jsonValue    = "free",
            androidValue = CalendarContract.Events.AVAILABILITY_FREE,
        ),
        Tentative(
            jsonValue    = "tentative",
            androidValue = CalendarContract.Events.AVAILABILITY_TENTATIVE,
        );

        companion object {
            fun fromJson(json: JSONObject): CalendarEventAvailability {
                val rawValue = json.optString("availability", "busy")
                    .trim()
                    .lowercase(Locale.ROOT)

                return entries.firstOrNull { availability ->
                    availability.jsonValue == rawValue
                } ?: Busy
            }
        }
    }

    companion object {
        const val TOOL_NAME = "calendar_event_tool"

        private const val DEFAULT_EVENT_DURATION_MINUTES = 60L
        private const val MIN_ALL_DAY_DURATION_MINUTES = 24L * 60L
        private const val MAX_EVENT_DURATION_MINUTES = 366L * 24L * 60L
        private const val MAX_TITLE_LENGTH = 140
        private const val MAX_ATTENDEE_COUNT = 50

        fun toolNames(): List<String> =
            listOf(TOOL_NAME)

        fun definitions(): List<KsenaxToolDefinition> =
            listOf(
                KsenaxToolDefinition(
                    name                          = TOOL_NAME,
                    description                   = "Prepares a new Android calendar event in the system Calendar app.",
                    arguments                     = KsenaxRawToolArgumentsObject(CALENDAR_EVENT_ARGUMENT_SCHEMA),
                    riskLevel                     = KsenaxToolRiskLevel.MEDIUM,
                    requiresConfirmationByDefault = false,
                )
            )

        private val CALENDAR_EVENT_ARGUMENT_SCHEMA: String =
            """
            {
              "type": "object",
              "properties": {
                "title": {
                  "type": "string",
                  "description": "Short calendar event title."
                },
                "start_at_millis": {
                  "type": "integer",
                  "description": "Unix timestamp in milliseconds for the event start. Use current prompt time as the reference."
                },
                "start_delay_minutes": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Minutes from now to the event start. Use for relative requests."
                },
                "start_delay_hours": {
                  "type": "number",
                  "minimum": 0,
                  "description": "Hours from now to the event start. Use for phrases like 'через 2 часа'."
                },
                "end_at_millis": {
                  "type": "integer",
                  "description": "Unix timestamp in milliseconds for the event end."
                },
                "duration_minutes": {
                  "type": "integer",
                  "minimum": 1,
                  "maximum": 527040,
                  "description": "Event duration in minutes. Defaults to 60 minutes, or one day for all-day events."
                },
                "all_day": {
                  "type": "boolean",
                  "description": "true for an all-day event."
                },
                "location": {
                  "type": "string",
                  "description": "Optional event place."
                },
                "description": {
                  "type": "string",
                  "description": "Optional event notes."
                },
                "attendees": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  },
                  "description": "Optional attendee email addresses."
                },
                "availability": {
                  "type": "string",
                  "enum": ["busy", "free", "tentative"],
                  "description": "Calendar availability. Default is busy."
                }
              },
              "required": ["title"],
              "anyOf": [
                { "required": ["start_at_millis"] },
                { "required": ["start_delay_minutes"] },
                { "required": ["start_delay_hours"] }
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
