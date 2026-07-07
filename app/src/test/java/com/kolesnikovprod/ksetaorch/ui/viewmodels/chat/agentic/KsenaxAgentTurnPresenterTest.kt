package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.tools.driver.KsenaxToolOrchestrationResult
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnResult
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxAgentTurnPresenterTest {

    @Test
    fun noteToolGetsWorkspaceSpecificPipelineText() {
        val text = KsenaxAgentTurnPresenter.stageText(
            KsenaxAgentTurnStage.ExecutingTools(
                calls = listOf(
                    KsenaxToolCall(
                        id = "call-1",
                        name = "create_or_edit_markdown_note",
                        arguments = KsenaxToolArgumentsObject { "{}" },
                        requiresConfirmation = false,
                        riskLevel = KsenaxToolRiskLevel.MEDIUM,
                    ),
                ),
            ),
        )

        assertEquals("Сохраняю заметку в рабочей директории.", text)
    }

    @Test
    fun successfulToolResultIsMarkedAsSuccessful() {
        val text = KsenaxAgentTurnPresenter.resultText(
            KsenaxAgentTurnResult.ToolExecution(
                toolCalls = emptyList(),
                toolResult = KsenaxToolOrchestrationResult(
                    executed = listOf(
                        KsenaxToolResult.Success(
                            callId = "call-1",
                            toolName = "torch_tool",
                            message = "Фонарик включён.",
                        ),
                    ),
                    denied = emptyList(),
                    pendingConfirmation = emptyList(),
                ),
            ),
        )

        assertTrue(text.startsWith("Успешно."))
        assertTrue(text.contains("Фонарик включён."))
    }
}
