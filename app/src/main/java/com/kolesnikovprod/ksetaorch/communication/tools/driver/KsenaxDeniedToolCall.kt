package com.kolesnikovprod.ksetaorch.communication.tools.driver

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall

/**
 * Вызов инструмента, который драйвер отказался выполнять.
 *
 * Драйвер создаёт этот объект после проверки правил безопасности, прав
 * приложения или доступности инструмента. Сам вызов сохраняется, чтобы UI мог
 * показать, какое действие модель предложила и почему приложение его остановило.
 *
 * @property call вызов инструмента, который не дошёл до executor-а.
 * @property reason человекочитаемая причина отказа.
 * @property code машинный код отказа для логов и будущей обработки.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxDeniedToolCall(
    val call  : KsenaxToolCall,
    val reason: String,
    val code  : String,
)
