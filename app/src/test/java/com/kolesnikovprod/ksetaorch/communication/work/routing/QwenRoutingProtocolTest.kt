package com.kolesnikovprod.ksetaorch.communication.work.routing

import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchOneShotProtocol
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

/** Контракт и границы JSON; эти тесты не доказывают качество Qwen.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class QwenRoutingProtocolTest {
    private val protocol = QwenRoutingProtocol(TorchOneShotProtocol.declarations + AlarmOneShotProtocol.declarations + CalendarEventOneShotProtocol.declarations)

    @Test fun `catalogue and protocol never become user input`() {
        val input = "Пожалуйста, помоги с запросом"
        val request = protocol.buildRequest(input, ZonedDateTime.parse("2026-10-06T13:00:00+03:00"))
        assertEquals(input, request.prompt)
        assertTrue(request.systemInstruction.contains("alarm_after_minutes"))
        assertFalse(request.prompt.contains("Каталог"))
        assertFalse(request.systemInstruction.contains("<|im_start|>"))
    }
    @Test fun `four routes and real argument objects are distinct`() {
        val result = protocol.parseResponse("""{"route":"FAST_TOOL","tool":"alarm_at_time","arguments":{"time":"19:00","count":3}}""") as KsenaxRoutingDecision.FastTool
        assertEquals("alarm_at_time", result.toolName)
        assertEquals("3", result.arguments["count"].toString())
        assertEquals(KsenaxRoutingDecision.PlannedWork, protocol.parseResponse("""{"route":"LLM_BOUND"}"""))
        assertTrue(protocol.parseResponse("""{"route":"NEEDS_CLARIFICATION","question":"Во сколько?"}""") is KsenaxRoutingDecision.Clarification)
        assertTrue(protocol.parseResponse("""{"route":"UNSUPPORTED"}""") is KsenaxRoutingDecision.Unsupported)
    }
    @Test fun `malformed native or foreign protocols never become calls`() {
        listOf("call:torch_on{}}", """{"route":"FAST_TOOL","tool":"torch_on","arguments":{}}}""",
            """<tool_call>{"name":"torch_on","arguments":{}}</tool_call>""",
            """{"route":"LLM_BOUND"}{"route":"UNSUPPORTED"}""", "[]",
            """{"route":"FAST_TOOL","tool":"unknown","arguments":{}}""",
            """{"route":"FAST_TOOL","tool":"torch_on","arguments":"{}"}""",
            """{"route":"LLM_BOUND","tool":"torch_on"}""")
            .forEach { source -> assertThrows(Exception::class.java) { protocol.parseResponse(source) } }
    }
    @Test fun `duplicate escaped or nested keys remain ambiguous`() {
        listOf("""{"route":"LLM_BOUND","route":"UNSUPPORTED"}""",
            """{"route":"LLM_BOUND","ro\u0075te":"UNSUPPORTED"}""",
            """{"route":"FAST_TOOL","tool":"alarm_at_time","arguments":{"time":"07:00","time":"08:00"}}""")
            .forEach { source -> assertThrows(Exception::class.java) { protocol.parseResponse(source) } }
        assertTrue(protocol.parseResponse("""{"route":"UNSUPPORTED","reason":"Текст \"route\": внутри строки"}""") is KsenaxRoutingDecision.Unsupported)
    }

    @Test fun `legacy Hermes text produces untrusted decisions not SDK calls`() {
        val decision = protocol.parseHermesResponse("""<tool_call>{"name":"alarm_after_minutes","arguments":{"minutes":4,"count":20}}</tool_call>""") as KsenaxRoutingDecision.FastTool
        assertEquals("alarm_after_minutes", decision.toolName)
        assertEquals("20", decision.arguments["count"].toString())
        assertEquals(KsenaxRoutingDecision.PlannedWork, protocol.parseHermesResponse("""<tool_call>{"name":"route_planned_work","arguments":{}}</tool_call>"""))
        assertTrue(protocol.parseHermesResponse("""<tool_call>{"name":"route_clarification","arguments":{}}</tool_call>""") is KsenaxRoutingDecision.Clarification)
        assertTrue(protocol.parseHermesResponse("""<tool_call>{"name":"route_unsupported","arguments":{}}</tool_call>""") is KsenaxRoutingDecision.Unsupported)
    }

    @Test fun `Hermes never repairs prose multiple calls duplicate keys or unknown functions`() {
        listOf(
            """Done <tool_call>{"name":"torch_on","arguments":{}}</tool_call>""",
            """<tool_call>{"name":"torch_on","arguments":{}}}</tool_call>""",
            """<tool_call>{"name":"torch_on","arguments":{}}</tool_call><tool_call>{"name":"torch_off","arguments":{}}</tool_call>""",
            """<tool_call>{"name":"torch_on","name":"torch_off","arguments":{}}</tool_call>""",
            """<tool_call>{"name":"alarm_at_time","arguments":{"time":"07:00","t\u0069me":"08:00"}}</tool_call>""",
            """<tool_call>{"name":"BUDIEN","arguments":{}}</tool_call>""",
            """<tool_call>{"name":"torch_on","arguments":"{}"}</tool_call>""",
            """<tool_call>{"name":"route_unsupported","arguments":{"tool":"torch_on"}}</tool_call>""",
        ).forEach { source -> assertThrows(Exception::class.java) { protocol.parseHermesResponse(source) } }
    }
}
