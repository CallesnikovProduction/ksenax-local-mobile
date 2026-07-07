package com.kolesnikovprod.ksetaorch.communication.tools.contracts

/**
 * Вызов tool-а, который парсер маршрутизации получил из ответа модели.
 *
 * После parser-а tool call становится typed-командой для orchestration-слоя:
 * найти executor по [name], проверить policy, при необходимости запросить
 * подтверждение и только потом выполнить действие.
 *
 * @property id id конкретного вызова. Нужен для связи call-а с результатом.
 * @property name имя tool-а из [KsenaxToolDefinition.name].
 * @property arguments JSON-object с аргументами конкретного вызова.
 * @property requiresConfirmation требует ли этот конкретный вызов подтверждения
 *           пользователя.
 * @property riskLevel риск, с которым parser или policy связывает вызов.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxToolCall(
    val id:                   String,
    val name:                 String,
    val arguments:            KsenaxToolArgumentsObject,
    val requiresConfirmation: Boolean,
    val riskLevel:            KsenaxToolRiskLevel,
)
