package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal

import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxBasicChatHistoryMessage
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxBasicChatRole
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredMessage

internal fun List<KsenaxStoredMessage>.toCoordinatorHistory():
        List<KsenaxBasicChatHistoryMessage> {
    return mapNotNull { message ->
        when (message.role) {
            KsenaxMessageRole.User      -> KsenaxBasicChatHistoryMessage(
                role = KsenaxBasicChatRole.User,
                text = message.text,
            )

            KsenaxMessageRole.Assistant -> KsenaxBasicChatHistoryMessage(
                role = KsenaxBasicChatRole.Assistant,
                text = message.text,
            )

            KsenaxMessageRole.System,
            KsenaxMessageRole.Tool      -> null
        }
    }
}