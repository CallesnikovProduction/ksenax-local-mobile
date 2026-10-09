package com.kolesnikovprod.ksetaorch.communication.work.planning

import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Фиксирует обязательные ограничения G4-планировщика.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class G4PlanningPromptFactoryTest {
    @Test fun `calendar metadata agrees with canonical preflight input`() {
        val executor = object : KsenaxToolExecutor {
            override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult =
                throw AssertionError("Metadata inspection must not execute an action")
        }
        val request = G4PlanningPromptFactory(CalendarEventOneShotToolModule(executor).actionSpecs)
            .buildPlanningRequest("Создай событие завтра в 18:00")
        assertTrue(request.prompt.contains("\"start_local_date_time\""))
        assertFalse(request.prompt.contains("\"start_local_date\""))
        assertFalse(request.prompt.contains("\"start_local_time\""))
    }

    @Test
    fun `prompt preserves bulk alarms note content and local calendar time`() {
        val request = G4PlanningPromptFactory(
            actionSpecs = listOf(
                KsenaxWorkActionSpec(
                    name = "alarm_after_hours",
                    description = "alarm",
                    inputHint = "hours and count",
                ),
                KsenaxWorkActionSpec(
                    name = "obsidian_note_write",
                    description = "note",
                    inputHint = "title and markdown_body",
                ),
                KsenaxWorkActionSpec(
                    name = "calendar_event_create",
                    description = "calendar",
                    inputHint = "start_local_date_time",
                ),
            )
        ).buildPlanningRequest(
            userText = "Создай заметку и событие",
            nowIso = "2026-07-07T12:00:00+03:00[Europe/Moscow]",
        )

        assertTrue(request.prompt.contains("bulk alarm request with count N is one alarm action"))
        assertTrue(request.prompt.contains("finished Markdown now"))
        assertTrue(request.prompt.contains("start_local_date_time"))
        assertTrue(request.prompt.contains("YYYY-07-08"))
        assertTrue(request.prompt.contains("Input is never a separate step"))
        assertTrue(request.prompt.contains("not a command, template, placeholder or promise"))
        assertTrue(request.prompt.contains("No Markdown fences"))
        assertTrue(!request.prompt.contains("\"instruction\""))
        assertTrue(request.prompt.endsWith("Создай заметку и событие"))
    }
}
