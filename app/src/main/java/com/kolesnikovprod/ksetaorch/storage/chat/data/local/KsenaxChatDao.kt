package com.kolesnikovprod.ksetaorch.storage.chat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxChatEntity
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxChatWithMessages
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * SQL-интерфейс Room для таблиц чатов и сообщений.
 *
 * Room генерирует реализацию этого интерфейса на этапе сборки. Аннотированные
 * методы здесь играют роль типизированных запросов: параметры Kotlin
 * подставляются в SQL, а результаты преобразуются в entity-классы и [Flow].
 *
 * Составные операции, которым нужна атомарность, собираются в транзакции на
 * уровне [RoomKsenaxChatRepository][com.kolesnikovprod.ksetaorch.storage.chat.data.RoomKsenaxChatRepository].
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Dao
interface KsenaxChatDao {

    /**
     * Наблюдает за всеми чатами с сообщениями, начиная с недавно изменённых.
     *
     * @since 0.2
     */
    @Transaction
    @Query("SELECT * FROM chats ORDER BY updated_at_epoch_millis DESC")
    fun observeChats(): Flow<List<KsenaxChatWithMessages>>

    /**
     * Наблюдает за одним чатом или возвращает `null`, если такой строки нет.
     *
     * @since 0.2
     */
    @Transaction
    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun observeChat(chatId: Long): Flow<KsenaxChatWithMessages?>

    /**
     * Вставляет чат и возвращает его идентификатор.
     *
     * @since 0.2
     */
    @Insert
    suspend fun insertChat(chat: KsenaxChatEntity): Long

    /**
     * Обновляет чат по первичному ключу и возвращает число изменённых строк.
     *
     * @since 0.2
     */
    @Update
    suspend fun updateChat(chat: KsenaxChatEntity): Int

    /**
     * Меняет только заголовок и время обновления, не затрагивая сообщения.
     *
     * @since 0.2
     */
    @Query(
        """
        UPDATE chats
        SET title = :title,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :chatId
        """
    )
    suspend fun renameChat(
        chatId: Long,
        title: String,
        updatedAtEpochMillis: Long,
    )

    /**
     * Удаляет чат; связанные сообщения удаляются внешним ключом `CASCADE`.
     *
     * @since 0.2
     */
    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)

    /**
     * Удаляет все сообщения чата перед полной заменой его истории.
     *
     * @since 0.2
     */
    @Query("DELETE FROM chat_messages WHERE chat_id = :chatId")
    suspend fun deleteMessages(chatId: Long)

    /**
     * Вставляет список сообщений.
     *
     * @since 0.2
     */
    @Insert
    suspend fun insertMessages(messages: List<KsenaxMessageEntity>)

    /**
     * Вставляет одно сообщение и возвращает его идентификатор.
     *
     * @since 0.2
     */
    @Insert
    suspend fun insertMessage(message: KsenaxMessageEntity): Long

    /**
     * Вычисляет следующую свободную позицию в истории указанного чата.
     *
     * Для пустой истории возвращает `0`.
     *
     * @since 0.2
     */
    @Query(
        """
        SELECT COALESCE(MAX(position), -1) + 1
        FROM chat_messages
        WHERE chat_id = :chatId
        """
    )
    suspend fun nextMessagePosition(chatId: Long): Long

    /**
     * Обновляет время последнего изменения чата после добавления сообщения.
     *
     * @since 0.2
     */
    @Query(
        """
        UPDATE chats
        SET updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :chatId
        """
    )
    suspend fun updateChatTimestamp(chatId: Long, updatedAtEpochMillis: Long)
}
