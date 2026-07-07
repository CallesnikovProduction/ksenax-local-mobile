package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic

import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxMessage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState

data class KsenaxAgenticChatUiState(
    val inputText: String = "",
    val chats: List<KsenaxChat> = emptyList(),
    val activeChatId: Long? = null,
    val workspaceTreeUri: String? = null,
    val workspaceDisplayPath: String = "",
    val transientUserText: String? = null,
    val isRunning: Boolean = false,
    val modelGateState: KsenaxBasicModelGateState = KsenaxBasicModelGateState.Idle,
    val errorMessage: String? = null,
) {
    val activeChat: KsenaxChat?
        get() {
            val storedChat = chats.firstOrNull { chat -> chat.id == activeChatId }
            val messages = buildList {
                addAll(storedChat?.messages.orEmpty())
                transientUserText?.takeIf(String::isNotBlank)?.let { text ->
                    if (lastOrNull()?.text != text || lastOrNull()?.isUser != true) {
                        add(KsenaxMessage(text = text))
                    }
                }
            }

            if (storedChat == null && messages.isEmpty()) return null

            return storedChat?.copy(messages = messages) ?: KsenaxChat(
                id = PendingChatId,
                mode = ChatMode.Agentic,
                title = transientUserText.orEmpty(),
                messages = messages,
            )
        }

    val isScreenBlocked: Boolean
        get() = modelGateState != KsenaxBasicModelGateState.Ready &&
            modelGateState != KsenaxBasicModelGateState.Idle

    private companion object {
        const val PendingChatId = Long.MIN_VALUE + 1L
    }
}

sealed interface KsenaxAgenticChatEffect {
    data class InitialMessageCommitted(val text: String) : KsenaxAgenticChatEffect
    data class DeleteChat(
        val chatId: Long,
        val returnToMain: Boolean,
    ) : KsenaxAgenticChatEffect
    data object ExitToMain : KsenaxAgenticChatEffect
}
