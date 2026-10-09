package com.kolesnikovprod.ksetaorch.communication.tools.builtin.scheduling

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

/**
 * Время и составные числительные не должны превращаться в частично прочитанные числа.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class LocalScheduleParserTest {
    @Test fun `quantity must consume the whole modifier`() {
        assertEquals(5, LocalScheduleParser.quantity("5"))
        assertEquals(5, LocalScheduleParser.quantity("пять"))
        assertEquals(25, LocalScheduleParser.quantity("двадцать пять"))
        listOf("зарядник, пять", "пять зарядников", "пять или шесть", "двадцать пять запасных", "двадцать десять").forEach {
            assertNull(it, LocalScheduleParser.quantity(it))
        }
    }
    @Test fun `date syntax remains visible when date is ambiguous or invalid`() {
        val today = LocalDate.of(2026, 7, 7)
        listOf("завтра или послезавтра", "2026-13-40", "2026-7-40", "завтра или 2026-7-40", "31 февраля", "через много дней", "через два или три дня").forEach {
            assertTrue(it, LocalScheduleParser.hasDateExpression(it))
            assertNull(it, LocalScheduleParser.date(it, today))
        }
        listOf("сегодня", "завтра", "послезавтра", "в пятницу", "2026-07-08", "восьмое июля", "через двадцать пять дней").forEach {
            assertTrue(it, LocalScheduleParser.hasDateExpression(it))
            assertNotNull(it, LocalScheduleParser.date(it, today))
        }
        listOf("разбуди в 07:00", "через два часа", "через три минуты", "запиши мысль").forEach {
            assertFalse(it, LocalScheduleParser.hasDateExpression(it))
        }
    }

    @Test fun `different intervals require clarification rather than first value`() {
        assertNull(LocalScheduleParser.amount("разбуди через два часа или через три часа", "час"))
        assertNull(LocalScheduleParser.amount("разбуди через два или три часа", "час"))
        assertNull(LocalScheduleParser.amount("разбуди через 10 минут или через 20 минут", "мин"))
        assertEquals(2, LocalScheduleParser.amount("через два часа то есть через 2 часа", "час"))
    }

    @Test fun `different explicit clock values require clarification`() {
        assertNull(LocalScheduleParser.clock("разбуди в 07:00 или в 09:00"))
        assertNull(LocalScheduleParser.clock("разбуди в семь или в девять"))
        assertNull(LocalScheduleParser.clock("разбуди в 07:00 или в девять"))
        assertNull(LocalScheduleParser.clock("в семь утра или в семь вечера"))
    }

    @Test fun `repeated equivalent clocks and half hour retain their value`() {
        assertEquals(LocalTime.of(7, 0), LocalScheduleParser.clock("в 07:00 то есть в семь"))
        assertEquals(LocalTime.of(7, 30), LocalScheduleParser.clock("в половине восьмого утра"))
        assertEquals(LocalTime.of(19, 30), LocalScheduleParser.clock("в половине восьмого вечера"))
        assertEquals(LocalTime.of(7, 30), LocalScheduleParser.clock("в 07:30"))
    }

    @Test fun `different explicit dates require clarification`() {
        val today = LocalDate.of(2026, 7, 7)
        assertNull(LocalScheduleParser.date("завтра или послезавтра", today))
        assertNull(LocalScheduleParser.date("8 июля или 9 июля", today))
        assertNull(LocalScheduleParser.date("2026-07-08 или 2026-07-09", today))
        assertNull(LocalScheduleParser.date("завтра или в пятницу", today))
        assertEquals(LocalDate.of(2026, 7, 8), LocalScheduleParser.date("завтра 8 июля", today))
    }

    @Test fun `relative days do not change morning into afternoon`() {
        val today = LocalDate.of(2026, 7, 7)
        val source = "создай встречу через два дня в десять утра"
        assertEquals(today.plusDays(2), LocalScheduleParser.date(source, today))
        assertEquals(LocalTime.of(10, 0), LocalScheduleParser.clock(source))
        assertEquals(today.plusDays(25), LocalScheduleParser.date("через двадцать пять дней", today))
        assertNull(LocalScheduleParser.date("через два дня или через три дня", today))
    }

    @Test fun `compound numbers preserve count time and day`() {
        assertEquals(25, LocalScheduleParser.alarmCount("поставь двадцать пять будильников через 4 минуты"))
        assertEquals(5, LocalScheduleParser.alarmCount("поставь будильники, 5 штук, через 10 минут"))
        assertEquals(LocalTime.of(21, 0), LocalScheduleParser.clock("разбуди в двадцать один"))
        assertEquals(LocalTime.of(23, 0), LocalScheduleParser.clock("разбуди в двадцать три часа"))
        assertEquals(25, LocalScheduleParser.amount("разбуди через двадцать пять минут", "мин"))
        assertEquals(LocalDate.of(2026, 7, 28), LocalScheduleParser.date("на двадцать восьмое июля", LocalDate.of(2026, 7, 7)))
    }
    @Test fun `invalid clock and date are not guessed`() {
        assertNull(LocalScheduleParser.clock("на 25:30"))
        assertNull(LocalScheduleParser.date("31 февраля", LocalDate.of(2026, 7, 7)))
        assertNull(LocalScheduleParser.clock("завтра вечером"))
        assertNull(LocalScheduleParser.clock("на восьмое июля"))
    }
}
