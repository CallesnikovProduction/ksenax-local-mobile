package com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChatMode

/**
 * Строка таблицы `chats` с метаданными одного сохранённого диалога.
 *
 * Сообщения не встраиваются в эту сущность: они хранятся отдельно в
 * `chat_messages` и связываются с чатом через его [id].
 *
 * @property id локальный идентификатор чата, генерируемый Room при вставке.
 * @property mode режим работы модели, в котором создан чат.
 * @property title отображаемое название чата.
 * @property createdAtEpochMillis время создания в миллисекундах Unix epoch.
 * @property updatedAtEpochMillis время последнего изменения; используется для
 * сортировки списка чатов.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Entity(tableName = "chats")
data class KsenaxChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val mode: KsenaxStoredChatMode,
    val title: String,

    @ColumnInfo(name = "workspace_tree_uri")
    val workspaceTreeUri: String? = null,

    @ColumnInfo(name = "workspace_display_path")
    val workspaceDisplayPath: String? = null,

    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "updated_at_epoch_millis", index = true)
    val updatedAtEpochMillis: Long,
)
