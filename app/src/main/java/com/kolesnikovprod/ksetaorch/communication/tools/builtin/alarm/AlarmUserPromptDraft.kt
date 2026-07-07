package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionInputDraft
import java.util.Locale

/**
 * Локальный нормализатор русского UP для быстрых alarm OneShot-вызовов.
 *
 * FunctionGemma остаётся compiler-ом function-call, но числа и единицы времени
 * мы извлекаем сами: это дешевле, стабильнее и не зависит от того, как 270M
 * в конкретном запуске поняла "5 будильников через 10 часов".
 */
internal object AlarmUserPromptDraft {

    fun build(userMessage: String): KsenaxActionInputDraft? {
        val text = AlarmKeywordText.normalize(userMessage)
        if (!AlarmKeywordText.looksLikeAlarm(text)) return null

        if (text.isClearRequest()) {
            return KsenaxActionInputDraft(
                preferredActionName = AlarmToolOneShot.ClearAll.codeName,
                instruction = userMessage,
            )
        }

        val count = text.extractAlarmCount()
        text.extractAmountBeforeUnit(MINUTE_UNIT_PREFIXES)?.let { minutes ->
            return KsenaxActionInputDraft(
                preferredActionName = AlarmToolOneShot.AfterMinutes.codeName,
                plannerInputJson = buildJson(
                    "minutes" to minutes,
                    "count" to count,
                ),
                instruction = "Create Android alarms after $minutes minutes.",
            )
        }

        text.extractAmountBeforeUnit(HOUR_UNIT_PREFIXES)?.let { hours ->
            return KsenaxActionInputDraft(
                preferredActionName = AlarmToolOneShot.AfterHours.codeName,
                plannerInputJson = buildJson(
                    "hours" to hours,
                    "count" to count,
                ),
                instruction = "Create Android alarms after $hours hours.",
            )
        }

        text.extractClockTime()?.let { time ->
            return KsenaxActionInputDraft(
                preferredActionName = AlarmToolOneShot.AtTime.codeName,
                plannerInputJson = buildJson(
                    "time" to time,
                    "count" to count,
                ),
                instruction = "Create Android alarms at $time.",
            )
        }

        return null
    }

    private fun String.isClearRequest(): Boolean =
        ("очист" in this || "удал" in this || "снес" in this || "убер" in this) &&
            ("будильник" in this || "будильники" in this || "будильников" in this)

    private fun String.extractAlarmCount(): Int? {
        val tokens = tokenize()
        val alarmIndex = tokens.indexOfFirst { token -> token.startsWith("будильник") }
        if (alarmIndex <= 0) return null
        return tokens[alarmIndex - 1].toRussianNumberOrNull()
            ?.takeIf { count -> count in 1..50 }
    }

    private fun String.extractAmountBeforeUnit(unitPrefixes: Set<String>): Int? {
        val tokens = tokenize()
        val unitIndex = tokens.indexOfFirst { token ->
            unitPrefixes.any(token::startsWith)
        }
        if (unitIndex <= 0) return null
        return tokens[unitIndex - 1].toRussianNumberOrNull()
            ?.takeIf { amount -> amount >= 0 }
    }

    private fun String.extractClockTime(): String? {
        CLOCK_TIME_PATTERN.find(this)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                return "%02d:%02d".format(hour, minute)
            }
        }

        val tokens = tokenize()
        tokens.forEachIndexed { index, token ->
            if (token == "на" || token == "в") {
                val hour = tokens.getOrNull(index + 1)
                    ?.toRussianNumberOrNull()
                    ?.takeIf { candidate -> candidate in 0..23 }
                val unit = tokens.getOrNull(index + 2).orEmpty()
                if (hour != null && HOUR_UNIT_PREFIXES.any(unit::startsWith)) {
                    return "%02d:00".format(hour)
                }
            }
        }
        return null
    }

    private fun String.tokenize(): List<String> =
        lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}:]+"""), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

    private fun String.toRussianNumberOrNull(): Int? =
        toIntOrNull() ?: RUSSIAN_NUMBERS[this]

    private fun buildJson(vararg pairs: Pair<String, Any?>): String =
        pairs
            .filter { (_, value) -> value != null }
            .joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
            ) { (key, value) ->
                val encodedValue = when (value) {
                    is Number -> value.toString()
                    else -> value.toString().toJsonString()
                }
                key.toJsonString() + ":" + encodedValue
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

    private val MINUTE_UNIT_PREFIXES: Set<String> =
        setOf("минут", "мин")

    private val HOUR_UNIT_PREFIXES: Set<String> =
        setOf("час", "ч")

    private val RUSSIAN_NUMBERS: Map<String, Int> =
        mapOf(
            "ноль" to 0,
            "один" to 1,
            "одна" to 1,
            "одно" to 1,
            "два" to 2,
            "две" to 2,
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
        )
}
