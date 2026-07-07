package com.kolesnikovprod.ksetaorch.communication.orchestration.basechat

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelResponse
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxTemporaricChatCoordinatorTest {

    @Test
    fun `stream reply uses only ephemeral model entry`() = runBlocking {
        val session = RecordingModelSession()
        val coordinator = KsenaxTemporaricChatCoordinator(session)

        val events = coordinator.streamReply("  raw prompt  ").toList()

        assertEquals(listOf("raw prompt"), session.ephemeralPrompts)
        assertEquals(
            listOf(
                KsenaxTemporaricChatEvent.TextDelta("raw "),
                KsenaxTemporaricChatEvent.TextDelta("answer"),
                KsenaxTemporaricChatEvent.Completed("raw answer", 42L),
            ),
            events,
        )
        assertEquals(0, session.persistentCalls)
        assertEquals(0, session.statelessCalls)
    }

    @Test
    fun `prepare only initializes shared engine`() = runBlocking {
        val session = RecordingModelSession()

        KsenaxTemporaricChatCoordinator(session).prepare()

        assertTrue(session.wasInitialized)
        assertEquals(0, session.persistentCalls)
        assertEquals(0, session.statelessCalls)
    }
}

private class RecordingModelSession : KsenaxModelSession {
    var wasInitialized = false
    var persistentCalls = 0
    var statelessCalls = 0
    val ephemeralPrompts = mutableListOf<String>()

    override suspend fun initializeEngine() {
        wasInitialized = true
    }

    override suspend fun askStateless(
        request: KsenaxModelRequest,
    ): KsenaxModelResponse {
        statelessCalls += 1
        error("TEMPORARIC_PATTERN coordinator must not call askStateless.")
    }

    override suspend fun askPersistent(
        request: KsenaxModelRequest,
    ): KsenaxModelResponse {
        persistentCalls += 1
        error("TEMPORARIC_PATTERN coordinator must not call askPersistent.")
    }

    override fun streamPersistent(
        request: KsenaxModelRequest,
    ): Flow<KsenaxModelStreamEvent> {
        persistentCalls += 1
        error("TEMPORARIC_PATTERN coordinator must not call streamPersistent.")
    }

    override fun streamEphemeral(
        userText: String,
    ): Flow<KsenaxModelStreamEvent> {
        ephemeralPrompts += userText
        return flowOf(
            KsenaxModelStreamEvent.TextDelta("raw "),
            KsenaxModelStreamEvent.TextDelta("answer"),
            KsenaxModelStreamEvent.Completed(
                KsenaxModelResponse(
                    text = "raw answer",
                    latencyMs = 42L,
                    profile = KsenaxModelTaskProfile.CHAT,
                ),
            ),
        )
    }

    override suspend fun transcribe(
        voiceMessage: KsenaxVoiceMessage,
        prompt: KsenaxVoiceTranscriptionPrompt,
    ): KsenaxModelResponse {
        error("Not used by TEMPORARIC_PATTERN coordinator.")
    }

    override suspend fun resetPersistentConversation() {
        persistentCalls += 1
        error("TEMPORARIC_PATTERN coordinator must not reset persistent conversation.")
    }

    override suspend fun close() = Unit
}
