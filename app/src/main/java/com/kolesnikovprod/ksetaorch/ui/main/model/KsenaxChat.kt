package com.kolesnikovprod.ksetaorch.ui.main.model

/**
 * Снимок одного диалога, который можно напрямую отобразить в Compose.
 *
 * Объект хранит выбранный при создании режим и неизменяемый список сообщений.
 * Добавление сообщения создаёт новую копию [KsenaxChat], поэтому UI получает
 * предсказуемое обновление состояния без изменения списка на месте.
 *
 * Сейчас это presentation-модель в памяти: класс сам не сохраняет диалог на
 * диск и не управляет модельной сессией.
 *
 * @property id локальный идентификатор диалога.
 * @property mode режим общения, определяющий поведение и оформление ответов.
 * @property title короткое название диалога для списка чатов.
 * @property messages сообщения в порядке их появления.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxChat(
    val id:       Long,
    val mode:     ChatMode,
    val title:    String,
    val messages: List<KsenaxMessage>,
)

/**
 * Сообщение пользовательского или модельного происхождения внутри диалога.
 *
 * Класс содержит только данные, необходимые текущему экрану чата. Он не
 * кодирует состояние сетевого запроса, идентификатор tool-вызова или результат
 * выполнения действия.
 *
 * @property text текст, который отображается в чате.
 * @property isUser `true` для сообщения пользователя, `false` для ответа модели.
 * @property isFinalAgenticStep `true`, если ответ завершает отображаемую
 * последовательность шагов agentic-режима. Флаг не означает завершение диалога.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxMessage(
    val text:               String,
    val isUser:             Boolean = true,
    val generationDurationMillis: Long? = null,
    val isStreaming:        Boolean = false,
    val isFinalAgenticStep: Boolean = false,
)
