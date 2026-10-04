package com.kolesnikovprod.ksetaorch.communication.tools.builtin.scheduling

import java.time.*
import java.util.Locale

/**
 * Детерминированные дата, время и числа из русского текста. Не выбирает инструмент.
 * Неясные выражения возвращают null, не произвольное время.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal object LocalScheduleParser {
    fun normalize(text: String): String = text.lowercase(Locale.ROOT).replace('ё', 'е')
    fun number(text: String): Int? = text.toIntOrNull() ?: numbers[normalize(text)]

    private fun numberAt(words: List<String>, index: Int): Int? {
        val first = words.getOrNull(index)?.let { number(it) ?: ordinals[it] } ?: return null
        val second = words.getOrNull(index + 1)?.let { number(it) ?: ordinals[it] }
        return if (first in 20..90 && first % 10 == 0 && second != null && second in 1..9) first + second else first
    }

    private fun numberBefore(words: List<String>, index: Int): Int? {
        val last = words.getOrNull(index - 1)?.let { number(it) ?: ordinals[it] } ?: return null
        val tens = words.getOrNull(index - 2)?.let(::number)
        return if (last in 1..9 && tens != null && tens in 20..90 && tens % 10 == 0) tens + last else last
    }

    fun clock(text: String): LocalTime? {
        val normalized = normalize(text)
        Regex("""\b(\d{1,2})[:.](\d{2})\b""").find(normalized)?.let {
            return runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
        }
        val words = normalized.split(Regex("""[^\p{L}\p{N}]+""")).filter(String::isNotBlank)
        words.forEachIndexed { index, word ->
            if (word in setOf("половину", "половине", "половина", "пол")) {
                val next = numberAt(words, index + 1)
                if (next != null && next in 1..12) return adjustedClock(next - 1, 30, normalized)
            }
            if (word in setOf("в", "на") || word.startsWith("час")) {
                val hour = if (word.startsWith("час")) numberBefore(words, index) else numberAt(words, index + 1)
                // 'на 8 июля' является датой, не временем.
                val numberLength = if (hour != null && words.getOrNull(index + 1)?.let(::number)?.let { it != hour } == true) 2 else 1
                val suffix = words.getOrNull(index + 1 + numberLength)
                if (suffix in months || suffix?.let(::number)?.let { it >= 1900 } == true) return@forEachIndexed
                if (hour != null && hour in 0..23) return adjustedClock(hour, 0, normalized)
            }
            if (word.startsWith("вечер") || word.startsWith("утр")) {
                val hour = numberBefore(words, index)
                if (hour != null && hour in 0..23) return adjustedClock(hour, 0, normalized)
            }
        }
        return null
    }

    private fun adjustedClock(hour: Int, minute: Int, text: String): LocalTime =
        LocalTime.of(if (Regex("вечер|дня").containsMatchIn(text) && hour in 1..11) hour + 12 else hour, minute)

    fun date(text: String, today: LocalDate): LocalDate? {
        val normalized = normalize(text)
        if ("послезавтра" in normalized) return today.plusDays(2)
        if ("завтра" in normalized) return today.plusDays(1)
        if ("сегодня" in normalized) return today
        weekdays.entries.firstOrNull { normalized.contains(it.key) }?.let { (_, day) ->
            val offset = (day.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(if (offset == 0) 7 else offset.toLong())
        }
        Regex("""\b(\d{4}-\d{2}-\d{2})\b""").find(normalized)?.let {
            return runCatching { LocalDate.parse(it.value) }.getOrNull()
        }
        val words = normalized.split(Regex("""[^\p{L}\p{N}]+""")).filter(String::isNotBlank)
        val monthIndex = words.indexOfFirst { it in months }
        if (monthIndex <= 0) return null
        val day = numberBefore(words, monthIndex) ?: return null
        val year = words.getOrNull(monthIndex + 1)?.toIntOrNull()?.takeIf { it >= 1900 }
        return runCatching {
            val candidate = LocalDate.of(year ?: today.year, months.getValue(words[monthIndex]), day)
            if (year == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
        }.getOrNull()
    }

    fun amount(text: String, unit: String): Int? {
        val normalized = normalize(text)
        val match = Regex("""через\s+([\p{L}\d]+(?:\s+[\p{L}]+)?)\s+(?:$unit)""").find(normalized) ?: return null
        val words = match.groupValues[1].split(Regex("\\s+"))
        val value = numberAt(words, 0) ?: return null
        // Не принимаем частично разобранное выражение вроде 'два с небольшим'.
        return value.takeIf { words.size == 1 || (number(words[0]) in 20..90 && number(words[1]) in 1..9) }
    }

    fun alarmCount(text: String): Int? {
        val words = normalize(text).split(Regex("""[^\p{L}\p{N}]+""")).filter(String::isNotBlank)
        val index = words.indexOfFirst { it.startsWith("будильник") }
        if (index < 0) return null
        numberBefore(words, index)?.let { return it }
        numberAt(words, index + 1)?.let { return it }
        words.indexOfFirst { it.startsWith("штук") }.takeIf { it >= 0 }?.let { numberBefore(words, it)?.let { count -> return count } }
        words.indexOfFirst { it == "всего" }.takeIf { it >= 0 }?.let { numberAt(words, it + 1)?.let { count -> return count } }
        return null
    }

    private val numbers = listOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать", "двадцать").withIndex().associate { it.value to it.index } + mapOf("одна" to 1, "одно" to 1, "две" to 2, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90, "сто" to 100)
    private val ordinals = listOf("перв", "втор", "трет", "четверт", "пят", "шест", "седьм", "восьм", "девят", "десят", "одиннадцат", "двенадцат", "тринадцат", "четырнадцат", "пятнадцат", "шестнадцат", "семнадцат", "восемнадцат", "девятнадцат", "двадцат").flatMapIndexed { index, stem -> listOf("ое", "ого").map { stem + it to index + 1 } }.toMap() + mapOf("третье" to 3, "третьего" to 3)
    private val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря").withIndex().associate { it.value to it.index + 1 }
    private val weekdays = mapOf("понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "среду" to DayOfWeek.WEDNESDAY, "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY, "воскресень" to DayOfWeek.SUNDAY)
}
