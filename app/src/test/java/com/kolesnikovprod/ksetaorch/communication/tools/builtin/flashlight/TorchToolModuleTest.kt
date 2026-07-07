package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
