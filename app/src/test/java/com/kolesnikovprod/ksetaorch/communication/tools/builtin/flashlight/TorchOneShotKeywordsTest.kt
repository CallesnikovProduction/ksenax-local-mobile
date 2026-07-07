package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorchOneShotKeywordsTest {

    @Test
    fun `matches flashlight prompts`() {
        assertTrue(TorchOneShotKeywords.matches("фонарик"))
        assertTrue(TorchOneShotKeywords.matches("ВКЛЮЧИ   ФОНАРИК"))
        assertTrue(TorchOneShotKeywords.matches("Пожалуйста, выключи фонарик"))
        assertTrue(TorchOneShotKeywords.matches("выключить фонарь"))
    }

    @Test
    fun `rejects unrelated prompts`() {
        assertFalse(TorchOneShotKeywords.matches("Привет"))
        assertFalse(TorchOneShotKeywords.matches("Открой заметки"))
    }
}
