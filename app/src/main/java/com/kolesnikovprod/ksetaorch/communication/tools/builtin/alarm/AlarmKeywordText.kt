package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import java.util.Locale

internal object AlarmKeywordText {

    fun normalize(userMessage: String): String =
        userMessage
            .lowercase(Locale.ROOT)
            .normalizeWhitespace()
            .trim()

    fun looksLikeAlarm(text: String): Boolean =
        "будильник" in text ||
            "будильника" in text ||
            "будильников" in text ||
            "разбуди" in text ||
            "разбудить" in text ||
            ("очист" in text && "будильник" in text) ||
            ("удал" in text && "будильник" in text)

    fun mentionsClockTime(text: String): Boolean {
        val colonIndex = text.indexOf(':')
        if (colonIndex <= 0 || colonIndex >= text.lastIndex) return false

        val hour = text.readDigitsBefore(colonIndex, maxDigits = 2)
        val minute = text.readDigitsAfter(colonIndex, maxDigits = 2)
        return hour != null && minute != null && hour in 0..23 && minute in 0..59
    }

    fun mentionsDate(text: String): Boolean =
        "завтра" in text ||
            "послезавтра" in text ||
            "сегодня" in text ||
            "дата" in text ||
            text.any { symbol -> symbol == '-' || symbol == '.' }

    fun mentionsHours(text: String): Boolean =
        "через" in text &&
            (
                " час" in text ||
                    " часа" in text ||
                    " часов" in text ||
                    " ч " in text
                )

    fun mentionsMinutes(text: String): Boolean =
        "через" in text &&
            (
                " минут" in text ||
                    " минуты" in text ||
                    " минуту" in text ||
                    " мин" in text
                )

    private fun String.normalizeWhitespace(): String =
        buildString(length) {
            var previousWasWhitespace = false
            this@normalizeWhitespace.forEach { symbol ->
                if (symbol.isWhitespace()) {
                    if (!previousWasWhitespace) {
                        append(' ')
                        previousWasWhitespace = true
                    }
                } else {
                    append(symbol)
                    previousWasWhitespace = false
                }
            }
        }

    private fun String.readDigitsBefore(index: Int, maxDigits: Int): Int? {
        var start = index - 1
        var count = 0
        while (start >= 0 && this[start].isDigit() && count < maxDigits) {
            start--
            count++
        }
        if (count == 0) return null
        return substring(start + 1, index).toIntOrNull()
    }

    private fun String.readDigitsAfter(index: Int, maxDigits: Int): Int? {
        var end = index + 1
        var count = 0
        while (end < length && this[end].isDigit() && count < maxDigits) {
            end++
            count++
        }
        if (count != 2) return null
        return substring(index + 1, end).toIntOrNull()
    }
}
