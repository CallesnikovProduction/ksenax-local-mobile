package com.kolesnikovprod.ksetaorch.communication.work.oneshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Проверяет разбор реального формата FunctionGemma.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class FunctionGemmaCallParserTest {

    @Test
    fun `parses escaped values and surrounding model tokens`() {
        val call = FunctionGemmaCallParser.parse(
            "<start_of_turn>model\n" +
                "<start_function_call>call:note_write{" +
                "title:<escape>День, который важен<escape>,count:3}" +
                "<end_function_call><end_of_turn>"
        )

        assertEquals("note_write", call.name)
        assertEquals("{\"title\":\"День, который важен\",\"count\":3}", call.argumentsJson)
    }

    @Test
    fun `rejects more than one function call`() {
        assertThrows(IllegalArgumentException::class.java) {
            FunctionGemmaCallParser.parse(
                "<start_function_call>call:torch_on{}<end_function_call>" +
                    "<start_function_call>call:torch_off{}<end_function_call>"
            )
        }
    }
}
