package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Проверяет передачу содержимого заметки мимо короткого FG prompt.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class ObsidianNoteOneShotToolModuleTest {
    @Test fun `polite discussion of writing is not permission to write`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        assertThrows(IllegalArgumentException::class.java) {
            module.validateSourceCall("Пожалуйста, расскажи, как создать заметку", ObsidianNoteOneShot.Write.codeName)
        }
    }

    @Test fun `verbs in note topic do not require another operation`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        val text = "Создай заметку о том, как проанализировать текст"
        val call = KsenaxToolCall("s1", ObsidianNoteOneShot.Write.codeName, KsenaxRawToolArgumentsObject("{}"), false, KsenaxToolRiskLevel.LOW)
        module.validateSourceCall(text, call.name)
        module.validatePlannedInputs(text, java.time.ZonedDateTime.now(), listOf(call))
        assertThrows(IllegalArgumentException::class.java) { module.validateSourceCall(text, ObsidianNoteOneShot.AppendAnalysis.codeName) }
    }

    @Test fun `new analysis command after note topic must remain in plan`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        val text = "Создай заметку об архитектуре и проанализируй заметку"
        val write = KsenaxToolCall("s1", ObsidianNoteOneShot.Write.codeName, KsenaxRawToolArgumentsObject("{}"), false, KsenaxToolRiskLevel.LOW)
        val analysis = write.copy(id = "s2", name = ObsidianNoteOneShot.AppendAnalysis.codeName)
        module.validateSourceCall(text, write.name)
        module.validateSourceCall(text, analysis.name)
        module.validatePlannedInputs(text, java.time.ZonedDateTime.now(), listOf(write, analysis))
        assertThrows(IllegalArgumentException::class.java) { module.validatePlannedInputs(text, java.time.ZonedDateTime.now(), listOf(write)) }
    }

    @Test fun `template placeholder is not generated note content`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        assertThrows(IllegalArgumentException::class.java) {
            module.resolveExecutableCall("Создай заметку про архитектуру", KsenaxWorkPlanStep("s1", "obsidian_note_write",
                "Создай заметку", """{"title":"Архитектура","markdown_body":"# Архитектура\n\n[Здесь будет полный Markdown-текст]"}"""),
                KsenaxToolCall("s1", "obsidian_note_write", KsenaxRawToolArgumentsObject("{}"), false, KsenaxToolRiskLevel.LOW))
        }
    }

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
    fun `planner command cannot masquerade as generated note body`() {
        val module = ObsidianNoteOneShotToolModule(FakeExecutor)
        assertThrows(IllegalArgumentException::class.java) { module.resolveExecutableCall(
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
        ) }
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
