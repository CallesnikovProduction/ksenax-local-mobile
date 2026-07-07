package com.kolesnikovprod.ksetaorch.communication.tools.driver

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult

/**
 * Итог работы драйвера инструментов за один агентный ход.
 *
 * Объект разделяет три исхода: инструмент выполнился, вызов запрещён, вызов
 * ждёт подтверждения. Coordinator упаковывает этот результат в
 * `KsenaxAgentTurnResult.ToolExecution`, а ViewModel переводит его в состояние
 * экрана.
 *
 * @property executed результаты реально выполненных инструментов.
 * @property denied вызовы, которые приложение запретило выполнять.
 * @property pendingConfirmation вызовы, которые ждут подтверждения пользователя.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxToolOrchestrationResult(
    val executed           : List<KsenaxToolResult>,
    val denied             : List<KsenaxDeniedToolCall>,
    val pendingConfirmation: List<KsenaxPendingToolCall>,
)
