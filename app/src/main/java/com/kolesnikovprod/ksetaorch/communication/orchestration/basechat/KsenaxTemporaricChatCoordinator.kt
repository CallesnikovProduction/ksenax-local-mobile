package com.kolesnikovprod.ksetaorch.communication.orchestration.basechat

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    fun streamReply(userText: String): Flow<KsenaxTemporaricChatEvent> {
        val normalizedText = userText.trim()
        require(normalizedText.isNotEmpty()) {
            "TEMPORARIC_PATTERN user text must not be blank."
        }

        return modelSession.streamEphemeral(normalizedText).map { event ->
            when (event) {
                is KsenaxModelStreamEvent.TextDelta ->
                    KsenaxTemporaricChatEvent.TextDelta(event.text)

                is KsenaxModelStreamEvent.Completed ->
                    KsenaxTemporaricChatEvent.Completed(
                        text = event.response.text,
                        latencyMs = event.response.latencyMs,
                    )
            }
        }
    }
}

sealed interface KsenaxTemporaricChatEvent {
    data class TextDelta(
        val text: String,
    ) : KsenaxTemporaricChatEvent

    data class Completed(
        val text: String,
        val latencyMs: Long,
    ) : KsenaxTemporaricChatEvent
}
