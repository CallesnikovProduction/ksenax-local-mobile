package com.kolesnikovprod.ksetaorch.storage.chat.data

import androidx.room.withTransaction
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.KsenaxChatDatabase
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxChatEntity
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxChatWithMessages
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxMessageEntity
import com.kolesnikovprod.ksetaorch.storage.chat.domain.KsenaxChatRepository
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChat
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-реализация domain-контракта постоянного хранилища чатов.
 *
 * Repository изолирует остальное приложение от entity-моделей и SQL:
 * преобразует данные между Room и domain, сортирует сообщения по позиции и
 * объединяет составные записи в транзакции.
 *
 * @property database база, через которую выполняются запросы и транзакции.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class RoomKsenaxChatRepository(
    private val database: KsenaxChatDatabase,
) : KsenaxChatRepository {

    private val chatDao = database.chatDao()

    /**
     * Реактивный список сохранённых чатов с их сообщениями.
     *
     * @since 0.2
     */
    override val chats: Flow<List<KsenaxStoredChat>> =
        chatDao.observeChats().map { storedChats ->
            storedChats.map(KsenaxChatWithMessages::toDomain)
        }

    /**
     * Наблюдает за одним чатом по его локальному идентификатору.
     *
     * @since 0.2
     */
    override fun observeChat(chatId: Long): Flow<KsenaxStoredChat?> {
        return chatDao.observeChat(chatId).map { storedChat ->
            storedChat?.toDomain()
        }
    }

    /**
     * Атомарно создаёт или обновляет чат и полностью заменяет его сообщения.
     *
     * @return идентификатор сохранённого чата.
     *
     * @since 0.2
     */
    override suspend fun saveChat(chat: KsenaxStoredChat): Long {
        return database.withTransaction {
            val chatEntity = chat.toEntity()
            val chatId = if (chat.id == 0L) {
                chatDao.insertChat(chatEntity)
            } else {
                val updatedRows = chatDao.updateChat(chatEntity)
                if (updatedRows == 0) {
                    chatDao.insertChat(chatEntity)
                } else {
                    chat.id
                }
            }

            chatDao.deleteMessages(chatId)
            chatDao.insertMessages(
                chat.messages.mapIndexed { index, message ->
                    message.toEntity(
                        chatId = chatId,
                        position = index.toLong(),
                    )
                },
            )

            chatId
        }
    }

    /**
     * Атомарно добавляет сообщение в конец истории и обновляет время чата.
     *
     * @return идентификатор вставленного сообщения.
     *
     * @since 0.2
     */
    override suspend fun appendMessage(
        chatId: Long,
        message: KsenaxStoredMessage,
    ): Long {
        return database.withTransaction {
            val position = chatDao.nextMessagePosition(chatId)
            val messageId = chatDao.insertMessage(
                message.toEntity(chatId, position)
            )
            chatDao.updateChatTimestamp(
                chatId,
                updatedAtEpochMillis = message.createdAtEpochMillis,
            )
            messageId
        }
    }

    /**
     * Переименовывает чат отдельным SQL update, сохраняя историю и message ID.
     *
     * @since 0.2
     */
    override suspend fun renameChat(
        chatId: Long,
        title: String,
        updatedAtEpochMillis: Long,
    ) {
        chatDao.renameChat(
            chatId = chatId,
            title = title,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    /**
     * Удаляет чат вместе с сообщениями благодаря внешнему ключу `CASCADE`.
     *
     * @since 0.2
     */
    override suspend fun deleteChat(chatId: Long) {
        chatDao.deleteChat(chatId)
    }
}

private fun KsenaxStoredChat.toEntity(): KsenaxChatEntity {
    return KsenaxChatEntity(
        id                   = id,
        mode                 = mode,
        title                = title,
        workspaceTreeUri     = workspaceTreeUri,
        workspaceDisplayPath = workspaceDisplayPath,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun KsenaxStoredMessage.toEntity(
    chatId  : Long,
    position: Long,
): KsenaxMessageEntity {
    return KsenaxMessageEntity(
        id                   = id,
        chatId               = chatId,
        role                 = role,
        text                 = text,
        position             = position,
        createdAtEpochMillis = createdAtEpochMillis,
        generationDurationMillis = generationDurationMillis,
        isFinalAgenticStep   = isFinalAgenticStep,
    )
}

private fun KsenaxChatWithMessages.toDomain(): KsenaxStoredChat {
    return KsenaxStoredChat(
        id                   = chat.id,
        mode                 = chat.mode,
        title                = chat.title,
        workspaceTreeUri     = chat.workspaceTreeUri,
        workspaceDisplayPath = chat.workspaceDisplayPath,
        messages             = messages
            .sortedBy(KsenaxMessageEntity::position)
            .map(KsenaxMessageEntity::toDomain),
        createdAtEpochMillis = chat.createdAtEpochMillis,
        updatedAtEpochMillis = chat.updatedAtEpochMillis,
    )
}

private fun KsenaxMessageEntity.toDomain(): KsenaxStoredMessage {
    return KsenaxStoredMessage(
        id                   = id,
        role                 = role,
        text                 = text,
        createdAtEpochMillis = createdAtEpochMillis,
        generationDurationMillis = generationDurationMillis,
        isFinalAgenticStep   = isFinalAgenticStep,
    )
}
