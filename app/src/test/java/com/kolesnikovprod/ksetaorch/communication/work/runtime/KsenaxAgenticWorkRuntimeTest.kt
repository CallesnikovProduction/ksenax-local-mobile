package com.kolesnikovprod.ksetaorch.communication.work.runtime

import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.ObsidianNoteOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.*
import com.kolesnikovprod.ksetaorch.communication.tools.policy.KsenaxPolicyContext
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnResult
import java.time.ZonedDateTime
import java.util.ArrayDeque
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Проверяет маршруты, входы и отсутствие побочных эффектов через модельные подмены.
 * Не является оценкой качества реальной FG или времени работы телефона.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class KsenaxAgenticWorkRuntimeTest {
    @Test fun `flashlight fact never authorizes an action`() = runBlocking {
        assertFactRejected("Фонарик уже включён", "torch_on", "{}")
    }
    @Test fun `alarm fact never authorizes an action`() = runBlocking {
        assertFactRejected("Будильник «Включи фонарик» уже поставлен на завтра на 07:00", "alarm_at_date_time",
            """{"date_time":"2026-07-08T07:00","count":1}""")
        listOf("Будильник на 06:15 уже стоит", "Будильник на 06:15 сработает завтра").forEach { text ->
            assertFactRejected(text, "alarm_at_time", """{"time":"06:15","count":1}""")
        }
    }
    @Test fun `calendar fact never authorizes an action`() = runBlocking {
        assertFactRejected("Встреча с Димой завтра в 18:00", "calendar_event_create",
            """{"title":"Встреча с Димой","start_local_date_time":"2026-07-08T18:00"}""")
    }
    @Test fun `negated wake intention does not authorize an alarm`() = runBlocking {
        listOf("Не хочу проснуться завтра в семь", "Мне не нужно проснуться завтра в семь").forEach { text ->
            assertFactRejected(text, "alarm_at_date_time", """{"date_time":"2026-07-08T07:00","count":1}""")
        }
    }
    @Test fun `need for another object does not authorize a mentioned tool`() = runBlocking {
        assertFactRejected("Мне нужен зарядник, фонарик у меня есть", "torch_toggle", "{}")
        assertFactRejected("Мне нужен зарядник, будильник стоит на 06:15", "alarm_at_time", """{"time":"06:15","count":1}""")
        assertFactRejected("Мне нужен зарядник, встреча с Димой завтра в 18:00", "calendar_event_create",
            """{"title":"Встреча с Димой","start_local_date_time":"2026-07-08T18:00"}""")
    }
    @Test fun `explicit alarm need keeps cardinal quantity modifiers`() = runBlocking {
        mapOf("5" to 5, "пять" to 5, "двадцать пять" to 25).forEach { (quantity, count) ->
            val text = "Мне нужны $quantity будильников через 10 часов"
            val args = """{"hours":10,"count":$count}"""
            val executor = RecordingExecutor()
            val result = runtime(RecordingModelSession(listOf(proposal("alarm_after_hours", args))), RecordingModelSession(), executor)
                .handleUserText(text)
            assertTrue(text, result is KsenaxAgentTurnResult.ToolExecution)
            assertEquals(count, Json.parseToJsonElement(executor.calls.single().arguments.JSONtoString())
                .jsonObject.getValue("count").jsonPrimitive.int)
        }
    }
    private suspend fun assertFactRejected(text: String, name: String, args: String) {
        listOf(false, true).forEach { planned ->
            val action = RecordingModelSession(if (planned) listOf(proposal("route_planned_work"), proposal(name, args)) else listOf(proposal(name, args)))
            val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"action":"$name","input":$args}]}"""))
            val executor = RecordingExecutor()
            assertTrue(runtime(action, planner, executor).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `planned route without any source permission stops before G4 initialization`() = runBlocking {
        listOf("Расскажи про фонарик Android", "Открой существующую заметку тест", "Запиши это",
            "Пожалуйста, объясни, как сохранить заметку в Obsidian").forEach { text ->
            val action = RecordingModelSession(listOf(proposal("route_planned_work")))
            val planner = RecordingModelSession()
            val executor = RecordingExecutor()
            val result = runtime(action, planner, executor).handleUserText(text)
            assertTrue(result is KsenaxAgentTurnResult.Clarification)
            assertEquals(1, action.requests.size)
            assertEquals(0, planner.initializationCount)
            assertTrue(planner.statelessPrompts.isEmpty())
            assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `missing or ambiguous source values stop before G4 initialization`() = runBlocking {
        listOf("Создай событие на завтра", "Запиши созвон", "Добавь встречу в календарь",
            "Поставь будильник", "Разбуди меня завтра", "Поставь будильник на 7:00 или 8:00").forEach { text ->
            val action = RecordingModelSession(listOf(proposal("route_planned_work")))
            val planner = RecordingModelSession()
            val executor = RecordingExecutor()
            val result = runtime(action, planner, executor).handleUserText(text)
            assertTrue(text, result is KsenaxAgentTurnResult.Clarification)
            assertEquals(text, 0, planner.initializationCount)
            assertTrue(text, planner.statelessPrompts.isEmpty())
            assertEquals(text, 1, action.requests.size)
            assertTrue(text, executor.calls.isEmpty())
        }
    }

    @Test fun `incomplete hardware command stops mixed work before G4 initialization`() = runBlocking {
        listOf("Создай заметку об Android и создай встречу на завтра",
            "Включи фонарик и поставь будильник").forEach { text ->
            val action = RecordingModelSession(listOf(proposal("route_planned_work")))
            val planner = RecordingModelSession()
            val executor = RecordingExecutor()
            val result = runtime(action, planner, executor).handleUserText(text)
            assertTrue(text, result is KsenaxAgentTurnResult.Clarification)
            assertEquals(text, 0, planner.initializationCount)
            assertTrue(text, planner.statelessPrompts.isEmpty())
            assertTrue(text, executor.calls.isEmpty())
        }
    }

    @Test fun `complete calendar and alarm source still reaches G4 and both FG calls`() = runBlocking {
        val calendar = """{"title":"Встреча","start_local_date_time":"2026-07-08T18:00"}"""
        val planner = RecordingModelSession(statelessResponses = listOf(
            """{"type":"plan","steps":[{"action":"alarm_at_time","input":{"time":"07:00"}},{"action":"calendar_event_create","input":$calendar}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"),
            proposal("alarm_at_time", """{"time":"07:00"}"""), proposal("calendar_event_create", calendar)))
        val executor = RecordingExecutor()
        val result = runtime(action, planner, executor).handleUserText("Поставь будильник на 7:00 и создай встречу завтра в 18:00")
        assertTrue(result is KsenaxAgentTurnResult.ToolExecution)
        assertEquals(1, planner.initializationCount)
        assertEquals(1, planner.statelessPrompts.size)
        assertEquals(3, action.requests.size)
        assertEquals(listOf("alarm_at_time", "calendar_event_create"), executor.calls.map { it.name })
    }

    @Test fun `planned alarm time must agree with source even when both times are in range`() = runBlocking {
        val at = current.withHour(20)
        assertPlannedInputRejected("Разбуди меня завтра в семь", listOf("alarm_at_date_time" to
            """{"date_time":"2026-07-08T19:00"}"""), at)
        val executor = RecordingExecutor()
        val planner = RecordingModelSession(statelessResponses = listOf(
            """{"type":"plan","steps":[{"action":"alarm_at_date_time","input":{"date_time":"2026-07-08T07:00"}}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("alarm_at_date_time",
            """{"date_time":"2026-07-08T07:00"}""")))
        assertTrue(runtime(action, planner, executor, clock = at).handleUserText("Разбуди меня завтра в семь") is KsenaxAgentTurnResult.ToolExecution)
        assertEquals(1, executor.calls.size)
    }
    @Test fun `planned alarm cannot swap count amount or unit`() = runBlocking {
        listOf("alarm_after_hours" to """{"hours":10,"count":1}""",
            "alarm_after_hours" to """{"hours":5,"count":5}""",
            "alarm_after_minutes" to """{"minutes":600,"count":5}""").forEach { wrong ->
            assertPlannedInputRejected("Поставь 5 будильников через 10 часов", listOf(wrong))
        }
    }
    @Test fun `planned alarms consume distinct source commands without dropping or repeating one`() = runBlocking {
        val text = "Поставь будильник на 7:00 и поставь будильник на 8:00"
        val seven = "alarm_at_time" to """{"time":"07:00"}"""
        assertPlannedInputRejected(text, listOf(seven, seven))
        assertPlannedInputRejected(text, listOf(seven))
        val executor = RecordingExecutor()
        val planner = RecordingModelSession(statelessResponses = listOf(
            """{"type":"plan","steps":[{"action":"alarm_at_time","input":{"time":"07:00"}},{"action":"alarm_at_time","input":{"time":"08:00"}}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("alarm_at_time", seven.second),
            proposal("alarm_at_time", """{"time":"08:00"}""")))
        assertTrue(runtime(action, planner, executor).handleUserText(text) is KsenaxAgentTurnResult.ToolExecution)
        assertEquals(2, executor.calls.size)
    }
    @Test fun `planned calendar cannot borrow alarm time and later error prevents earlier execution`() = runBlocking {
        assertPlannedInputRejected("Поставь будильник на 7:00 и создай встречу завтра в 18:00", listOf(
            "alarm_at_time" to """{"time":"07:00"}""",
            "calendar_event_create" to """{"title":"Встреча","start_local_date_time":"2026-07-08T07:00"}"""))
    }
    @Test fun `planned calendar cannot invent required time`() = runBlocking {
        assertPlannedInputRejected("Создай встречу завтра", listOf("calendar_event_create" to
            """{"title":"Встреча","start_local_date_time":"2026-07-08T18:00"}"""))
    }
    @Test fun `ambiguous source time cannot be replaced by first matching planned time`() = runBlocking {
        assertPlannedInputRejected("Поставь будильник на 7:00 или 8:00", listOf("alarm_at_time" to """{"time":"07:00"}"""))
    }
    @Test fun `ambiguous or invalid alarm date cannot degrade to an undated clock`() = runBlocking {
        listOf("Поставь будильник завтра или послезавтра в 07:00",
            "Поставь будильник 2026-99-99 в 07:00").forEach { text ->
            assertPlannedInputRejected(text, listOf("alarm_at_time" to """{"time":"07:00"}"""))
            val executor = RecordingExecutor()
            assertTrue(runtime(RecordingModelSession(listOf(proposal("alarm_at_time", """{"time":"07:00"}"""))),
                RecordingModelSession(), executor).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }
    @Test fun `unparsed relative alarm interval cannot degrade to a clock`() = runBlocking {
        val executor = RecordingExecutor()
        assertTrue(runtime(RecordingModelSession(listOf(proposal("alarm_at_time", """{"time":"05:00"}"""))),
            RecordingModelSession(), executor).handleUserText("Поставь будильник через 1.5 часа") is KsenaxAgentTurnResult.Clarification)
        assertTrue(executor.calls.isEmpty())
    }
    @Test fun `planned source date is fixed before inference crosses midnight`() = runBlocking {
        var clock = ZonedDateTime.parse("2026-07-07T23:59:59+03:00[Europe/Moscow]")
        val args = """{"title":"Тест","start_local_date_time":"2026-07-08T18:00"}"""
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("calendar_event_create", args)))
        action.beforeResponse = { clock = ZonedDateTime.parse("2026-07-08T00:00:02+03:00[Europe/Moscow]") }
        val planner = RecordingModelSession(statelessResponses = listOf(
            """{"type":"plan","steps":[{"action":"calendar_event_create","input":$args}]}"""))
        val executor = RecordingExecutor()
        val runtime = KsenaxAgenticWorkRuntime(planner, action, listOf(CalendarEventOneShotToolModule(executor)), now = { clock })
        assertTrue(runtime.handleUserText("Создай событие завтра в 18:00 — тест") is KsenaxAgentTurnResult.ToolExecution)
        assertEquals(1, executor.calls.size)
    }
    @Test fun `dotted clock remains intact across source command boundaries`() = runBlocking {
        assertPlannedInputRejected("Разбуди завтра в 07.30", listOf("alarm_at_date_time" to
            """{"date_time":"2026-07-08T07:00"}"""))
    }
    @Test fun `relative alarm interval cannot silently discard explicit clock or date`() = runBlocking {
        listOf("Разбуди завтра в семь через 10 минут", "Разбуди в 07:00 через 10 минут").forEach { text ->
            assertPlannedInputRejected(text, listOf("alarm_after_minutes" to """{"minutes":10}"""))
        }
    }
    @Test fun `planned alarm commands cannot reverse their source order`() = runBlocking {
        assertPlannedInputRejected("Поставь будильник через 5 минут, затем очисти все будильники", listOf(
            "alarm_clear_all" to "{}", "alarm_after_minutes" to """{"minutes":5}"""))
    }
    @Test fun `planned torch commands cannot be dropped duplicated or reordered`() = runBlocking {
        listOf("включи фонарь, затем выключи фонарь", "включи затем выключи фонарь").forEach { text ->
            assertPlannedInputRejected(text, listOf("torch_off" to "{}", "torch_on" to "{}"))
            assertPlannedInputRejected(text, listOf("torch_on" to "{}"))
        }
        assertPlannedInputRejected("Включи фонарь", listOf("torch_on" to "{}", "torch_on" to "{}"))
    }
    @Test fun `planner cannot create a note from a question fact or negation`() = runBlocking {
        listOf("Как работает будильник?", "Расскажи про фонарик Android", "Я создал заметку вчера",
            "Не напиши заметку про архитектуру").forEach { text ->
            assertPlannedInputRejected(text, listOf("obsidian_note_write" to
                """{"title":"Тест","markdown_body":"Подробное описание устройства будильника."}"""))
        }
    }
    @Test fun `planner cannot omit requested note from mixed work or substitute analysis`() = runBlocking {
        assertPlannedInputRejected("Включи фонарик и создай заметку что мы проверяем архитектуру", listOf("torch_on" to "{}"))
        assertPlannedInputRejected("Создай заметку про архитектуру", listOf("obsidian_note_append_analysis" to
            """{"title":"Тест","analysis_markdown":"Подробный анализ архитектуры приложения."}"""))
    }
    @Test fun `planned calendar cannot swap explicit duration minutes`() = runBlocking {
        assertPlannedInputRejected("Создай встречу завтра в семь на 30 минут", listOf("calendar_event_create" to
            """{"title":"Встреча","start_local_date_time":"2026-07-08T07:00","duration_minutes":300}"""))
        val executor = RecordingExecutor()
        val args = """{"title":"Встреча","start_local_date_time":"2026-07-08T07:00","duration_minutes":30}"""
        val planner = RecordingModelSession(statelessResponses = listOf(
            """{"type":"plan","steps":[{"action":"calendar_event_create","input":$args}]}"""))
        assertTrue(runtime(RecordingModelSession(listOf(proposal("route_planned_work"), proposal("calendar_event_create", args))),
            planner, executor).handleUserText("Создай встречу завтра в семь на тридцать минут") is KsenaxAgentTurnResult.ToolExecution)
        assertEquals(1, executor.calls.size)
    }
    private suspend fun assertPlannedInputRejected(text: String, steps: List<Pair<String, String>>, clock: ZonedDateTime = current) {
        val executor = RecordingExecutor()
        val stepJson = steps.joinToString(",") { (name, input) -> """{"action":"$name","input":$input}""" }
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[$stepJson]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work")) + steps.map { proposal(it.first, it.second) })
        assertTrue(text, runtime(action, planner, executor, clock = clock).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
        assertTrue(text, executor.calls.isEmpty())
        assertEquals(text, 1, action.requests.size)
    }
    @Test fun `alarm dismissal cannot borrow verb from flashlight clause`() = runBlocking {
        listOf(" и ", ", ", ",", "; ", " а ещё ").forEach { separator ->
            assertPlannedSourceRejected("Отключи фонарик${separator}поставь все три будильника через 5 минут", "alarm_clear_all")
        }
    }
    @Test fun `flashlight direction cannot borrow verb from alarm clause`() = runBlocking {
        listOf(" и ", ", ", ",", "; ", " а ещё ").forEach { separator ->
            assertPlannedSourceRejected("Включи фонарик${separator}отключи все будильники", "torch_off")
        }
    }
    @Test fun `ordinary planned alarm cannot authorize dismiss all`() = runBlocking {
        assertPlannedSourceRejected("Создай заметку и поставь будильник через 5 минут", "alarm_clear_all")
    }
    @Test fun `planned flashlight direction cannot contradict explicit source`() = runBlocking {
        assertPlannedSourceRejected("Выключи фонарик и поставь будильник через 5 минут", "torch_on")
    }
    @Test fun `note thought content never authorizes planned hardware`() = runBlocking {
        assertPlannedSourceRejected("Запиши мысль: завтра нужно включить фонарик", "torch_on")
    }
    private suspend fun assertPlannedSourceRejected(text: String, name: String) {
        val executor = RecordingExecutor()
        val planner = RecordingModelSession(statelessResponses = listOf(
            """{"type":"plan","steps":[{"action":"$name","input":{}}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal(name)))
        assertTrue(text, runtime(action, planner, executor).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
        assertTrue(text, executor.calls.isEmpty())
    }
    @Test fun `negated flashlight synonyms never authorize a model proposal`() = runBlocking {
        listOf("Не зажигай фонарик", "Не зажги фонарь", "Не погаси фонарь", "Не гаси фонарик",
            "Не выруби фонарик", "Не врубай фонарик").forEach { text ->
            val executor = RecordingExecutor()
            val direct = runtime(RecordingModelSession(listOf(proposal("torch_on"))), RecordingModelSession(), executor)
            assertTrue(text, direct.handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            val planner = RecordingModelSession(statelessResponses = listOf(
                """{"type":"plan","steps":[{"action":"torch_on","input":{}}]}"""))
            val planned = runtime(RecordingModelSession(listOf(proposal("route_planned_work"))), planner, executor)
            assertTrue(text, planned.handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            assertTrue(text, executor.calls.isEmpty())
        }
    }
    @Test fun `request date is fixed before inference crosses midnight`() = runBlocking {
        var clock = ZonedDateTime.parse("2026-07-07T23:59:59+03:00[Europe/Moscow]")
        val action = RecordingModelSession(listOf(proposal("calendar_event_create",
            """{"title":"Тест","start_local_date_time":"2026-07-08T18:00"}""")))
        action.beforeResponse = { clock = ZonedDateTime.parse("2026-07-08T00:00:02+03:00[Europe/Moscow]") }
        val executor = RecordingExecutor()
        val runtime = KsenaxAgenticWorkRuntime(RecordingModelSession(), action,
            listOf(CalendarEventOneShotToolModule(executor)), now = { clock })
        runtime.handleUserText("Создай событие завтра в 18:00 — тест")
        assertEquals("2026-07-08T18:00", Json.parseToJsonElement(executor.calls.single().arguments.JSONtoString())
            .jsonObject.getValue("start_local_date_time").jsonPrimitive.content)
    }
    @Test fun `contradictory FG choice gets one model refinement within its kit`() = runBlocking {
        val executor = RecordingExecutor()
        val action = RecordingModelSession(listOf(proposal("torch_on"), proposal("torch_off")))
        val result = runtime(action, RecordingModelSession(), executor).handleUserText("Выключи фонарик")
        assertTrue(result is KsenaxAgentTurnResult.ToolExecution)
        assertEquals(listOf("torch_off"), executor.calls.map { it.name })
        assertEquals(setOf("torch_on", "torch_off", "torch_toggle"), action.requests.last().functions.map { it.name }.toSet())
        assertEquals("Выключи фонарик", action.requests.last().userMessage)
    }
    @Test fun `repeated model contradiction stops without execution or more retries`() = runBlocking {
        val executor = RecordingExecutor()
        val action = RecordingModelSession(listOf(proposal("torch_on"), proposal("torch_toggle")))
        assertTrue(runtime(action, RecordingModelSession(), executor).handleUserText("Выключи фонарик") is KsenaxAgentTurnResult.Clarification)
        assertEquals(2, action.requests.size)
        assertTrue(executor.calls.isEmpty())
    }
    @Test fun `unrelated request misrouted to no argument torch never executes`() = runBlocking {
        val executor = RecordingExecutor()
        listOf("Поставь будильник через 20 минут", "Поставь будильник «Свет» через 20 минут",
            "Поставь будильник с названием \"фонарик\" через 20 минут").forEach { text ->
            val result = runtime(RecordingModelSession(listOf(proposal("torch_toggle"))), RecordingModelSession(), executor)
                .handleUserText(text)
            assertTrue(result is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }
    @Test fun `date and time alone do not authorize the wrong action domain`() = runBlocking {
        listOf("Создай событие завтра в 07:00" to "alarm_at_date_time",
            "Поставь будильник завтра в 07:00" to "calendar_event_create").forEach { (text, actionName) ->
            val executor = RecordingExecutor()
            val result = runtime(RecordingModelSession(listOf(proposal(actionName))), RecordingModelSession(), executor).handleUserText(text)
            assertTrue(result is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }
    @Test fun `planner cannot authorize hardware by discussion or quoted tool names`() = runBlocking {
        listOf("Создай заметку о фонарике" to "torch_on",
            "Создай заметку про будильник" to "alarm_after_minutes",
            "Создай заметку про календарь" to "calendar_event_create",
            "Создай заметку про будильник и календарь" to "calendar_event_create",
            "Не включай фонарик" to "torch_on").forEach { (text, actionName) ->
            val planner = RecordingModelSession(statelessResponses = listOf(
                """{"type":"plan","steps":[{"action":"$actionName","input":{}}]}"""))
            val executor = RecordingExecutor()
            val result = runtime(RecordingModelSession(listOf(proposal("route_planned_work"))), planner, executor).handleUserText(text)
            assertTrue(result is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }
    private val current = ZonedDateTime.parse("2026-07-07T18:00:00+03:00[Europe/Moscow]")
    private fun proposal(name: String, args: String = "{}") = KsenaxModelFunctionResponse(listOf(KsenaxModelFunctionCall(name, args)), 2)
    private fun runtime(action: RecordingModelSession, planner: RecordingModelSession, executor: RecordingExecutor,
        kits: List<KsenaxOneShotActionKit> = listOf(TorchToolModule(executor), AlarmOneShotToolModule(executor), CalendarEventOneShotToolModule(executor), ObsidianNoteOneShotToolModule(executor)),
        clock: ZonedDateTime = current) =
        KsenaxAgenticWorkRuntime(planner, action, kits, now = { clock })

    @Test fun `flashlight paraphrases use one native FG call and no G4`() = runBlocking {
        mapOf("Включи фонарик" to "torch_on", "Зажги фонарь" to "torch_on", "Мне нужен свет с телефона" to "torch_on",
            "Выключи фонарик" to "torch_off", "погаси фонарь пожалуйста" to "torch_off", "фонарик" to "torch_toggle",
            "зажги пожалуйста этот ну фонарик" to "torch_on").forEach { (text, name) ->
            val planner = RecordingModelSession(); val action = RecordingModelSession(listOf(proposal(name))); val executor = RecordingExecutor()
            val runtime = runtime(action, planner, executor)
            runtime.prepare()
            assertTrue(runtime.handleUserText(text) is KsenaxAgentTurnResult.ToolExecution)
            assertEquals(listOf(name), executor.calls.map { it.name })
            assertEquals(0, planner.initializationCount); assertEquals(0, planner.statelessPrompts.size)
            assertEquals(1, action.requests.size)
        }
    }

    @Test fun `alarm paraphrases exact counts colloquial clocks and relative units stay fast`() = runBlocking {
        val cases = listOf(
            Triple("Поставь будильник на 7:30", "alarm_at_time", """{"time":"07:30"}"""),
            Triple("Будильник на 06:15", "alarm_at_time", """{"time":"06:15"}"""),
            Triple("Разбуди меня завтра в семь", "alarm_at_date_time", """{"date_time":"2026-07-08T07:00"}"""),
            Triple("Мне завтра надо проснуться в половине восьмого", "alarm_at_date_time", """{"date_time":"2026-07-08T07:30"}"""),
            Triple("Разбуди через два часа", "alarm_after_hours", """{"hours":2}"""),
            Triple("слушай ну разбуди меня завтра где то в семь утра", "alarm_at_date_time", """{"date_time":"2026-07-08T07:00"}"""),
            Triple("поставь 20 будильников через 4 минуты", "alarm_after_minutes", """{"minutes":4,"count":20}"""),
            Triple("поставь сейчас будильник через 4 минуты", "alarm_after_minutes", """{"minutes":4}"""),
            Triple("поставь 5 будильников через 10 часов", "alarm_after_hours", """{"hours":10,"count":5}"""),
        )
        cases.forEach { (text, name, args) ->
            val planner = RecordingModelSession(); val action = RecordingModelSession(listOf(proposal(name, args))); val executor = RecordingExecutor()
            assertTrue("$text", runtime(action, planner, executor).handleUserText(text) is KsenaxAgentTurnResult.ToolExecution)
            assertEquals(1, executor.calls.size); assertEquals(0, planner.initializationCount)
            val actual = Json.parseToJsonElement(executor.calls.single().arguments.JSONtoString()).jsonObject
            Json.parseToJsonElement(args).jsonObject.forEach { (key, value) -> assertEquals(text, value, actual[key]) }
        }
    }

    @Test fun `model cannot invent alarm parameters absent in user text`() = runBlocking {
        val action = RecordingModelSession(listOf(proposal("alarm_at_time", """{"time":"07:00"}""")))
        val executor = RecordingExecutor()
        assertTrue(runtime(action, RecordingModelSession(), executor).handleUserText("Поставь будильник") is KsenaxAgentTurnResult.Clarification)
        assertTrue(executor.calls.isEmpty())
    }

    @Test fun `local alarm numbers override incorrect but well typed model extraction`() = runBlocking {
        val executor = RecordingExecutor(); val action = RecordingModelSession(listOf(proposal("alarm_after_hours", """{"hours":5,"count":1}""")))
        runtime(action, RecordingModelSession(), executor).handleUserText("поставь 5 будильников через 10 часов")
        assertEquals("""{"hours":10,"count":5}""", executor.calls.single().arguments.JSONtoString())
        assertFalse(action.requests.last().userMessage.contains("Local now"))
        assertTrue(action.requests.last().userMessage.contains("\"hours\":10"))
        assertTrue(action.requests.last().userMessage.contains("\"count\":5"))
    }

    @Test fun `FG cannot replace an alarm creation with dismissal of all alarms`() = runBlocking {
        val executor = RecordingExecutor()
        val result = runtime(RecordingModelSession(listOf(proposal("alarm_clear_all"))), RecordingModelSession(), executor)
            .handleUserText("поставь будильник через 4 минуты")
        assertTrue(result is KsenaxAgentTurnResult.Clarification)
        assertTrue(executor.calls.isEmpty())
    }

    @Test fun `calendar exact dates and STT paraphrases stay fast`() = runBlocking {
        val cases = listOf(
            "Создай событие завтра в 18:00 — созвон с Димой" to "2026-07-08T18:00",
            "Добавь в календарь встречу с преподавателем в пятницу в 15:00" to "2026-07-10T15:00",
            "короче добавь пожалуйста встречу завтра шесть вечера с димой" to "2026-07-08T18:00",
        )
        cases.forEach { (text, date) ->
            val executor = RecordingExecutor(); val planner = RecordingModelSession()
            val action = RecordingModelSession(listOf(proposal("calendar_event_create", """{"title":"Встреча","start_local_date_time":"$date"}""")))
            assertTrue(runtime(action, planner, executor).handleUserText(text) is KsenaxAgentTurnResult.ToolExecution)
            assertEquals(0, planner.initializationCount)
            assertTrue(executor.calls.single().arguments.JSONtoString().contains(date))
        }
    }

    @Test fun `calendar without precise time requires clarification even if FG invents it`() = runBlocking {
        listOf("Запиши на послезавтра в календарь защиту проекта", "Создай встречу завтра вечером", "Добавь встречу в календарь").forEach { text ->
            val executor = RecordingExecutor()
            val action = RecordingModelSession(listOf(proposal("calendar_event_create", """{"title":"Встреча","start_local_date_time":"2026-07-08T18:00"}""")))
            assertTrue(runtime(action, RecordingModelSession(), executor).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `generation and compound requests cannot be reduced to a fast side effect`() = runBlocking {
        listOf("Создай заметку про архитектуру OpenKsenax", "Сделай из этого текста нормальную Obsidian-заметку", "Сформулируй технический конспект и сохрани его в заметки", "Создай заметку о сегодняшней встрече и разбуди меня завтра в семь").forEach { text ->
            val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"clarification","question":"Уточни детали"}"""))
            val action = RecordingModelSession(listOf(proposal("torch_on"))); val executor = RecordingExecutor()
            assertTrue(runtime(action, planner, executor).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            assertEquals(1, planner.statelessPrompts.size); assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `note content bypasses FG while model selects the planned function`() = runBlocking {
        val content = "OpenKsenax работает локально. Полный сгенерированный анализ."
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"id":"note_1","action":"obsidian_note_write","instruction":"Создай заметку","input":{"title":"Архитектура","markdown_body":"$content"}}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("obsidian_note_write")))
        val executor = RecordingExecutor()
        val result = runtime(action, planner, executor).handleUserText("Создай заметку про архитектуру OpenKsenax")
        assertTrue(result is KsenaxAgentTurnResult.ToolExecution)
        assertFalse(action.requests[1].userMessage.contains(content))
        assertEquals("note_1", executor.calls.single().id)
        assertTrue(executor.calls.single().arguments.JSONtoString().contains(content))
    }

    @Test fun `mixed note and alarm plan preserves both actions and order`() = runBlocking {
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"id":"note","action":"obsidian_note_write","instruction":"Создай заметку","input":{"markdown_body":"Сегодня состоялась встреча."}},{"id":"alarm","action":"alarm_at_date_time","instruction":"Разбуди завтра в семь","input":{"date_time":"2026-07-08T07:00"}}]}"""))
        val executor = RecordingExecutor()
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("obsidian_note_write"), proposal("alarm_at_date_time", """{"date_time":"2026-07-08T07:00"}""")))
        val result = runtime(action, planner, executor).handleUserText("Создай заметку о сегодняшней встрече и разбуди меня завтра в семь") as KsenaxAgentTurnResult.ToolExecution
        assertEquals(listOf("obsidian_note_write", "alarm_at_date_time"), executor.calls.map { it.name })
        assertEquals(listOf("note", "alarm"), result.toolResult.executed.map { it.callId })
    }

    @Test fun `turn mutex prevents interleaved model and execution stages`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val action = RecordingModelSession(listOf(proposal("torch_on"), proposal("torch_off")))
        action.beforeResponse = { if (action.requests.size == 1) gate.await() }
        val runtime = runtime(action, RecordingModelSession(), RecordingExecutor())
        val first = launch(start = CoroutineStart.UNDISPATCHED) { runtime.handleUserText("включи фонарик") }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { runtime.handleUserText("выключи фонарик") }
        assertEquals(1, action.requests.size)
        gate.complete(Unit); joinAll(first, second)
        assertEquals(2, action.requests.size)
    }

    @Test fun `malformed unknown missing and wrong typed output cannot execute`() = runBlocking {
        val cases = listOf(proposal("unknown"), proposal("torch_on", "{"), proposal("torch_on", """{"extra":1}"""),
            proposal("alarm_after_minutes", "{}"), proposal("alarm_after_minutes", """{"minutes":"4"}"""),
            proposal("alarm_after_minutes", """{"minutes":-1}"""), proposal("alarm_after_minutes", """{"minutes":4,"count":100}"""),
            KsenaxModelFunctionResponse(emptyList(), 1), KsenaxModelFunctionResponse(listOf(KsenaxModelFunctionCall("torch_on", "{}"), KsenaxModelFunctionCall("torch_off", "{}")), 1))
        cases.forEach { response ->
            val executor = RecordingExecutor()
            val result = runtime(RecordingModelSession(listOf(response)), RecordingModelSession(), executor).handleUserText("поставь 1 будильник через 4 минуты")
            assertTrue(result is KsenaxAgentTurnResult.ModelFailure || result is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `facts and negative commands have no side effects`() = runBlocking {
        listOf("Мне завтра надо быть на работе в семь", "Не включай фонарик").forEach { text ->
            val executor = RecordingExecutor()
            assertTrue(runtime(RecordingModelSession(listOf(proposal("torch_on"))), RecordingModelSession(), executor).handleUserText(text) is KsenaxAgentTurnResult.Clarification)
            assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `clarification and unsupported are terminal without G4`() = runBlocking {
        listOf(proposal("route_clarification", """{"question":"Когда?"}"""), proposal("route_unsupported", """{"reason":"Не поддерживается"}"""))
            .forEach { response ->
                val planner = RecordingModelSession(); val executor = RecordingExecutor()
                val result = runtime(RecordingModelSession(listOf(response)), planner, executor).handleUserText("какой-то запрос")
                assertTrue(result is KsenaxAgentTurnResult.Clarification || result is KsenaxAgentTurnResult.Refusal)
                assertEquals(0, planner.initializationCount); assertTrue(executor.calls.isEmpty())
            }
    }

    @Test fun `policy and executor exceptions are contained`() = runBlocking {
        val executor = RecordingExecutor { throw IllegalStateException("hardware") }
        val result = runtime(RecordingModelSession(listOf(proposal("torch_on"))), RecordingModelSession(), executor).handleUserText("включи фонарик") as KsenaxAgentTurnResult.ToolExecution
        assertTrue(result.toolResult.executed.single() is KsenaxToolResult.Failure)
        val blockedExecutor = RecordingExecutor()
        val blocked = runtime(RecordingModelSession(listOf(proposal("torch_on"))), RecordingModelSession(), blockedExecutor)
            .handleUserText("включи фонарик", KsenaxPolicyContext(blockedToolNames = setOf("torch_on"))) as KsenaxAgentTurnResult.ToolExecution
        assertEquals("BLOCKED_TOOL", blocked.toolResult.denied.single().code)
        assertTrue(blockedExecutor.calls.isEmpty())
    }

    @Test fun `cancellation is not converted to model failure`() = runBlocking {
        val action = RecordingModelSession(); action.failure = CancellationException("cancel")
        try { runtime(action, RecordingModelSession(), RecordingExecutor()).handleUserText("фонарик"); fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }

    @Test fun `plan stops on first failure and keeps unique step ids`() = runBlocking {
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"id":"step_1","action":"torch_on","input":{}},{"id":"step_2","action":"torch_off","input":{}}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("torch_on"), proposal("torch_off")))
        val executor = RecordingExecutor { KsenaxToolResult.Failure(it.id, it.name, "failed", "TEST") }
        val result = runtime(action, planner, executor).handleUserText("включи затем выключи фонарь") as KsenaxAgentTurnResult.ToolExecution
        assertEquals(listOf("step_1"), executor.calls.map { it.id })
        assertEquals(1, result.toolResult.executed.size)
    }

    @Test fun `invalid later step prevents all plan side effects`() = runBlocking {
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"action":"torch_on","input":{}},{"action":"alarm_after_minutes","input":{"minutes":"four"}}]}"""))
        val executor = RecordingExecutor()
        val result = runtime(RecordingModelSession(listOf(proposal("route_planned_work"))), planner, executor).handleUserText("включи фонарь и поставь будильник через четыре минуты")
        assertTrue(result is KsenaxAgentTurnResult.ModelFailure); assertTrue(executor.calls.isEmpty())
    }

    @Test fun `invalid later FG response prevents every plan side effect`() = runBlocking {
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"action":"torch_on","input":{}},{"action":"torch_off","input":{}}]}"""))
        val executor = RecordingExecutor()
        val result = runtime(RecordingModelSession(listOf(proposal("route_planned_work"), proposal("torch_on"), proposal("unknown"))), planner, executor)
            .handleUserText("включи затем выключи фонарь") as KsenaxAgentTurnResult.ToolExecution
        assertTrue(executor.calls.isEmpty()); assertEquals("FG_ACTION_MISMATCH", result.toolResult.denied.single().code)
    }

    @Test fun `expired later action keeps earlier execution visible in result`() = runBlocking {
        var clock = current
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"action":"torch_on","input":{}},{"action":"calendar_event_create","input":{"title":"Встреча","start_local_date_time":"2026-07-07T18:01"}}]}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work"), proposal("torch_on"), proposal("calendar_event_create", """{"title":"Встреча","start_local_date_time":"2026-07-07T18:01"}""")))
        val executor = RecordingExecutor { call -> clock = current.plusMinutes(2); KsenaxToolResult.Success(call.id, call.name, "ok") }
        val runtime = KsenaxAgenticWorkRuntime(planner, action, listOf(TorchToolModule(executor), CalendarEventOneShotToolModule(executor)), now = { clock })
        val result = runtime.handleUserText("включи фонарик и создай встречу сегодня в 18:01") as KsenaxAgentTurnResult.ToolExecution
        assertEquals(listOf("torch_on"), executor.calls.map { it.name })
        assertEquals(1, result.toolResult.executed.size)
        assertEquals("INVALID_ARGUMENTS", result.toolResult.denied.single().code)
    }

    @Test fun `long input is sent intact to G4 and never partially routed`() = runBlocking {
        val text = "Сделай из этого текста заметку: " + "полные детали ".repeat(80).trimEnd()
        val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"clarification","question":"Как оформить?"}"""))
        val action = RecordingModelSession(listOf(proposal("route_planned_work")))
        runtime(action, planner, RecordingExecutor()).handleUserText(text)
        assertEquals(listOf("route_planned_work"), action.requests.single().functions.map { it.name })
        assertTrue(planner.statelessPrompts.single().endsWith(text))
    }

    @Test fun `mixed duration is clarified instead of silently shortened`() = runBlocking {
        val executor = RecordingExecutor()
        val result = runtime(RecordingModelSession(listOf(proposal("alarm_after_hours", """{"hours":1.5}"""))), RecordingModelSession(), executor)
            .handleUserText("разбуди через 1 час 30 минут")
        assertTrue(result is KsenaxAgentTurnResult.Clarification); assertTrue(executor.calls.isEmpty())
    }

    @Test fun `contradictory and all day calendar fields cannot reach executor`() = runBlocking {
        listOf("""{"title":"X","start_local_date_time":"2026-07-08T18:00","start_delay_minutes":1}""",
            """{"title":"X","start_local_date_time":"2026-07-08T18:00","all_day":true}""").forEach { args ->
            val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"action":"calendar_event_create","input":$args}]}"""))
            val executor = RecordingExecutor()
            assertTrue(runtime(RecordingModelSession(listOf(proposal("route_planned_work"))), planner, executor)
                .handleUserText("Создай событие завтра в 18:00") is KsenaxAgentTurnResult.ModelFailure)
            assertEquals(1, planner.initializationCount)
            assertEquals(1, planner.statelessPrompts.size)
            assertTrue(executor.calls.isEmpty())
        }
    }

    @Test fun `missing or wrong typed note payload cannot be replaced with UP`() = runBlocking {
        listOf("{}", """{"markdown_body":123}""").forEach { input ->
            val planner = RecordingModelSession(statelessResponses = listOf("""{"type":"plan","steps":[{"action":"obsidian_note_write","input":$input}]}"""))
            val executor = RecordingExecutor()
            val result = runtime(RecordingModelSession(listOf(proposal("route_planned_work"))), planner, executor).handleUserText("Создай заметку о том как прошел вечер")
            assertTrue(result is KsenaxAgentTurnResult.Clarification || result is KsenaxAgentTurnResult.ModelFailure)
            assertTrue(executor.calls.isEmpty())
        }
    }
}

private class RecordingExecutor(private val result: (KsenaxToolCall) -> KsenaxToolResult = { KsenaxToolResult.Success(it.id, it.name, "ok") }) : KsenaxToolExecutor {
    val calls = mutableListOf<KsenaxToolCall>()
    override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult { calls += call; return result(call) }
}

private class RecordingModelSession(functionResponses: List<KsenaxModelFunctionResponse> = emptyList(), statelessResponses: List<String> = emptyList()) : KsenaxModelSession {
    // Параметризованный fixture означает два отдельных ответа: выбор кода и извлечение аргументов.
    private val functionQueue = ArrayDeque(if (functionResponses.firstOrNull()?.calls?.singleOrNull()?.name == "route_planned_work") functionResponses else functionResponses.flatMap { response ->
        val call = response.calls.singleOrNull()
        if (call != null && (call.name.startsWith("alarm_") && call.name != "alarm_clear_all" || call.name == "calendar_event_create") && call.argumentsJson != "{}")
            listOf(KsenaxModelFunctionResponse(listOf(call.copy(argumentsJson = "{}")), 1), response)
        else listOf(response)
    })
    private val textQueue = ArrayDeque(statelessResponses)
    var initializationCount = 0
    var failure: Exception? = null
    var beforeResponse: suspend () -> Unit = {}
    val requests = mutableListOf<KsenaxModelFunctionRequest>()
    val statelessPrompts = mutableListOf<String>()
    override suspend fun initializeEngine() { initializationCount++ }
    override suspend fun askFunctions(request: KsenaxModelFunctionRequest): KsenaxModelFunctionResponse {
        failure?.let { throw it }; requests += request; beforeResponse()
        val response = functionQueue.removeFirst()
        if (request.functions.size > 1 && response.calls.size == 1) {
            val call = response.calls.single()
            if (call.argumentsJson == "{}" || call.name in setOf("route_clarification", "route_unsupported")) {
                return response.copy(calls = listOf(call.copy(argumentsJson = "{}")))
            }
        }
        return response
    }
    override suspend fun askStateless(request: KsenaxModelRequest): KsenaxModelResponse {
        statelessPrompts += request.prompt; return KsenaxModelResponse(textQueue.removeFirst(), 1, KsenaxModelTaskProfile.ROUTER)
    }
    override suspend fun askPersistent(request: KsenaxModelRequest): KsenaxModelResponse = error("Not persistent")
    override fun streamPersistent(request: KsenaxModelRequest): Flow<KsenaxModelStreamEvent> = error("Not persistent")
    override fun streamEphemeral(userText: String): Flow<KsenaxModelStreamEvent> = error("Native functions must be used")
    override suspend fun transcribe(voiceMessage: KsenaxVoiceMessage, prompt: KsenaxVoiceTranscriptionPrompt): KsenaxModelResponse = error("No audio")
    override suspend fun resetPersistentConversation() = Unit
    override suspend fun close() = Unit
}
