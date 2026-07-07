package com.kolesnikovprod.ksetaorch.storage.chat.domain.model

/**
 * Независимая от UI модель сохранённого диалога.
 *
 * @property id постоянный идентификатор; `0` означает ещё не сохранённый чат.
 * @property mode режим работы модели для этого диалога.
 * @property title отображаемое название.
 * @property workspaceTreeUri SAF tree URI рабочей директории agentic-чата.
 * `null` у Basic-чата; для Agentic значение `null` выбирает default workspace
 * `Documents/ksenax-workspace`.
 * @property workspaceDisplayPath человекочитаемое имя рабочей директории.
 * @property messages упорядоченная история сообщений.
 * @property createdAtEpochMillis время создания в миллисекундах Unix epoch.
 * @property updatedAtEpochMillis время последнего изменения.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxStoredChat(
    val id:                   Long = 0L,
    val mode:                 KsenaxStoredChatMode,
    val title:                String,
    val workspaceTreeUri:     String? = null,
    val workspaceDisplayPath: String? = null,
    val messages:             List<KsenaxStoredMessage> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/**
 * Режим сохранённого диалога.
 *
 * Значения сохраняются в Room по имени enum-константы, поэтому их
 * переименование требует миграции уже записанных данных.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxStoredChatMode {
    /** Обычный диалог с локальной языковой моделью. */
    Basic,

    /** Агентный диалог с оркестрацией инструментов. */
    Agentic,
}
