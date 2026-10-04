package com.kolesnikovprod.ksetaorch.communication.work.routing

import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchOneShotProtocol
import org.junit.Assert.*
import org.junit.Test

/**
 * Четыре семантических исхода и недоверенный ответ FG.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class FunctionGemmaRoutingProtocolTest {
    private val protocol = FunctionGemmaRoutingProtocol(TorchOneShotProtocol.declarations)
    private fun response(name: String, args: String = "{}") = KsenaxModelFunctionResponse(listOf(KsenaxModelFunctionCall(name, args)), 1)

    @Test fun `request owns functions but not chat control tokens`() {
        val request = protocol.buildRequest("Мне нужен свет с телефона")
        assertTrue(request.functions.any { it.name == "torch_on" })
        assertTrue(request.functions.any { it.name == "route_clarification" })
        assertTrue(request.functions.any { it.name == "route_unsupported" })
        assertFalse(request.userMessage.contains("<start_of_turn>"))
    }
    @Test fun `four outcomes are distinguishable`() {
        assertTrue(protocol.parseResponse(response("torch_on")) is RequestRoute.FastTool)
        assertEquals(RequestRoute.PlannedWork, protocol.parseResponse(response("route_planned_work")))
        assertEquals(RequestRoute.Clarification("Когда?"), protocol.parseResponse(response("route_clarification", """{"question":"Когда?"}""")))
        assertEquals(RequestRoute.Unsupported("Нет инструмента"), protocol.parseResponse(response("route_unsupported", """{"reason":"Нет инструмента"}""")))
    }
    @Test fun `unknown multiple empty and malformed calls are rejected`() {
        listOf(response("unknown"), response("torch_on", "{"), response("route_planned_work", """{"extra":1}"""),
            response("route_clarification", """{"question":7}"""), KsenaxModelFunctionResponse(emptyList(), 1),
            KsenaxModelFunctionResponse(listOf(KsenaxModelFunctionCall("torch_on", "{}"), KsenaxModelFunctionCall("torch_off", "{}")), 1)
        ).forEach { assertThrows(IllegalArgumentException::class.java) { protocol.parseResponse(it) } }
    }
}
