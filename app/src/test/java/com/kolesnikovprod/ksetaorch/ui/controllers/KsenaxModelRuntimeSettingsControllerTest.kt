package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelResponse
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRuntimeConfig
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KsenaxModelRuntimeSettingsControllerTest {

    @Test
    fun `selected context window is applied to every response session`() = runBlocking {
        val gemmaSession = RecordingRuntimeSession()
        val functionGemmaSession = RecordingRuntimeSession()
        val controller = KsenaxModelRuntimeSettingsController(
            responseModelSessions = listOf(gemmaSession, functionGemmaSession),
        )

        controller.applyMaxContextTokens(16_384)

        val expected = listOf(
            KsenaxModelRuntimeConfig(maxContextTokens = 16_384),
        )
        assertEquals(expected, gemmaSession.configurations)
        assertEquals(expected, functionGemmaSession.configurations)
    }
}

private class RecordingRuntimeSession : KsenaxModelSession {
    val configurations = mutableListOf<KsenaxModelRuntimeConfig>()

    override suspend fun configureRuntime(config: KsenaxModelRuntimeConfig) {
        configurations += config
    }

    override suspend fun initializeEngine() {
        unused<Unit>()
    }

    override suspend fun askStateless(
        request: KsenaxModelRequest,
    ): KsenaxModelResponse = unused()

    override suspend fun askPersistent(
        request: KsenaxModelRequest,
    ): KsenaxModelResponse = unused()

    override fun streamPersistent(
        request: KsenaxModelRequest,
    ): Flow<KsenaxModelStreamEvent> = unused()

    override fun streamEphemeral(
        userText: String,
    ): Flow<KsenaxModelStreamEvent> = unused()

    override suspend fun transcribe(
        voiceMessage: KsenaxVoiceMessage,
        prompt: KsenaxVoiceTranscriptionPrompt,
    ): KsenaxModelResponse = unused()

    override suspend fun resetPersistentConversation() {
        unused<Unit>()
    }

    override suspend fun close() = Unit

    private fun <T> unused(): T =
        error("Only configureRuntime is expected in this test.")
}
