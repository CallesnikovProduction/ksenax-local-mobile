package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.temporaric

import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxMessage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState

/**
 * Оперативный снимок TEMPORARIC_PATTERN-чата.
 *
 * Список [messages] существует только внутри process-scoped ViewModel. State не
 * содержит database id, repository-моделей или SavedStateHandle, поэтому после
 * завершения процесса Android восстановить эту переписку не сможет.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxTemporaricChatUiState(
    val inputText: String = "",
    val messages: List<KsenaxMessage> = emptyList(),
    val streamingAssistantText: String = "",
    val isGenerating: Boolean = false,
    val modelGateState: KsenaxBasicModelGateState = KsenaxBasicModelGateState.Idle,
    val errorMessage: String? = null,
) {
    val activeChat: KsenaxChat?
        get() {
            val visibleMessages = buildList {
                addAll(messages)
                if (streamingAssistantText.isNotEmpty()) {
                    add(
                        KsenaxMessage(
                            text = streamingAssistantText,
                            isUser = false,
                            isStreaming = isGenerating,
                        ),
                    )
                }
            }
            if (visibleMessages.isEmpty()) return null

            return KsenaxChat(
                id = TEMPORARIC_CHAT_ID,
                mode = ChatMode.Temporaric,
                title = "RAM ONLY",
                messages = visibleMessages,
            )
        }

    val isScreenBlocked: Boolean
        get() = modelGateState != KsenaxBasicModelGateState.Idle &&
            modelGateState != KsenaxBasicModelGateState.Ready

    val isAwaitingAssistantText: Boolean
        get() = isGenerating && streamingAssistantText.isEmpty()

    private companion object {
        const val TEMPORARIC_CHAT_ID = Long.MIN_VALUE + 1L
    }
}

sealed interface KsenaxTemporaricChatEffect {
    data class InitialMessageAccepted(
        val text: String,
    ) : KsenaxTemporaricChatEffect

    data object ExitToMain : KsenaxTemporaricChatEffect
}
