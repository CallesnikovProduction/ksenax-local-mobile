package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmOneShotProtocolTest {

    @Test
    fun `prompt declares small alarm functions`() {
        val prompt = AlarmOneShotProtocol.buildOneShotPrompt("Поставь будильник на 19:00")

        assertTrue(prompt.contains("declaration:alarm_at_time{"))
        assertTrue(prompt.contains("declaration:alarm_after_hours{"))
        assertTrue(prompt.contains("declaration:alarm_after_minutes{"))
        assertTrue(prompt.contains("declaration:alarm_at_date_time{"))
    }

    @Test
    fun `parses functiongemma escaped at time arguments`() {
        val call = AlarmOneShotProtocol.parseOneShotResponse(
            "<start_function_call>call:alarm_at_time{time:<escape>19:00<escape>}<end_function_call>"
        )
        val arguments = call.arguments.JSONtoString()

        assertEquals("alarm_at_time", call.name)
        assertTrue(arguments.contains(""""time":"19:00""""))
    }

    @Test
    fun `parses functiongemma escaped relative hours arguments`() {
        val call = AlarmOneShotProtocol.parseOneShotResponse(
            "<start_function_call>call:alarm_after_hours{count:3,hours:9}<end_function_call>"
        )
        val arguments = call.arguments.JSONtoString()

        assertEquals("alarm_after_hours", call.name)
        assertTrue(arguments.contains(""""count":3"""))
        assertTrue(arguments.contains(""""hours":9"""))
    }

    @Test
    fun `parses functiongemma alarm tool response with count and escaped local time`() {
        val call = AlarmOneShotProtocol.parseOneShotResponse(
            "<start_function_call>call:alarm_at_time{count:3,start_local_time:<escape>9:00<escape>}<end_function_call>"
        )
        val arguments = call.arguments.JSONtoString()

        assertEquals("alarm_at_time", call.name)
        assertTrue(arguments.contains(""""count":3"""))
        assertTrue(arguments.contains(""""start_local_time":"9:00""""))
    }
}
