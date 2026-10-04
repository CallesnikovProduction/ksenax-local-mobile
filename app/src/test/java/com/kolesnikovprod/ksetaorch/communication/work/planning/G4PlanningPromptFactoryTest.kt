package com.kolesnikovprod.ksetaorch.communication.work.planning

import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Фиксирует обязательные ограничения G4-планировщика.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class G4PlanningPromptFactoryTest {

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
        assertTrue(request.prompt.contains("complete requested content"))
        assertTrue(request.prompt.contains("start_local_date_time"))
        assertTrue(request.prompt.contains("YYYY-07-08"))
        assertTrue(request.prompt.endsWith("Создай заметку и событие"))
    }
}
