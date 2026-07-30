package com.kolesnikovprod.ksetaorch.addons.modelprovider

import dev.openksenax.addons.contract.model.ModelGenerationRequest
import dev.openksenax.addons.contract.model.ModelFailureCode
import dev.openksenax.addons.contract.model.ModelGenerationStatus
import dev.openksenax.addons.contract.model.ModelMessage
import dev.openksenax.addons.contract.model.ModelMessageRole
import dev.openksenax.addons.contract.model.ModelRequestPriority
import dev.openksenax.addons.contract.model.ModelRequestValidationKind
import dev.openksenax.addons.contract.model.ModelSamplingParameters
import dev.openksenax.addons.contract.model.validationIssue
import dev.openksenax.addons.contract.HostCapabilities
import dev.openksenax.addons.contract.AddonId
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelResponse
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KsenaxModelGenerationEngineTest {

    @Test
    fun `generation uses shared stateless text profile`() =
        runBlocking {
            val session = RecordingModelSession()
            val engine = KsenaxModelGenerationEngine(
                modelSession = session,
                modelId = "local-test-model",
                isTextGenerationReady = { true },
            )
            val request = validRequest().copy(
                systemInstruction = "Host instruction",
                messages = listOf(
                    ModelMessage(
                        ModelMessageRole.SYSTEM,
                        "Addon instruction",
                    ),
                    ModelMessage(
                        ModelMessageRole.USER,
                        "First message",
                    ),
                    ModelMessage(
                        ModelMessageRole.ASSISTANT,
                        "Previous answer",
                    ),
                    ModelMessage(
                        ModelMessageRole.USER,
                        "Next message",
                    ),
                ),
            )

            val result = engine.generate(
                request.toAuthorizedCommand(
                    addonId = AddonId(request.addonId),
                    capabilityId = HostCapabilities.TextGeneration,
                ),
            )

            assertEquals(
                setOf(HostCapabilities.TextGeneration),
                engine.availableCapabilities,
            )
            assertEquals(
                setOf(HostCapabilities.TextGeneration),
                engine.supportedCapabilities,
            )
            assertEquals(
                KsenaxModelTaskProfile.TEXT_GENERATION,
                session.lastRequest?.profile,
            )
            assertEquals(
                "Host instruction\n\nAddon instruction",
                session.lastRequest?.systemInstruction,
            )
            assertEquals(
                "[USER]\nFirst message\n\n" +
                        "[ASSISTANT]\nPrevious answer\n\n" +
                        "[USER]\nNext message",
                session.lastRequest?.prompt,
            )
            assertEquals("generated text", result.text)
            assertEquals("local-test-model", result.modelId)
            assertEquals(15L, result.inferenceTimeMillis)
        }

    @Test
    fun `runtime readiness does not remove structural provider support`() {
        var isReady = false
        val engine = KsenaxModelGenerationEngine(
            modelSession = RecordingModelSession(),
            isTextGenerationReady = { isReady },
        )

        assertEquals(
            setOf(HostCapabilities.TextGeneration),
            engine.supportedCapabilities,
        )
        assertTrue(engine.availableCapabilities.isEmpty())

        isReady = true

        assertEquals(
            setOf(HostCapabilities.TextGeneration),
            engine.availableCapabilities,
        )
    }

    @Test
    fun `generation fails before model session when runtime is not ready`() =
        runBlocking {
            val session = RecordingModelSession()
            val engine = KsenaxModelGenerationEngine(
                modelSession = session,
                isTextGenerationReady = { false },
            )
            val request = validRequest()

            try {
                engine.generate(
                    request.toAuthorizedCommand(
                        addonId = AddonId(request.addonId),
                        capabilityId = HostCapabilities.TextGeneration,
                    ),
                )
                fail("Expected ModelGenerationEngineNotReadyException")
            } catch (_: ModelGenerationEngineNotReadyException) {
                assertEquals(null, session.lastRequest)
            }
        }

    @Test
    fun `provider API 1 rejects unsupported request features`() {
        val customSampling = validRequest().copy(
            sampling = ModelSamplingParameters(
                temperature = 0.7f,
            ),
        )
        val toolMessage = validRequest().copy(
            messages = listOf(
                ModelMessage(
                    role = ModelMessageRole.TOOL,
                    content = "tool result",
                ),
            ),
        )
        val schema = validRequest().copy(
            outputSchemaJson = "{\"type\":\"object\"}",
        )

        assertEquals(
            ModelRequestValidationKind.UNSUPPORTED,
            customSampling.validationIssue()?.kind,
        )
        assertEquals(
            ModelRequestValidationKind.UNSUPPORTED,
            toolMessage.validationIssue()?.kind,
        )
        assertEquals(
            ModelRequestValidationKind.UNSUPPORTED,
            schema.validationIssue()?.kind,
        )
    }

    @Test
    fun `unknown model provider wire values fail closed`() {
        assertEquals(
            ModelMessageRole.UNKNOWN,
            ModelMessageRole.fromWireValue("FUTURE_ROLE"),
        )
        assertEquals(
            ModelRequestPriority.UNKNOWN,
            ModelRequestPriority.fromWireValue(null),
        )
        assertEquals(
            ModelGenerationStatus.UNKNOWN,
            ModelGenerationStatus.fromWireValue("FUTURE_STATUS"),
        )
        assertEquals(
            ModelFailureCode.UNKNOWN,
            ModelFailureCode.fromWireValue("FUTURE_FAILURE"),
        )
    }

    private fun validRequest(): ModelGenerationRequest {
        return ModelGenerationRequest(
            requestId = "request-1",
            addonId = "dev.openksenax.addon.test",
            capabilityId =
                HostCapabilities.TextGeneration.value,
            messages = listOf(
                ModelMessage(
                    role = ModelMessageRole.USER,
                    content = "Generate a response",
                ),
            ),
        )
    }

    private class RecordingModelSession : KsenaxModelSession {

        var lastRequest: KsenaxModelRequest? = null

        override suspend fun initializeEngine() = Unit

        override suspend fun askStateless(
            request: KsenaxModelRequest,
        ): KsenaxModelResponse {
            lastRequest = request
            return KsenaxModelResponse(
                text = "generated text",
                latencyMs = 15L,
                profile = request.profile,
            )
        }

        override suspend fun askPersistent(
            request: KsenaxModelRequest,
        ): KsenaxModelResponse {
            error("Persistent path must not be used")
        }

        override fun streamPersistent(
            request: KsenaxModelRequest,
        ): Flow<KsenaxModelStreamEvent> = emptyFlow()

        override fun streamEphemeral(
            userText: String,
        ): Flow<KsenaxModelStreamEvent> = emptyFlow()

        override suspend fun transcribe(
            voiceMessage: KsenaxVoiceMessage,
            prompt: KsenaxVoiceTranscriptionPrompt,
        ): KsenaxModelResponse {
            error("Voice path must not be used")
        }

        override suspend fun resetPersistentConversation() = Unit

        override suspend fun close() = Unit
    }
}
