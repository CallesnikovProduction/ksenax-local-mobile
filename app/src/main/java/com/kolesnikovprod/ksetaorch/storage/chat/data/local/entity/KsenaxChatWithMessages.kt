package com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Результат Room-запроса, объединяющий чат со всеми принадлежащими ему
 * сообщениями.
 *
 * Room строит связь по `chats.id = chat_messages.chat_id`. Порядок элементов
 * в [messages] базой не гарантируется, поэтому repository дополнительно
 * сортирует их по позиции перед преобразованием в domain-модель.
 *
 * @property chat метаданные сохранённого чата.
 * @property messages сообщения, связанные с чатом внешним ключом.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxChatWithMessages(
    @Embedded
    val chat: KsenaxChatEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "chat_id",
    )
    val messages: List<KsenaxMessageEntity>,
)
