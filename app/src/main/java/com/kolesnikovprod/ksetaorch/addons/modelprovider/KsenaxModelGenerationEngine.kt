package com.kolesnikovprod.ksetaorch.addons.modelprovider

import dev.openksenax.addons.contract.HostCapabilities
import dev.openksenax.addons.contract.HostCapabilityId
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import kotlinx.coroutines.CancellationException

/**
 * Адаптер addon Model Provider к общей [KsenaxModelSession] OKx.
 *
 * Класс не создаёт второй model engine. Он преобразует IPC-запрос
 * в одноразовый [KsenaxModelTaskProfile.TEXT_GENERATION] в shared сессии.
 * Поэтому addon не получает Engine, путь к модели или историю
 * основного чата.
 *
 * @since 0.3
 */
internal class KsenaxModelGenerationEngine(
    private val modelSession: KsenaxModelSession,
    private val modelId: String? = null,
    private val isTextGenerationReady: () -> Boolean,
) : ModelGenerationEngine {

    override val supportedCapabilities: Set<HostCapabilityId> =
        setOf(HostCapabilities.TextGeneration)

    override val availableCapabilities: Set<HostCapabilityId>
        get() = supportedCapabilities.takeIf {
            isTextGenerationReady()
        }.orEmpty()

    override suspend fun generate(
        command: AuthorizedModelGenerationCommand,
    ): ModelGenerationEngineResult {
        require(
            command.capabilityId ==
                    HostCapabilities.TextGeneration,
        ) {
            "Only ${HostCapabilities.TextGeneration.value} is supported"
        }

        ensureTextGenerationReady()

        val response = try {
            modelSession.askStateless(
                KsenaxModelRequest(
                    prompt = command.toStatelessPrompt(),
                    systemInstruction =
                        command.toSystemInstruction(),
                    profile =
                        KsenaxModelTaskProfile.TEXT_GENERATION,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (!isTextGenerationReady()) {
                throw ModelGenerationEngineNotReadyException(
                    message =
                        "The OpenKsenax text-generation model became unavailable.",
                    cause = error,
                )
            }
            throw error
        }

        return ModelGenerationEngineResult(
            text = response.text,
            modelId = modelId,
            inferenceTimeMillis = response.latencyMs,
        )
    }

    private fun ensureTextGenerationReady() {
        if (!isTextGenerationReady()) {
            throw ModelGenerationEngineNotReadyException(
                "The OpenKsenax text-generation model is not ready.",
            )
        }
    }

    private fun AuthorizedModelGenerationCommand.toSystemInstruction():
            String {
        val instructions = buildList {
            systemInstruction
                ?.takeIf(String::isNotBlank)
                ?.let(::add)

            messages
                .filter { message ->
                    message.role == AuthorizedModelMessageRole.SYSTEM
                }
                .mapTo(this) { message -> message.content }
        }

        return instructions.joinToString(
            separator = "\n\n",
        ).ifBlank {
            DEFAULT_SYSTEM_INSTRUCTION
        }
    }

    private fun AuthorizedModelGenerationCommand.toStatelessPrompt(): String {
        return messages
            .asSequence()
            .filter { message ->
                message.role != AuthorizedModelMessageRole.SYSTEM
            }
            .joinToString(separator = "\n\n") { message ->
                "[${message.role.promptLabel}]\n${message.content}"
            }
    }

    private val AuthorizedModelMessageRole.promptLabel: String
        get() = when (this) {
            AuthorizedModelMessageRole.SYSTEM -> "SYSTEM"
            AuthorizedModelMessageRole.USER -> "USER"
            AuthorizedModelMessageRole.ASSISTANT -> "ASSISTANT"
        }

    private companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "Follow the supplied conversation and generate the next text response."
    }
}
