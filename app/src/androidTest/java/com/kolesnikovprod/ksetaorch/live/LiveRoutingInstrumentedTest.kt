package com.kolesnikovprod.ksetaorch.live

import android.app.Instrumentation
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.MainActivity
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxAgenticWorkController
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionDeclaration
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionRequest
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.work.routing.FunctionGemmaRoutingProtocol
import kotlinx.coroutines.runBlocking
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Запускается только явно через ADB; использует установленные модели и реальные Android tools.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@RunWith(AndroidJUnit4::class)
class LiveRoutingInstrumentedTest {
    /** Сравнивает схемы модельного выбора, без G4 и Android-действий.
     * Диагностика не заменяет проверку полного production-пайплайна.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    @Test fun diagnoseRoutingStages() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        val selected = requireNotNull(args.getString("cases")).split(",").toSet()
        val cases = LiveRoutingCorpus.cases.filter { it.id in selected }
        check(cases.map { it.id }.toSet() == selected)
        val comparison = args.getString("comparison") ?: "four_way_gate"
        require(comparison in setOf("four_way_gate", "wire_names"))
        val repeats = args.getString("repeats")?.toInt() ?: 1
        require(repeats > 0)
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"),
            requireNotNull(args.getString("runId")))
        val full = FunctionGemmaRoutingProtocol(TorchOneShotProtocol.declarations +
            AlarmOneShotProtocol.declarations + CalendarEventOneShotProtocol.declarations)
        val gates = listOf(
            KsenaxModelFunctionDeclaration("execute_device_action", "One command to switch flashlight, set alarms or create a calendar event. No writing or generation."),
            KsenaxModelFunctionDeclaration("generate_or_plan_work", "Write, generate or analyze a note or text; or perform several different actions."),
            KsenaxModelFunctionDeclaration("ask_for_missing_details", "A device command without required time or with unclear meaning."),
            KsenaxModelFunctionDeclaration("reject_non_command", "A question, a fact or a negation, not a command to perform an action."),
        )
        // Меняем только имена трёх функций: порядок, описания и параметры прежние.
        // Это проверка совместимости схемы, не предположение о датасете весов.
        val wireNames = mapOf("torch_on" to "turn_on_flashlight", "torch_off" to "turn_off_flashlight",
            "calendar_event_create" to "create_calendar_event")
        writer.append(JSONObject().put("type", "environment").put("timestamp", ZonedDateTime.now().toString())
            .put("appLastUpdateTime", app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime)
            .put("executorsInvoked", false).put("backend", "CPU").put("selectedCases", JSONArray(cases.map { it.id }))
            .put("comparison", comparison).put("repeats", repeats)
            .put("expectedResults", cases.size * repeats * if (comparison == "wire_names") 4 else 2))
        for ((caseIndex, case) in cases.withIndex()) repeat(repeats) { repetition ->
            val production = full.buildRequest(case.input)
            val variants = if (comparison == "wire_names") {
                val reordered = production.functions.toMutableList()
                for ((first, second) in listOf("torch_on" to "torch_off", "calendar_event_create" to "route_planned_work")) {
                    val a = reordered.indexOfFirst { it.name == first }
                    val b = reordered.indexOfFirst { it.name == second }
                    if (a >= 0 && b >= 0) java.util.Collections.swap(reordered, a, b)
                }
                val orderControl = production.copy(functions = reordered)
                fun rename(request: KsenaxModelFunctionRequest) = request.copy(functions = request.functions.map {
                    it.copy(name = wireNames[it.name] ?: it.name)
                })
                listOf("production_catalog" to production, "wire_names" to rename(production),
                    "order_control" to orderControl, "wire_names_order_control" to rename(orderControl))
            } else listOf("production_catalog" to production, comparison to KsenaxModelFunctionRequest(case.input, gates))
            // Ротация отделяет вариант схемы от прогрева и позиции inference в серии.
            val offset = (caseIndex + repetition) % variants.size
            for ((variant, request) in variants.drop(offset) + variants.take(offset)) {
                val started = SystemClock.elapsedRealtime()
                val event = JSONObject().put("type", "diagnostic_result").put("id", case.id)
                    .put("input", case.input).put("expectedRoute", case.route).put("variant", variant)
                    .put("repetition", repetition).put("functionNames", JSONArray(request.functions.map { it.name }))
                try {
                    val response = app.functionGemmaModelSession.askFunctions(request)
                    event.put("calls", JSONArray(response.calls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson) }))
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { event.put("error", error.toString()) }
                writer.append(event.put("wallLatencyMs", SystemClock.elapsedRealtime() - started))
            }
        }
        writer.append(JSONObject().put("type", "suite_finished").put("complete", true))
    }

    @Test fun diagnoseReducedTorchCatalog() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"), "reduced-torch-catalog")
        val declarations = TorchOneShotProtocol.declarations.map { KsenaxModelFunctionDeclaration(it.codeName, it.description) }
        for (text in listOf("Выключи фонарик", "Зажги фонарь", "Погаси пожалуйста свет на телефоне", "Выключи фонарик")) {
            val event = JSONObject().put("input", text)
            try {
                val response = app.functionGemmaModelSession.askFunctions(KsenaxModelFunctionRequest(text, declarations))
                event.put("calls", JSONArray(response.calls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson) }))
                    .put("latencyMs", response.latencyMs)
            } catch (error: Exception) { event.put("error", error.toString()) }
            writer.append(event)
        }
    }
    @Test fun diagnoseRouterVariants() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        val declarations = (TorchOneShotProtocol.declarations + AlarmOneShotProtocol.declarations + CalendarEventOneShotProtocol.declarations)
            .map { KsenaxModelFunctionDeclaration(it.codeName, it.description.substringBefore("Use for").substringBefore("Arguments are")) } + listOf(
                KsenaxModelFunctionDeclaration("route_planned_work", "Generate/write/analyze notes or text, reason, or perform multiple actions."),
                KsenaxModelFunctionDeclaration("route_clarification", "Command missing required time or meaning."),
                KsenaxModelFunctionDeclaration("route_unsupported", "No supported command, a question or a fact."))
        val shortSchema = """{"type":"object","properties":{"action":{"type":"string","enum":["turn_on_flashlight","turn_off_flashlight","set_alarm","create_calendar_event","generate_note","ask_question","unsupported"]}},"required":["action"],"additionalProperties":false}"""
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"), "router-variants")
        for (text in listOf("Включи фонарик", "Выключи фонарик", "Поставь будильник через 20 минут", "Создай заметку про Android", "Создай событие завтра в 18:00 — созвон", "Поставь будильник")) {
            for ((variant, request) in listOf(
                "constrained_catalog" to KsenaxModelFunctionRequest(text, declarations),
                "compact_semantic_enum" to KsenaxModelFunctionRequest(text, listOf(KsenaxModelFunctionDeclaration("route_request", "Choose the action requested by the user. If required details are missing, ask_question. Text generation uses generate_note. Questions without a command are unsupported.", shortSchema))),
            )) {
                val event = JSONObject().put("variant", variant).put("input", text)
                try { val response = app.functionGemmaModelSession.askFunctions(request); event.put("calls", JSONArray(response.calls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson) })).put("latencyMs", response.latencyMs) }
                catch (error: Exception) { event.put("error", error.toString()) }
                writer.append(event)
            }
        }
    }
    @OptIn(ExperimentalApi::class)
    @Test fun diagnoseNativeCalling() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("constrained") != "false") {
            "Production askFunctions always captures constrained decoding; use a raw SDK probe for A/B."
        }
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        app.functionGemmaModelSession
        val runId = InstrumentationRegistry.getArguments().getString("runId") ?: "native-production"
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"), runId)
        val base = "You are a model that can do function calling with the following functions"
            for ((id, request) in listOf(
            "single_empty_object" to KsenaxModelFunctionRequest("Включи фонарик", listOf(KsenaxModelFunctionDeclaration("torch_on", "Turns on the device flashlight.")), base),
            "single_null" to KsenaxModelFunctionRequest("Включи фонарик", listOf(KsenaxModelFunctionDeclaration("torch_on", "Turns on the device flashlight.", "null")), base),
            "two_directions" to KsenaxModelFunctionRequest("Включи фонарик", listOf(KsenaxModelFunctionDeclaration("torch_on", "Turns on the device flashlight."), KsenaxModelFunctionDeclaration("torch_off", "Turns off the device flashlight.")), base),
            "single_parameter" to KsenaxModelFunctionRequest("Поставь будильник через 20 минут", listOf(KsenaxModelFunctionDeclaration("alarm_after_minutes", "Sets an alarm after the given minutes.", """{"type":"object","properties":{"minutes":{"type":"integer"}},"required":["minutes"]}""")), base),
            "semantic_selector_on" to KsenaxModelFunctionRequest("Включи фонарик", listOf(KsenaxModelFunctionDeclaration("route_request", "Select exactly one action matching the user request. Do not execute anything.", """{"type":"object","properties":{"action":{"type":"string","enum":["torch_on","torch_off","alarm_after_minutes","route_planned_work","route_clarification","route_unsupported"],"description":"torch_on=turn flashlight on; torch_off=turn flashlight off; alarm_after_minutes=set alarm after minutes; route_planned_work=generate notes or perform multiple actions; route_clarification=missing required details; route_unsupported=no supported command"}},"required":["action"]}""")), base),
            "semantic_selector_note" to KsenaxModelFunctionRequest("Создай заметку про архитектуру Android", listOf(KsenaxModelFunctionDeclaration("route_request", "Select exactly one action matching the user request. Do not execute anything.", """{"type":"object","properties":{"action":{"type":"string","enum":["torch_on","torch_off","alarm_after_minutes","route_planned_work","route_clarification","route_unsupported"],"description":"torch_on=turn flashlight on; torch_off=turn flashlight off; alarm_after_minutes=set alarm after minutes; route_planned_work=generate notes or perform multiple actions; route_clarification=missing required details; route_unsupported=no supported command"}},"required":["action"]}""")), base),
            )) {
                val event = JSONObject().put("variant", id).put("functionConstrained", true)
                    .put("globalConstrainedFlag", ExperimentalFlags.enableConversationConstrainedDecoding)
                    .put("processId", android.os.Process.myPid())
                try {
                    val response = app.functionGemmaModelSession.askFunctions(request)
                    event.put("calls", JSONArray(response.calls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson) })).put("latencyMs", response.latencyMs)
                } catch (error: Exception) { event.put("error", error.toString()) }
                writer.append(event)
            }
    }

    @OptIn(ExperimentalApi::class)
    @Test fun runLiveCorpus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val app = instrumentation.targetContext.applicationContext as KsenaxAndroidApplication
        val workspaceUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FOpenKsenax-live-tests-20261004"
        val workspacePath = "/storage/emulated/0/Documents/OpenKsenax-live-tests-20261004"
        check(app.contentResolver.persistedUriPermissions.any { it.uri.toString() == workspaceUri && it.isReadPermission && it.isWritePermission }) {
            "BLOCKED: choose and grant the isolated live-test folder in the real app first."
        }
        listOf(app.functionGemmaInstallUseCase.getInstalledPath(), app.gemmaInstallUseCase.getGemma4E2BModelPath()).forEach {
            check(File(it).let { file -> file.isFile && file.canRead() && file.length() > 0 }) { "BLOCKED: model unavailable" }
        }
        val runId = args.getString("runId") ?: "live-${System.currentTimeMillis()}"
        val selected = args.getString("cases")?.split(",")?.toSet()
        val cases = LiveRoutingCorpus.cases.filter { selected == null || it.id in selected }
        check(cases.isNotEmpty()) { "No matching cases" }
        check(selected == null || cases.map { it.id }.toSet() == selected) { "Unknown case id" }
        val repeats = args.getString("repeats")?.toInt() ?: 1
        require(repeats > 0) { "repeats must be positive" }
        val timeoutMs = args.getString("timeoutMs")?.toLong() ?: 360_000L
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"), runId)
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        app.functionGemmaModelSession
        writer.append(JSONObject().put("type", "environment").put("timestamp", java.time.ZonedDateTime.now().toString())
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("android", Build.VERSION.RELEASE).put("api", Build.VERSION.SDK_INT)
            .put("appVersion", packageInfo.versionName).put("versionCode", packageInfo.longVersionCode)
            .put("appLastUpdateTime", packageInfo.lastUpdateTime).put("runId", runId)
            .put("selectedCases", JSONArray(cases.map { case -> JSONObject().put("id", case.id).put("input", case.input)
                .put("expectedRoute", case.route).put("expectedTools", JSONArray(case.tools))
                .put("expectedArguments", JSONObject(case.arguments)).put("titleFragments", JSONArray(case.titleFragments)) }))
            .put("repeats", repeats).put("timeoutMs", timeoutMs).put("expectedResults", cases.size * repeats)
            .put("backend", "CPU").put("functionGemmaContext", app.functionGemmaModelSession.runtimeConfig.maxContextTokens)
            .put("gemmaContext", app.gemmaModelSession.runtimeConfig.maxContextTokens).put("workspace", workspacePath)
            .put("globalConstrainedFlag", ExperimentalFlags.enableConversationConstrainedDecoding).put("processId", android.os.Process.myPid())
            .put("inputBoundary", "production ViewModel onInputTextChanged/onSendClick"))
        val fg = LiveModelObserver(app.functionGemmaModelSession)
        val g4 = LiveModelObserver(app.gemmaModelSession)
        val turn = LiveTurnObserver(KsenaxAgenticWorkController(app, g4, fg))
        val input = LiveInputDriver(instrumentation, app, turn, workspaceUri, workspacePath)
        val intents = CopyOnWriteArrayList<JSONObject>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                intents.add(JSONObject().put("action", intent.action).put("data", intent.dataString)
                    .put("observedAt", ZonedDateTime.now().toString())
                    .put("extras", JSONObject().apply { intent.extras?.let { extras -> extras.keySet().forEach { key -> put(key, extras.get(key)?.toString()) } } }))
                return null // Наблюдение не блокирует и не подменяет системное приложение.
            }
        }
        val torchStates = ConcurrentHashMap<String, Boolean>()
        val camera = app.getSystemService(CameraManager::class.java)
        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchStates[cameraId] = enabled }
        }
        var failures = 0
        var completed = 0
        var complete = false
        var monitorAdded = false
        var callbackRegistered = false
        var alarmMonitorObserved = false
        var calendarMonitorObserved = false
        val tree = android.net.Uri.parse(workspaceUri)
        val screenFlags = linkedMapOf<android.app.Activity, Boolean>()
        fun keepScreenOn(activity: android.app.Activity) = instrumentation.runOnMainSync {
            screenFlags.putIfAbsent(activity, (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        fun showApplication() {
            // startActivitySync ждёт idle очереди, которого нет у постоянно анимированного UI.
            // Ждём реальное RESUMED, не отключая анимации и не меняя production UI.
            instrumentation.runOnMainSync {
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline) {
                var resumed: android.app.Activity? = null
                instrumentation.runOnMainSync {
                    resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .firstOrNull { it is MainActivity }
                }
                resumed?.let { keepScreenOn(it); return }
                Thread.sleep(100)
            }
            error("BLOCKED: MainActivity did not resume")
        }
        try {
            instrumentation.addMonitor(monitor)
            monitorAdded = true
            camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            callbackRegistered = true
            for (case in cases) repeat(repeats) { repetition ->
                showApplication()
                fg.reset(); g4.reset(); turn.reset(); intents.clear()
                val started = SystemClock.elapsedRealtime()
                val caseStarted = ZonedDateTime.now()
                writer.append(JSONObject().put("type", "case_started").put("id", case.id).put("input", case.input).put("repetition", repetition)
                    .put("timestamp", caseStarted.toString()).put("timezone", caseStarted.zone.id))
                val beforeNotes = readNoteFiles(app, tree)
                val beforeTorch = torchStates.toMap()
                val ui = input.submit(case.input, timeoutMs)
                Thread.sleep(350)
                val result = LiveResultEvaluator.evaluate(case, turn, fg, g4, ui, caseStarted)
                    .put("type", "case_result").put("repetition", repetition).put("totalLatencyMs", SystemClock.elapsedRealtime() - started)
                    .put("androidIntents", JSONArray(intents)).put("torchEnabled", torchStates.values.any { it })
                    .put("torchStateObserved", torchStates.isNotEmpty())
                if (case.expectedTorch != null && (torchStates.isEmpty() || torchStates.values.any { it } != case.expectedTorch)) {
                    result.getJSONArray("issues").put("EXECUTION_FAILURE:torch_state")
                    result.put("status", "FAIL").put("pipelineStatus", "FAIL")
                }
                if (case.tools.none { it.startsWith("torch_") } && beforeTorch.any { (id, enabled) -> torchStates[id]?.let { it != enabled } == true }) {
                    result.getJSONArray("issues").put("UNEXPECTED_SIDE_EFFECT:torch_state")
                    result.put("status", "FAIL").put("pipelineStatus", "FAIL")
                    if (case.route == "NO_ACTION" || case.route == "NEEDS_CLARIFICATION") result.put("safetyStatus", "FAIL")
                }
                val alarmIntents = intents.filter { it.optString("action") == AlarmClock.ACTION_SET_ALARM }
                if (alarmIntents.isNotEmpty()) alarmMonitorObserved = true
                val expectedAlarmCount = case.tools.filter { it.startsWith("alarm_") && it != "alarm_clear_all" }
                    .sumOf { case.arguments["count"]?.toIntOrNull() ?: 1 }
                val alarmTimesValid = alarmIntentTimesMatch(case, caseStarted, alarmIntents)
                result.put("expectedAlarmDispatchCount", expectedAlarmCount).put("observedAlarmDispatchCount", alarmIntents.size)
                    .put("alarmDispatchEvidence", if (alarmMonitorObserved) "OBSERVED_INTENTS_ONLY" else "UNVERIFIED_MONITOR")
                    .put("alarmPersistenceEvidence", "NOT_VERIFIED")
                    .put("alarmDispatchStatus", when {
                        expectedAlarmCount == 0 && alarmIntents.isEmpty() -> "NOT_APPLICABLE"
                        !alarmMonitorObserved -> "UNVERIFIED"
                        alarmIntents.size == expectedAlarmCount && alarmTimesValid -> "PASS"
                        else -> "FAIL"
                    })
                if (alarmMonitorObserved && (alarmIntents.size != expectedAlarmCount || !alarmTimesValid)) {
                    result.getJSONArray("issues").put("EXECUTION_FAILURE:alarm_dispatch_arguments")
                    result.put("status", "FAIL").put("pipelineStatus", "FAIL")
                    if (case.route == "NO_ACTION" || case.route == "NEEDS_CLARIFICATION") result.put("safetyStatus", "FAIL")
                }
                val calendarIntents = intents.filter {
                    it.optString("action") == Intent.ACTION_INSERT && it.optString("data") == CalendarContract.Events.CONTENT_URI.toString()
                }
                if (calendarIntents.isNotEmpty()) calendarMonitorObserved = true
                val expectedCalendarCount = case.tools.count { it == "calendar_event_create" }
                val expectedCalendarBegin = case.temporal?.takeIf { it.fieldName == "start_local_date_time" }
                    ?.expectedDateTime(caseStarted)?.atZone(caseStarted.zone)?.toInstant()?.toEpochMilli()
                val calendarEvidence = calendarIntents.map { event ->
                    val extras = event.optJSONObject("extras")
                    JSONObject().put("action", event.optString("action")).put("data", event.optString("data"))
                        .put("beginTime", extras?.optString(CalendarContract.EXTRA_EVENT_BEGIN_TIME)?.toLongOrNull() ?: JSONObject.NULL)
                        .put("endTime", extras?.optString(CalendarContract.EXTRA_EVENT_END_TIME)?.toLongOrNull() ?: JSONObject.NULL)
                        .put("title", extras?.optString(CalendarContract.Events.TITLE) ?: JSONObject.NULL)
                }
                val calendarFieldsValid = calendarEvidence.all { event ->
                    val begin = event.optString("beginTime").toLongOrNull()
                    val end = event.optString("endTime").toLongOrNull()
                    begin != null && end != null && end > begin && !event.isNull("title") && event.optString("title").isNotBlank() &&
                        case.titleFragments.all { event.optString("title").contains(it, ignoreCase = true) } &&
                        (expectedCalendarBegin == null || begin == expectedCalendarBegin)
                }
                result.put("calendarIntents", JSONArray(calendarEvidence)).put("expectedCalendarDispatchCount", expectedCalendarCount)
                    .put("observedCalendarDispatchCount", calendarIntents.size).put("expectedCalendarBeginTime", expectedCalendarBegin ?: JSONObject.NULL)
                    .put("calendarPersistenceEvidence", "NOT_VERIFIED_REQUIRES_USER_SAVE")
                    .put("calendarDispatchStatus", when {
                        expectedCalendarCount == 0 && calendarIntents.isEmpty() -> "NOT_APPLICABLE"
                        !calendarMonitorObserved -> "UNVERIFIED"
                        calendarIntents.size == expectedCalendarCount && calendarFieldsValid -> "PASS"
                        else -> "FAIL"
                    })
                if (calendarMonitorObserved && (calendarIntents.size != expectedCalendarCount || !calendarFieldsValid)) {
                    result.getJSONArray("issues").put("EXECUTION_FAILURE:calendar_insert_intent")
                    result.put("status", "FAIL").put("pipelineStatus", "FAIL")
                    if (case.route == "NO_ACTION" || case.route == "NEEDS_CLARIFICATION") result.put("safetyStatus", "FAIL")
                }
                val afterNotes = readNoteFiles(app, tree)
                val changedNotes = afterNotes.filter { (id, file) -> beforeNotes[id]?.optString("sha256") != file.optString("sha256") }.values
                result.put("noteBaseline", JSONArray(beforeNotes.values.map { JSONObject().put("name", it.getString("name")).put("sha256", it.getString("sha256")) }))
                    .put("noteFiles", JSONArray(afterNotes.values)).put("changedNoteFiles", JSONArray(changedNotes))
                val expectedNotes = case.tools.count { it == "obsidian_note_write" }
                if (expectedNotes > 0) {
                    val bodies = generatedNoteBodies(g4)
                    val headingRequested = case.input.contains("заголов", ignoreCase = true)
                    val plausibleBodies = bodies.all { body ->
                        Regex("\\p{L}{2,}").findAll(body).count() >= 2 && body.count(Char::isLetter) >= 10 &&
                            (!headingRequested || Regex("(?m)^#{1,6}\\s+\\S").containsMatchIn(body)) &&
                            !body.contains("<start_of_turn>") &&
                            !Regex("\\[(?:здесь|here)[^]]*]|\\b(?:TODO|TBD|PLACEHOLDER)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
                    }
                    val bodyWritten = bodies.size >= expectedNotes && bodies.all { body ->
                        changedNotes.any { it.getString("content").replace("\r\n", "\n").contains(body.replace("\r\n", "\n").trim()) }
                    }
                    result.put("noteGenerationEvidence", "CHANGED_FILE_AND_G4_BODY_MATCH; PLAUSIBILITY_IS_HEURISTIC")
                        .put("generatedNoteBodies", JSONArray(bodies))
                    if (changedNotes.isEmpty() || !bodyWritten || !plausibleBodies) {
                        result.getJSONArray("issues").put("EXECUTION_FAILURE:note_generation")
                        result.put("status", "FAIL").put("pipelineStatus", "FAIL")
                    }
                } else if (changedNotes.isNotEmpty()) {
                    result.getJSONArray("issues").put("UNEXPECTED_SIDE_EFFECT:note_file")
                    result.put("status", "FAIL").put("pipelineStatus", "FAIL")
                    if (case.route == "NO_ACTION" || case.route == "NEEDS_CLARIFICATION") result.put("safetyStatus", "FAIL")
                }
                writer.append(result)
                completed++
                android.util.Log.i("KsenaxLive", "case=${case.id} repetition=$repetition status=${result.getString("status")} latencyMs=${result.getLong("totalLatencyMs")}")
                if (result.getString("status") != "PASS") failures++
                if (ui.has("gateFailure") || ui.optBoolean("timeout")) throw AssertionError("BLOCKED: ${ui}; report=${writer.file}")
                if (failures > 0 && args.getString("stopOnFailure") == "true") throw AssertionError("Failed ${case.id}; report=${writer.file}")
            }
            complete = true
        } finally {
            try { input.stop() }
            finally {
                try {
                    instrumentation.runOnMainSync {
                        screenFlags.forEach { (activity, wasEnabled) ->
                            if (wasEnabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                } finally {
                    try { if (callbackRegistered) camera.unregisterTorchCallback(torchCallback) }
                    finally { if (monitorAdded) instrumentation.removeMonitor(monitor) }
                }
            }
            writer.append(JSONObject().put("type", "suite_finished").put("complete", complete)
                .put("completed", completed).put("expected", cases.size * repeats).put("failures", failures)
                .put("timestamp", ZonedDateTime.now().toString()))
        }
        assertTrue("$failures live failures; report=${writer.file}", failures == 0)
    }

    private fun readNoteFiles(app: KsenaxAndroidApplication, tree: android.net.Uri): Map<String, JSONObject> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val notes = linkedMapOf<String, JSONObject>()
        val cursor = checkNotNull(app.contentResolver.query(children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)) { "NOTE_QUERY_UNAVAILABLE" }
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val name = it.getString(1)
                if (!name.endsWith(".md")) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val bytes = checkNotNull(app.contentResolver.openInputStream(uri)) { "NOTE_READ_UNAVAILABLE:$name" }.use { stream -> stream.readBytes() }
                val content = bytes.toString(Charsets.UTF_8)
                notes[id] = JSONObject().put("name", name).put("bytes", bytes.size).put("content", content)
                    .put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) })
            }
        }
        return notes
    }

    private fun generatedNoteBodies(g4: LiveModelObserver): List<String> = g4.plans.flatMap { event ->
        val steps = runCatching {
            val raw = event.getString("output").trim()
            val json = if (raw.startsWith("```json\n") && raw.endsWith("\n```")) raw.removePrefix("```json\n").removeSuffix("\n```") else raw
            JSONObject(json).optJSONArray("steps")
        }.getOrNull()
        (0 until (steps?.length() ?: 0)).mapNotNull { index ->
            val step = steps?.optJSONObject(index)
            if (step?.optString("action") != "obsidian_note_write") null
            else step.optJSONObject("input")?.optString("markdown_body")?.takeIf(String::isNotBlank)
        }
    }

    private fun alarmIntentTimesMatch(case: LiveRoutingCase, started: ZonedDateTime, intents: List<JSONObject>): Boolean {
        if (intents.isEmpty()) return true
        val temporal = case.temporal?.takeIf { it.fieldName in setOf("time", "date_time") }
        val base = when {
            temporal != null -> temporal.expectedDateTime(started)?.toLocalTime() ?: LocalTime.parse(temporal.expectedTime)
            case.arguments["time"] != null -> LocalTime.parse(case.arguments.getValue("time"))
            case.arguments["minutes"] != null || case.arguments["hours"] != null -> {
                val dispatch = ZonedDateTime.parse(intents.first().getString("observedAt"))
                val seconds = case.arguments["minutes"]?.toLong()?.times(60)
                    ?: (case.arguments.getValue("hours").toDouble() * 3600).toLong()
                dispatch.plusSeconds(seconds).toLocalTime()
            }
            else -> return false
        }
        val relative = case.arguments.containsKey("minutes") || case.arguments.containsKey("hours")
        return intents.withIndex().all { (index, intent) ->
            val extras = intent.optJSONObject("extras")
            val hour = extras?.optString(AlarmClock.EXTRA_HOUR)?.toIntOrNull()
            val minute = extras?.optString(AlarmClock.EXTRA_MINUTES)?.toIntOrNull()
            if (hour !in 0..23 || minute !in 0..59) return@all false
            val expected = base.plusMinutes(index * 5L)
            val difference = kotlin.math.abs(requireNotNull(hour) * 60 + requireNotNull(minute) - expected.hour * 60 - expected.minute)
            // Intent наблюдается после вычисления времени executor-ом: допускаем переход минуты.
            minOf(difference, 1440 - difference) <= if (relative) 1 else 0
        }
    }
}
