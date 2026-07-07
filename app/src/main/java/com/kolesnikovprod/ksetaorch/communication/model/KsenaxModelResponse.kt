package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Сырой ответ локальной model-session.
 *
 * [text] содержит строку, которую вернул LiteRT-LM после `sendMessage`.
 * Для router-профиля это ещё не tool-call, refusal или clarification, а только
 * текст, который должен разобрать parser. Для chat-профиля это текст ответа
 * ассистента. Для voice transcription это распознанная речь.
 *
 * [latencyMs] показывает длительность конкретного модельного запроса в
 * миллисекундах. В текущей LiteRT-реализации замер начинается после
 * инициализации engine и включает создание conversation для запроса, отправку
 * payload и ожидание ответа. Cold start engine, parser, policy и tool execution
 * сюда не входят.
 *
 * [profile] фиксирует тип модельной задачи, которая породила этот ответ.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxModelResponse(
    val text:      String,
    val latencyMs: Long,
    val profile:   KsenaxModelTaskProfile,
)
