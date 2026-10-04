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
