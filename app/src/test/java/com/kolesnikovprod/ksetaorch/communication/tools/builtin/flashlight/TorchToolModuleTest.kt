package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет короткий flashlight-протокол и его входные слова.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class TorchOneShotProtocolTest {

    @Test
    fun `buildOneShotPrompt puts current user message into FG user turn`() {
        val prompt = TorchOneShotProtocol.buildPrompt(
            userMessage = "Включи фонарик",
            declaration = TorchToolOneShot.On,
        )

        assertEquals(
            """
            <bos><start_of_turn>developer
            You are a model that can do function calling with the following functions
            <start_function_declaration>
            declaration:torch_on{
            description:<escape>Turns on the device flashlight.<escape>,
            parameters:null
            <end_function_declaration>
            <end_of_turn>

            <start_of_turn>user
            User request:
            Включи фонарик

            Action instruction:
            Включи фонарик

            Return exactly one function call and no prose.
            <end_of_turn>

            <start_of_turn>model
            """.trimIndent(),
            prompt,
        )
    }

    @Test
    fun `default one-shot prompt declares torch on and off`() {
        val prompt = TorchOneShotProtocol.buildOneShotPrompt("Переключи фонарик")

        assertTrue(prompt.contains("declaration:torch_on{"))
        assertTrue(prompt.contains("declaration:torch_off{"))
    }

    @Test
    fun `torch one-shot prompt declares toggle`() {
        val prompt = TorchOneShotProtocol.buildOneShotPrompt("фонарик")

        assertTrue(prompt.contains("declaration:torch_toggle{"))
    }

    @Test
    fun `keywords match flashlight forms without matching a person name`() {
        assertTrue(TorchOneShotKeywords.matches("Погаси вспышку"))
        assertTrue(TorchOneShotKeywords.matches("Включи свет на телефоне"))
        assertFalse(TorchOneShotKeywords.matches("Позвони Светлане"))
    }
}
