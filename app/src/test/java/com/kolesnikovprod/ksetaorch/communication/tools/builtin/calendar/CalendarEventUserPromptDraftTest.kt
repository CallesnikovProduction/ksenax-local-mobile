package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventUserPromptDraftTest {

    @Test
    fun `normalizes russian date and time words`() {
        val json = CalendarEventUserPromptDraft.buildJson(
            "создать событие в календаре на восьмое июля свидания с ксюша и в семнадцать часов вечера"
        )

        assertTrue(json?.contains(""""title":"Свидание с Ксюшей"""") == true)
        assertTrue(json?.contains("-07-08T17:00") == true)
    }
}
