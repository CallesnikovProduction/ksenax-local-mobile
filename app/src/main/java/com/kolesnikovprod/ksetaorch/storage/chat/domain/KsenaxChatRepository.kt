package com.kolesnikovprod.ksetaorch.storage.chat.domain

import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChat
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredMessage
import kotlinx.coroutines.flow.Flow

/**
 * Контракт постоянного хранилища чатов.
 *
 * Domain-слой не знает, используется ли Room, файл или другая реализация.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxChatRepository {

    /**
     * Реактивный список всех сохранённых чатов.
     *
     * @since 0.2
     */
    val chats: Flow<List<KsenaxStoredChat>>

    /**
     * Наблюдает за одним чатом или возвращает `null`, если он не найден.
     *
     * @since 0.2
     */
    fun observeChat(chatId: Long): Flow<KsenaxStoredChat?>

    /**
     * Создаёт новый чат или сохраняет полное состояние существующего.
     *
     * @return постоянный идентификатор сохранённого чата.
     *
     * @since 0.2
     */
    suspend fun saveChat(chat: KsenaxStoredChat): Long

    /**
     * Добавляет сообщение в конец существующего чата.
     *
     * @return постоянный идентификатор сохранённого сообщения.
     *
     * @since 0.2
     */
    suspend fun appendMessage(chatId: Long, message: KsenaxStoredMessage): Long

    /**
     * Переименовывает существующий чат без перезаписи его сообщений.
     *
     * @since 0.2
     */
    suspend fun renameChat(
        chatId: Long,
        title: String,
        updatedAtEpochMillis: Long,
    )

    /**
     * Удаляет чат и принадлежащую ему историю сообщений.
     *
     * @since 0.2
     */
    suspend fun deleteChat(chatId: Long)
}
