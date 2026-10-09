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

    /** Полное количественное числительное, без частичного чтения соседних слов.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun quantity(text: String): Int? {
        val words = normalize(text).trim().split(Regex("\\s+"))
        if (words.size == 1) return number(words.single())
        if (words.size != 2) return null
        val tens = number(words[0]) ?: return null
        val units = number(words[1]) ?: return null
        return if (tens in 20..90 && tens % 10 == 0 && units in 1..9) tens + units else null
    }

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
        val candidates = mutableSetOf<LocalTime>()
        var invalid = false
        Regex("""\b(\d{1,2})[:.](\d{2})\b""").findAll(normalized).forEach {
            val candidate = runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
            if (candidate == null) invalid = true else candidates += candidate
        }
        // Числовое HH:mm остаётся одним токеном, а не вторым временем HH:00.
        val words = Regex("""\d{1,2}[:.]\d{2}|\p{L}+|\d+""").findAll(normalized).map { it.value }.toList()
        val consumed = mutableSetOf<Int>()
        words.forEachIndexed { index, word ->
            if (index in consumed) return@forEachIndexed
            if (word in setOf("половину", "половине", "половина", "пол")) {
                val next = numberAt(words, index + 1)
                if (next != null) {
                    if (next in 1..12) candidates += adjustedClock(next - 1, 30, words, index + 1)
                    else invalid = true
                    consumed += index + 1
                    // «Восьмого утра» относится к половине восьмого, не к 08:00.
                    val qualifier = words.getOrNull(index + 2)
                    if (qualifier?.startsWith("утр") == true || qualifier?.startsWith("вечер") == true || qualifier in hourUnits) {
                        consumed += index + 2
                        if (qualifier in hourUnits && words.getOrNull(index + 3)?.let { it.startsWith("утр") || it.startsWith("вечер") } == true) {
                            consumed += index + 3
                        }
                    }
                }
                return@forEachIndexed
            }
            if (word in setOf("в", "на") || word in hourUnits) {
                val unit = word in hourUnits
                val hour = if (unit) numberBefore(words, index) else numberAt(words, index + 1)
                // 'на 8 июля' является датой, не временем.
                val numberLength = if (hour != null && words.getOrNull(index + 1)?.let(::number)?.let { it != hour } == true) 2 else 1
                val suffix = words.getOrNull(index + 1 + numberLength)
                if (suffix in months || suffix?.let(::number)?.let { it >= 1900 } == true) return@forEachIndexed
                if (hour != null && hour < 1900) {
                    if (hour in 0..23) candidates += adjustedClock(hour, 0, words, if (unit) index - 1 else index + numberLength)
                    else invalid = true
                }
            }
            if (word.startsWith("вечер") || word.startsWith("утр")) {
                val hour = numberBefore(words, index)
                if (hour != null && hour in 0..23) candidates += adjustedClock(hour, 0, words, index - 1)
            }
        }
        return candidates.singleOrNull().takeUnless { invalid }
    }

    private val hourUnits = setOf("час", "часа", "часов")

    private fun adjustedClock(hour: Int, minute: Int, words: List<String>, numberEnd: Int): LocalTime {
        val next = words.getOrNull(numberEnd + 1)
        val period = if (next in hourUnits) words.getOrNull(numberEnd + 2) else next
        val afternoon = period?.startsWith("вечер") == true || period == "дня"
        return LocalTime.of(if (afternoon && hour in 1..11) hour + 12 else hour, minute)
    }

    /**
     * Отмечает синтаксис даты, даже если сама дата неверна или неоднозначна.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun hasDateExpression(text: String): Boolean {
        val normalized = normalize(text)
        return Regex("""(?<!\p{L})(послезавтра|завтра|сегодня)(?!\p{L})""").containsMatchIn(normalized) ||
            Regex("""\b\d{4}-\d{1,2}-\d{1,2}\b""").containsMatchIn(normalized) ||
            Regex("""через(?:\s+[\p{L}\d]+){1,4}\s+(?:день|дня|дней)(?!\p{L})""").containsMatchIn(normalized) ||
            months.keys.any { Regex("(?<!\\p{L})${Regex.escape(it)}(?!\\p{L})").containsMatchIn(normalized) } ||
            weekdays.keys.any { Regex("(?<!\\p{L})${Regex.escape(it)}\\p{L}*(?!\\p{L})").containsMatchIn(normalized) }
    }

    fun date(text: String, today: LocalDate): LocalDate? {
        val normalized = normalize(text)
        val candidates = mutableSetOf<LocalDate>()
        var invalid = false
        Regex("""(?<!\p{L})(послезавтра|завтра|сегодня)(?!\p{L})""").findAll(normalized).forEach {
            val offset = when (it.value) { "послезавтра" -> 2L; "завтра" -> 1L; else -> 0L }
            candidates += today.plusDays(offset)
        }
        weekdays.forEach { (stem, day) ->
            if (Regex("(?<!\\p{L})${Regex.escape(stem)}\\p{L}*(?!\\p{L})").containsMatchIn(normalized)) {
                val offset = (day.value - today.dayOfWeek.value + 7) % 7
                candidates += today.plusDays(if (offset == 0) 7 else offset.toLong())
            }
        }
        Regex("""\b(\d{4}-\d{1,2}-\d{1,2})\b""").findAll(normalized).forEach {
            val candidate = runCatching { LocalDate.parse(it.value) }.getOrNull()
            if (candidate == null) invalid = true else candidates += candidate
        }
        Regex("""через\s+([\p{L}\d]+(?:\s+[\p{L}]+)?)\s+(?:день|дня|дней)(?!\p{L})""").findAll(normalized).forEach {
            val amountWords = it.groupValues[1].split(Regex("\\s+"))
            val amount = numberAt(amountWords, 0)
            val complete = amountWords.size == 1 || (number(amountWords[0]) in 20..90 && number(amountWords[1]) in 1..9)
            if (amount == null || amount < 0 || !complete) invalid = true
            else candidates += today.plusDays(amount.toLong())
        }
        val words = normalized.split(Regex("""[^\p{L}\p{N}]+""")).filter(String::isNotBlank)
        words.forEachIndexed { monthIndex, word ->
            if (word !in months) return@forEachIndexed
            val day = numberBefore(words, monthIndex) ?: return@forEachIndexed
            val year = words.getOrNull(monthIndex + 1)?.toIntOrNull()?.takeIf { it >= 1900 }
            val candidate = runCatching {
                val value = LocalDate.of(year ?: today.year, months.getValue(word), day)
                if (year == null && value.isBefore(today)) value.plusYears(1) else value
            }.getOrNull()
            if (candidate == null) invalid = true else candidates += candidate
        }
        return candidates.singleOrNull().takeUnless { invalid }
    }

    fun amount(text: String, unit: String): Int? {
        val normalized = normalize(text)
        val candidates = mutableSetOf<Int>()
        var invalid = false
        Regex("""через\s+([\p{L}\d]+(?:\s+[\p{L}]+)?)\s+(?:$unit)""").findAll(normalized).forEach { match ->
            val words = match.groupValues[1].split(Regex("\\s+"))
            val value = numberAt(words, 0)
            // Не принимаем частично разобранное выражение вроде 'два с небольшим'.
            val complete = words.size == 1 || (number(words[0]) in 20..90 && number(words[1]) in 1..9)
            if (value == null || !complete) invalid = true else candidates += value
        }
        return candidates.singleOrNull().takeUnless { invalid }
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
