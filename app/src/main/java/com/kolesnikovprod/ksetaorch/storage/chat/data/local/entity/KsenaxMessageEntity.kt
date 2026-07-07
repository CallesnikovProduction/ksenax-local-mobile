package com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole

/**
 * Строка таблицы `chat_messages` с одним сообщением диалога.
 *
 * Внешний ключ на [KsenaxChatEntity] настроен с `CASCADE`: удаление чата
 * автоматически удаляет его сообщения. Составной уникальный индекс
 * `(chat_id, position)` не позволяет двум сообщениям занимать одно место
 * внутри одного чата.
 *
 * @property id локальный идентификатор сообщения, генерируемый Room.
 * @property chatId идентификатор родительского чата.
 * @property role роль автора сообщения в диалоге.
 * @property text текст сообщения.
 * @property position стабильная позиция сообщения внутри чата.
 * @property createdAtEpochMillis время создания в миллисекундах Unix epoch.
 * @property generationDurationMillis длительность генерации ответа модели.
 * @property isFinalAgenticStep отмечает финальный ответ агентного сценария.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = KsenaxChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["chat_id", "position"], unique = true),
    ],
)
data class KsenaxMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "chat_id")
    val chatId: Long,

    val role: KsenaxMessageRole,
    val text: String,
    val position: Long,

    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "generation_duration_millis")
    val generationDurationMillis: Long?,

    @ColumnInfo(name = "is_final_agentic_step")
    val isFinalAgenticStep: Boolean,
)
