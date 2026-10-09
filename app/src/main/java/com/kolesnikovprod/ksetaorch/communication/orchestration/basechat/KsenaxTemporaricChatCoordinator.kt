package com.kolesnikovprod.ksetaorch.communication.orchestration.basechat

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import kotlinx.coroutines.flow.Flow

/**
 * Изолированный runtime-контур временного чата для проверки сырого поведения
 * локальной модели.
 *
 * Координатор не принимает историю и не создаёт системную инструкцию. Каждый
 * запрос передаётся в новую одноразовую conversation через
 * [KsenaxModelSession.streamEphemeral], поэтому соседние turn-ы не влияют друг
 * на друга.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxTemporaricChatCoordinator(
    private val modelSession: KsenaxModelSession,
) {

    suspend fun prepare() {
        modelSession.initializeEngine()
    }

    fun streamReply(userText: String): Flow<KsenaxChatStreamEvent> {
        val normalizedText = userText.trim()
        require(normalizedText.isNotEmpty()) {
            "TEMPORARIC_PATTERN user text must not be blank."
        }

        return modelSession.streamEphemeral(normalizedText).toChatEvents()
    }
}
