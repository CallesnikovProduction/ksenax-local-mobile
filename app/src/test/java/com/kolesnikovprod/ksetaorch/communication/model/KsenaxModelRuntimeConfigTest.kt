package com.kolesnikovprod.ksetaorch.communication.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KsenaxModelRuntimeConfigTest {

    @Test
    fun `context window must be positive or null`() {
        assertEquals(
            1_024,
            KsenaxModelRuntimeConfig.DEFAULT.maxContextTokens,
        )
        assertThrows(IllegalArgumentException::class.java) {
            KsenaxModelRuntimeConfig(maxContextTokens = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KsenaxModelRuntimeConfig(maxContextTokens = -1)
        }
    }

    @Test
    fun `session applies context window without eager engine initialization`() = runBlocking {
        val session: KsenaxModelSession = LiteRtKsenaxModelSession(
            modelPath = "not-used-before-initialization.litertlm",
            cacheDirPath = "not-used-before-initialization-cache",
            audioBackend = null,
        )
        val configured = KsenaxModelRuntimeConfig(maxContextTokens = 4_096)

        session.configureRuntime(configured)

        assertEquals(configured, session.runtimeConfig)
    }
}
