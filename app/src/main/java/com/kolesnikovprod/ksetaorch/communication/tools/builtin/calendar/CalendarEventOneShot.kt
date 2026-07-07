package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration

object CalendarEventOneShot : KsenaxOneShotDeclaration {
    override val codeName: String = "calendar_event_create"
    override val description: String =
        "Prepares an Android calendar event in the system Calendar app."
    override val parameters: String = """
        {"type":"object","properties":{"title":{"type":"string"},"start_local_date_time":{"type":"string","description":"yyyy-MM-dd'T'HH:mm"},"start_local_date":{"type":"string","description":"yyyy-MM-dd"},"start_local_time":{"type":"string","description":"HH:mm"},"start_delay_minutes":{"type":"integer"},"start_delay_hours":{"type":"number"},"duration_minutes":{"type":"integer"},"all_day":{"type":"boolean"},"location":{"type":"string"},"description":{"type":"string"}},"required":["title"],"additionalProperties":false}
    """.trimIndent()
}
