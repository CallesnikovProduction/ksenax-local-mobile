package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject

class CalendarEventOneShotExecutor(
    private val calendarEventToolExecutor: CalendarEventToolExecutor,
) : KsenaxToolExecutor {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult {
        if (call.name != CalendarEventOneShot.codeName) {
            return KsenaxToolResult.Failure(
                callId = call.id,
                toolName = call.name,
                reason = "CalendarEventOneShotExecutor cannot execute tool: ${call.name}.",
                errorCode = "INVALID_TOOL",
            )
        }
        val legacyArguments = runCatching {
            translateArguments(JSONObject(call.arguments.JSONtoString()))
        }.getOrElse { error ->
            return KsenaxToolResult.Failure(
                callId = call.id,
                toolName = call.name,
                reason = error.message ?: "Неверные one-shot аргументы календаря.",
                errorCode = "INVALID_ARGUMENTS",
            )
        }
        val legacyCall = call.copy(
            name = CalendarEventToolExecutor.TOOL_NAME,
            arguments = KsenaxRawToolArgumentsObject(legacyArguments.toString()),
        )
        return calendarEventToolExecutor.execute(legacyCall).withToolName(call.name)
    }

    private fun translateArguments(input: JSONObject): JSONObject {
        val output = JSONObject(input.toString())
        if (output.hasAnyStartField()) return output

        val dateTime = input.stringByNames(
            "start_local_date_time",
            "startLocalDateTime",
            "local_date_time",
            "date_time",
            "datetime",
        )
        if (dateTime != null) {
            output.put("start_at_millis", dateTime.parseLocalDateTime().atZone(zoneId).toInstant().toEpochMilli())
            return output
        }

        val date = input.stringByNames(
            "start_local_date",
            "startLocalDate",
            "local_date",
            "date",
            "day",
        )
        val time = input.stringByNames(
            "start_local_time",
            "startLocalTime",
            "local_time",
            "time",
            "hour",
        )
        if (date != null && time != null) {
            val localDate = date.parseLocalDate()
            val localTime = time.parseLocalTime()
            output.put(
                "start_at_millis",
                LocalDateTime.of(localDate, localTime).atZone(zoneId).toInstant().toEpochMilli(),
            )
            return output
        }

        return output
    }

    private fun JSONObject.hasAnyStartField(): Boolean =
        listOf(
            "start_at_millis",
            "startAtMillis",
            "start_delay_minutes",
            "startDelayMinutes",
            "start_delay_hours",
            "startDelayHours",
        ).any(::has)

    private fun JSONObject.stringByNames(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            optString(name)
                .trim()
                .takeIf(String::isNotBlank)
                ?.takeUnless { value -> value == "null" }
        }

    private fun String.parseLocalDateTime(): LocalDateTime {
        val normalized = trim()
        return runCatching { LocalDateTime.parse(normalized) }
            .getOrElse {
                val date = normalized.substringBefore('T').substringBefore(' ')
                val time = normalized.substringAfter('T', "").ifBlank {
                    normalized.substringAfter(' ', "")
                }
                LocalDateTime.of(date.parseLocalDate(), time.parseLocalTime())
            }
    }

    private fun String.parseLocalDate(): LocalDate {
        val normalized = trim().lowercase(Locale.ROOT)
        runCatching { return LocalDate.parse(normalized) }

        if (normalized == "сегодня") return LocalDate.now(zoneId)
        if (normalized == "завтра") return LocalDate.now(zoneId).plusDays(1)
        if (normalized == "послезавтра") return LocalDate.now(zoneId).plusDays(2)

        val parts = normalized
            .replace(".", " ")
            .replace(",", " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (parts.size >= 2) {
            val day = parts[0].toIntOrNull()
            val month = RUSSIAN_MONTHS[parts[1]]
            if (day != null && month != null) {
                val now = LocalDate.now(zoneId)
                val explicitYear = parts.getOrNull(2)?.toIntOrNull()
                var candidate = LocalDate.of(explicitYear ?: now.year, month, day)
                if (explicitYear == null && candidate.isBefore(now)) {
                    candidate = candidate.plusYears(1)
                }
                return candidate
            }
        }

        throw IllegalArgumentException("Дата календарного события должна быть yyyy-MM-dd или понятной русской датой.")
    }

    private fun String.parseLocalTime(): LocalTime {
        val normalized = trim()
            .lowercase(Locale.ROOT)
            .replace("часов", "")
            .replace("часа", "")
            .replace("час", "")
            .replace("ч.", "")
            .replace("ч", "")
            .trim()
        if (':' in normalized) {
            val hour = normalized.substringBefore(':').trim().toIntOrNull()
            val minute = normalized.substringAfter(':').trim().toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                return LocalTime.of(hour, minute)
            }
        }
        normalized.toIntOrNull()?.let { hour ->
            if (hour in 0..23) return LocalTime.of(hour, 0)
        }
        throw IllegalArgumentException("Время календарного события должно быть HH:mm или валидным часом.")
    }

    private fun KsenaxToolResult.withToolName(toolName: String): KsenaxToolResult =
        when (this) {
            is KsenaxToolResult.Success -> copy(toolName = toolName)
            is KsenaxToolResult.Failure -> copy(toolName = toolName)
        }

    private companion object {
        val RUSSIAN_MONTHS: Map<String, Month> =
            mapOf(
                "января" to Month.JANUARY,
                "февраля" to Month.FEBRUARY,
                "марта" to Month.MARCH,
                "апреля" to Month.APRIL,
                "мая" to Month.MAY,
                "июня" to Month.JUNE,
                "июля" to Month.JULY,
                "августа" to Month.AUGUST,
                "сентября" to Month.SEPTEMBER,
                "октября" to Month.OCTOBER,
                "ноября" to Month.NOVEMBER,
                "декабря" to Month.DECEMBER,
            )
    }
}
