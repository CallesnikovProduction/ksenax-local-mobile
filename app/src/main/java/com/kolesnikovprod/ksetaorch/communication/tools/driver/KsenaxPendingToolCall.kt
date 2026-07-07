package com.kolesnikovprod.ksetaorch.communication.tools.driver

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall

/**
 * Вызов инструмента, который ждёт подтверждения пользователя.
 *
 * Драйвер кладёт сюда действие, если модель выбрала инструмент, но policy-слой
 * требует явного согласия перед запуском. После подтверждения ViewModel может
 * повторить ход с обновлённым `KsenaxPolicyContext`.
 *
 * @property call вызов инструмента, который пока не выполнен.
 * @property reason причина, по которой нужно подтверждение.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxPendingToolCall(
    val call  : KsenaxToolCall,
    val reason: String,
)
