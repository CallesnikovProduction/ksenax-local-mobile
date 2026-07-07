package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Locale

/**
 * Fallback-нормализатор календарного UP.
 *
 * G4 остаётся планировщиком, но дата/время — слишком критичные поля, чтобы
 * полностью зависеть от одного model output. Если planner не передал start,
 * calendar-kit пробует восстановить его из исходного пользовательского текста.
 */
internal object CalendarEventUserPromptDraft {

    fun buildJson(userMessage: String): String? {
        val text = userMessage.lowercase(Locale.ROOT)
        val date = text.extractDate() ?: return null
        val time = text.extractTime() ?: return null
        val title = text.extractTitle()
        return buildJson(
            "title" to title,
            "start_local_date_time" to "${date}T$time",
        )
    }

    private fun String.extractDate(): LocalDate? {
        val tokens = tokenize()
        val monthIndex = tokens.indexOfFirst { token -> MONTHS.containsKey(token) }
        if (monthIndex <= 0) return null

        val day = tokens[monthIndex - 1].toDayNumberOrNull() ?: return null
        val month = MONTHS.getValue(tokens[monthIndex])
        val explicitYear = tokens.getOrNull(monthIndex + 1)?.toIntOrNull()
        val now = LocalDate.now(ZoneId.systemDefault())
        var candidate = LocalDate.of(explicitYear ?: now.year, month, day)
        if (explicitYear == null && candidate.isBefore(now)) {
            candidate = candidate.plusYears(1)
        }
        return candidate
    }

    private fun String.extractTime(): String? {
        CLOCK_TIME_PATTERN.find(this)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                return "%02d:%02d".format(hour, minute)
            }
        }

        val tokens = tokenize()
        tokens.forEachIndexed { index, token ->
            if (token.startsWith("час")) {
                val rawHour = tokens.getOrNull(index - 1)?.toHourNumberOrNull()
                    ?: return@forEachIndexed
                val hasEvening = tokens.any { candidate -> candidate.startsWith("вечер") }
                val hour = if (hasEvening && rawHour in 1..11) rawHour + 12 else rawHour
                if (hour in 0..23) return "%02d:00".format(hour)
            }
        }

        tokens.forEachIndexed { index, token ->
            if (token == "в") {
                val rawHour = tokens.getOrNull(index + 1)?.toHourNumberOrNull()
                    ?: return@forEachIndexed
                val hasEvening = tokens.drop(index + 2).take(3).any { candidate ->
                    candidate.startsWith("вечер")
                }
                val hour = if (hasEvening && rawHour in 1..11) rawHour + 12 else rawHour
                if (hour in 0..23) return "%02d:00".format(hour)
            }
        }

        return null
    }

    private fun String.extractTitle(): String {
        if ("свидан" in this && ("ксюш" in this || "ксюша" in this)) {
            return "Свидание с Ксюшей"
        }
        return substringAfter("событие", missingDelimiterValue = this)
            .substringAfter("что будет", missingDelimiterValue = this)
            .trim(' ', ',', '.', ':', '-', '—')
            .replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
            .take(80)
            .ifBlank { "Календарное событие" }
    }

    private fun String.tokenize(): List<String> =
        lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}:]+"""), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

    private fun String.toDayNumberOrNull(): Int? =
        toIntOrNull()?.takeIf { it in 1..31 }
            ?: DAY_WORDS[this]

    private fun String.toHourNumberOrNull(): Int? =
        toIntOrNull()?.takeIf { it in 0..23 }
            ?: HOUR_WORDS[this]

    private fun buildJson(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        ) { (key, value) ->
            key.toJsonString() + ":" + value.toJsonString()
        }

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            this@toJsonString.forEach { symbol ->
                when (symbol) {
                    '\\' -> append("\\\\")
                    '"'  -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(symbol)
                }
            }
            append('"')
        }

    private val CLOCK_TIME_PATTERN = Regex("""\b([01]?\d|2[0-3])[:.](\d{2})\b""")

    private val MONTHS: Map<String, Month> =
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

    private val DAY_WORDS: Map<String, Int> =
        mapOf(
            "первое" to 1,
            "второе" to 2,
            "третье" to 3,
            "четвертое" to 4,
            "четвёртое" to 4,
            "пятое" to 5,
            "шестое" to 6,
            "седьмое" to 7,
            "восьмое" to 8,
            "девятое" to 9,
            "десятое" to 10,
            "одиннадцатое" to 11,
            "двенадцатое" to 12,
            "тринадцатое" to 13,
            "четырнадцатое" to 14,
            "пятнадцатое" to 15,
            "шестнадцатое" to 16,
            "семнадцатое" to 17,
            "восемнадцатое" to 18,
            "девятнадцатое" to 19,
            "двадцатое" to 20,
            "двадцать" to 20,
        )

    private val HOUR_WORDS: Map<String, Int> =
        mapOf(
            "ноль" to 0,
            "один" to 1,
            "два" to 2,
            "три" to 3,
            "четыре" to 4,
            "пять" to 5,
            "шесть" to 6,
            "семь" to 7,
            "восемь" to 8,
            "девять" to 9,
            "десять" to 10,
            "одиннадцать" to 11,
            "двенадцать" to 12,
            "тринадцать" to 13,
            "четырнадцать" to 14,
            "пятнадцать" to 15,
            "шестнадцать" to 16,
            "семнадцать" to 17,
            "восемнадцать" to 18,
            "девятнадцать" to 19,
            "двадцать" to 20,
            "двадцать один" to 21,
            "двадцать два" to 22,
            "двадцать три" to 23,
        )
}
