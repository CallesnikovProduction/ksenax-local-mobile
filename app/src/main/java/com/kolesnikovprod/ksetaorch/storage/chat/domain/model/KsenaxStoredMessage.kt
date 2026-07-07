package com.kolesnikovprod.ksetaorch.storage.chat.domain.model

/**
 * Независимая от UI модель сообщения.
 *
 * Порядок сообщений задаётся их положением в [KsenaxStoredChat.messages].
 *
 * @property id постоянный идентификатор; `0` означает новое сообщение.
 * @property role роль участника диалога.
 * @property text текстовое содержимое.
 * @property createdAtEpochMillis время создания в миллисекундах Unix epoch.
 * @property generationDurationMillis длительность генерации ответа модели.
 * @property isFinalAgenticStep отмечает итоговый ответ агентного сценария.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxStoredMessage(
    val id:                   Long    = 0L,
    val role:                 KsenaxMessageRole,
    val text:                 String,
    val createdAtEpochMillis: Long,
    val generationDurationMillis: Long? = null,
    val isFinalAgenticStep:   Boolean = false,
)

/**
 * Роль сообщения в сохранённой истории.
 *
 * Значения сохраняются в Room по имени enum-константы, поэтому их
 * переименование требует миграции уже записанных данных.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxMessageRole {
    /** Запрос пользователя. */
    User,

    /** Ответ языковой модели. */
    Assistant,

    /** Системная инструкция или служебный контекст модели. */
    System,

    /** Результат выполнения инструмента. */
    Tool,
}
