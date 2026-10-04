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
    private val current = ZonedDateTime.parse("2026-07-07T18:00:00+03:00[Europe/Moscow]")
    private fun proposal(name: String, args: String = "{}") = KsenaxModelFunctionResponse(listOf(KsenaxModelFunctionCall(name, args)), 2)
    private fun runtime(action: RecordingModelSession, planner: RecordingModelSession, executor: RecordingExecutor,
        kits: List<KsenaxOneShotActionKit> = listOf(TorchToolModule(executor), AlarmOneShotToolModule(executor), CalendarEventOneShotToolModule(executor), ObsidianNoteOneShotToolModule(executor))) =
        KsenaxAgenticWorkRuntime(planner, action, kits, now = { current })

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
        val result = runtime.handleUserText("включи фонарик и создай встречу") as KsenaxAgentTurnResult.ToolExecution
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
            assertTrue(runtime(RecordingModelSession(listOf(proposal("route_planned_work"))), planner, executor).handleUserText("Создай событие") is KsenaxAgentTurnResult.ModelFailure)
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
        failure?.let { throw it }; requests += request; beforeResponse(); return functionQueue.removeFirst()
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
