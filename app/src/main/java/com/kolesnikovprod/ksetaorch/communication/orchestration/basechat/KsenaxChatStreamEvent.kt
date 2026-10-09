package com.kolesnikovprod.ksetaorch.communication.orchestration.basechat

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ответ текстового чата, независимо от способа хранения и lifetime conversation.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
sealed interface KsenaxChatStreamEvent {
    data class TextDelta(val text: String) : KsenaxChatStreamEvent
    data class Completed(val text: String, val latencyMs: Long) : KsenaxChatStreamEvent
}

internal fun Flow<KsenaxModelStreamEvent>.toChatEvents(): Flow<KsenaxChatStreamEvent> = map { event ->
    when (event) {
        is KsenaxModelStreamEvent.TextDelta -> KsenaxChatStreamEvent.TextDelta(event.text)
        is KsenaxModelStreamEvent.Completed -> KsenaxChatStreamEvent.Completed(event.response.text, event.response.latencyMs)
    }
}
