package com.kolesnikovprod.ksetaorch.ui.main.model

import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChat
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChatMode

internal fun KsenaxStoredChat.toPresentationChat(): KsenaxChat {
    return KsenaxChat(
        id       = id,
        mode     = when (mode) {
            KsenaxStoredChatMode.Basic   -> ChatMode.Basic
            KsenaxStoredChatMode.Agentic -> ChatMode.Agentic
        },
        title    = title,
        messages = messages.map { message ->
            KsenaxMessage(
                text                     = message.text,
                isUser                   = message.role == KsenaxMessageRole.User,
                generationDurationMillis = message.generationDurationMillis,
                isFinalAgenticStep       = message.isFinalAgenticStep,
            )
        },
    )
}
