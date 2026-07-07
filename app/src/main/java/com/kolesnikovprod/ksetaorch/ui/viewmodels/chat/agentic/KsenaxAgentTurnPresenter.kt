package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnResult
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnStage

internal object KsenaxAgentTurnPresenter {

    fun stageText(stage: KsenaxAgentTurnStage): String = when (stage) {
        KsenaxAgentTurnStage.RequestReceived -> "Получаю твой запрос."
        KsenaxAgentTurnStage.Planning -> "Планирую действия."
        KsenaxAgentTurnStage.CompilingAction -> "Готовлю короткий FunctionGemma-вызов."
        is KsenaxAgentTurnStage.ExecutingTools -> {
            when (stage.calls.firstOrNull()?.name) {
                "create_or_edit_markdown_note",
                "obsidian_note_write",
                "obsidian_note_append_analysis" ->
                    "Сохраняю заметку в рабочей директории."
                "torch_tool", "torch_on", "torch_off", "torch_toggle" -> "Переключаю фонарик."
                "alarm_tool",
                "alarm_at_time",
                "alarm_after_hours",
                "alarm_after_minutes",
                "alarm_at_date_time",
                "alarm_clear_all" -> "Создаю будильник."
                "calendar_event_tool" -> "Добавляю событие в календарь."
                else -> "Выполняю действие."
            }
        }
    }

    fun resultText(result: KsenaxAgentTurnResult): String = when (result) {
        is KsenaxAgentTurnResult.AssistantMessage ->
            result.message.ifBlank { "Ответ модели получен." }

        is KsenaxAgentTurnResult.Clarification -> result.question
        is KsenaxAgentTurnResult.Refusal -> result.message
        is KsenaxAgentTurnResult.ModelFailure ->
            "Не удалось обработать запрос: ${result.reason}"

        is KsenaxAgentTurnResult.ToolExecution -> {
            val lines = buildList {
                result.toolResult.executed.forEach { toolResult ->
                    add(
                        when (toolResult) {
                            is KsenaxToolResult.Success ->
                                "Успешно. " +
                                    toolResult.message.ifBlank { "Действие выполнено." }
                            is KsenaxToolResult.Failure ->
                                "Не удалось выполнить действие: ${toolResult.reason}"
                        },
                    )
                }
                result.toolResult.denied.forEach { denied ->
                    add("Действие отклонено: ${denied.reason}")
                }
                result.toolResult.pendingConfirmation.forEach { pending ->
                    add("Для действия нужно подтверждение: ${pending.reason}")
                }
            }
            lines.joinToString(separator = "\n").ifBlank { "Действие завершено." }
        }
    }
}
