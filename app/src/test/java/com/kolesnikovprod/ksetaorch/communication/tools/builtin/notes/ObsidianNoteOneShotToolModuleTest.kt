package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianNoteOneShotToolModuleTest {

    @Test
    fun `resolveExecutableCall maps planner typo tilte to executor title`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        val call = module.resolveExecutableCall(
            userMessage = "запиши в заметку то что мы сейчас переходим на oneshot-архитектуру",
            step = KsenaxWorkPlanStep(
                id = "step_1",
                actionName = ObsidianNoteOneShot.Write.codeName,
                instruction = "Запиши мысль",
                plannerInputJson = """{"tilte":"OneShot migration","markdown_body":"Перехожу на oneshot-архитектуру."}""",
            ),
            compiledCall = KsenaxToolCall(
                id = "call_1",
                name = ObsidianNoteOneShot.Write.codeName,
                arguments = KsenaxRawToolArgumentsObject("{}"),
                requiresConfirmation = false,
                riskLevel = KsenaxToolRiskLevel.LOW,
            ),
        )

        val arguments = call.arguments.JSONtoString()
        assertTrue(arguments.contains(""""title":"Ежедневная заметка""""))
        assertTrue(arguments.contains(""""markdown_body":"Перехожу на oneshot-архитектуру.""""))
    }

    @Test
    fun `resolveExecutableCall falls back to detailed user note content when planner body is only command`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        val call = module.resolveExecutableCall(
            userMessage = "Создай и запиши заметку о том, как мы хорошо провели с Ксюшей время 5 июля, что было всё фантастически, и этот вечер я запомню надолго",
            step = KsenaxWorkPlanStep(
                id = "step_1",
                actionName = ObsidianNoteOneShot.Write.codeName,
                instruction = "Запиши заметку",
                plannerInputJson = """{"title":"agentic note","markdown_body":"Запиши заметку о фантастическом вечере с Ксюшей 5 июля"}""",
            ),
            compiledCall = KsenaxToolCall(
                id = "call_1",
                name = ObsidianNoteOneShot.Write.codeName,
                arguments = KsenaxRawToolArgumentsObject("{}"),
                requiresConfirmation = false,
                riskLevel = KsenaxToolRiskLevel.LOW,
            ),
        )

        val arguments = call.arguments.JSONtoString()
        assertTrue(arguments.contains(""""title":"Ежедневная заметка""""))
        assertTrue(arguments.contains("Ксюшей"))
        assertTrue(arguments.contains("5 июля"))
        assertTrue(arguments.contains("фантастически"))
        assertTrue(arguments.contains("запомню надолго"))
    }

    private object FakeExecutor : KsenaxToolExecutor {
        override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult =
            KsenaxToolResult.Success(
                callId = call.id,
                toolName = call.name,
                message = "ok",
            )
    }
}
