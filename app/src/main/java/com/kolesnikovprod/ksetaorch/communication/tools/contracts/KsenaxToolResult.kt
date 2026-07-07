package com.kolesnikovprod.ksetaorch.communication.tools.contracts

/**
 * Результат выполнения tool-а.
 *
 * Result связывается с исходным [KsenaxToolCall] через [callId] и [toolName].
 * UI или coordinator могут показать пользователю итог действия, записать
 * payload в лог или передать результат в следующий слой orchestration.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxToolResult {

    /**
     * Id исходного tool call-а.
     *
     * @since 0.2
     */
    val callId: String

    /**
     * Имя tool-а, который вернул результат.
     *
     * @since 0.2
     */
    val toolName: String

    /**
     * Tool выполнил действие.
     *
     * @property callId id исходного вызова.
     * @property toolName имя tool-а.
     * @property message короткое сообщение для пользователя.
     * @property payloadJson дополнительный машинный payload, если executor-у
     *           нужно вернуть данные выше по orchestration.
     *
     * @since 0.2
     */
    data class Success(
        override val callId:   String,
        override val toolName: String,
        val message:           String,
        val payloadJson:       String? = null,
    ) : KsenaxToolResult

    /**
     * Tool не смог выполнить действие.
     *
     * Failure подходит для ожидаемых ошибок: нет разрешения, аргументы не
     * прошли проверку, Android API недоступен или действие запрещено текущим
     * состоянием устройства.
     *
     * @property callId id исходного вызова.
     * @property toolName имя tool-а.
     * @property reason понятная причина для пользователя или логов.
     * @property errorCode стабильный код ошибки для UI, аналитики и тестов.
     *
     * @since 0.2
     */
    data class Failure(
        override val callId:   String,
        override val toolName: String,
        val reason:            String,
        val errorCode:         String,
    ) : KsenaxToolResult
}
