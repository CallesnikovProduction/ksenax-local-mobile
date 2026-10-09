package com.kolesnikovprod.ksetaorch.live

import android.app.Instrumentation
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.policy.KsenaxPolicyContext
import com.kolesnikovprod.ksetaorch.communication.work.turn.*
import com.kolesnikovprod.ksetaorch.communication.work.routing.FunctionGemmaRoutingProtocol
import com.kolesnikovprod.ksetaorch.ui.controllers.*
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic.KsenaxAgenticChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

/** Только наблюдение: все обращения и результаты делегируются настоящим моделям.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class LiveModelObserver(private val delegate: KsenaxModelSession) : KsenaxModelSession by delegate {
    val functions = CopyOnWriteArrayList<JSONObject>()
    val plans = CopyOnWriteArrayList<JSONObject>()
    override suspend fun askFunctions(request: KsenaxModelFunctionRequest): KsenaxModelFunctionResponse {
        val started = SystemClock.elapsedRealtime()
        val event = JSONObject().put("functionNames", JSONArray(request.functions.map { it.name }))
            .put("requestUtf8Bytes", request.userMessage.toByteArray().size)
        functions.add(event)
        try {
            val response = delegate.askFunctions(request)
            event.put("latencyMs", response.latencyMs).put("calls", JSONArray(response.calls.map {
                JSONObject().put("name", it.name).put("arguments", JSONObject(it.argumentsJson))
            }))
            return response
        } catch (error: Exception) {
            event.put("error", error.toString())
            throw error
        } finally {
            // Включает создание Conversation, которое не входит в model latency.
            event.put("wallLatencyMs", SystemClock.elapsedRealtime() - started)
        }
    }
    override suspend fun askStateless(request: KsenaxModelRequest): KsenaxModelResponse {
        val started = SystemClock.elapsedRealtime()
        val event = JSONObject()
        plans.add(event)
        try {
            val response = delegate.askStateless(request)
            event.put("latencyMs", response.latencyMs).put("output", response.text)
            return response
        } catch (error: Exception) {
            event.put("error", error.toString())
            throw error
        } finally {
            event.put("wallLatencyMs", SystemClock.elapsedRealtime() - started)
        }
    }
    fun reset() { functions.clear(); plans.clear() }
}

/** Прозрачный наблюдатель production coordinator; не меняет policy и исполнение.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class LiveTurnObserver(private val delegate: KsenaxAgentRuntimeController) : KsenaxAgentRuntimeController by delegate {
    @Volatile var result: KsenaxAgentTurnResult? = null
    @Volatile var received: String? = null
    val stages = CopyOnWriteArrayList<String>()
    val calls = CopyOnWriteArrayList<JSONObject>()
    override suspend fun createCoordinator(workspaceTreeUri: String?, workspaceDisplayPath: String): Result<KsenaxAgentTurnRuntime> =
        delegate.createCoordinator(workspaceTreeUri, workspaceDisplayPath).map { runtime ->
            object : KsenaxAgentTurnRuntime by runtime {
                override suspend fun handleUserText(text: String, policyContext: KsenaxPolicyContext, onStage: suspend (KsenaxAgentTurnStage) -> Unit): KsenaxAgentTurnResult {
                    received = text
                    return runtime.handleUserText(text, policyContext) { stage ->
                        stages.add(stage.javaClass.simpleName)
                        if (stage is KsenaxAgentTurnStage.ExecutingTools) stage.calls.forEach { call ->
                            calls.add(JSONObject().put("name", call.name).put("arguments", JSONObject(call.arguments.JSONtoString())))
                        }
                        onStage(stage)
                    }.also { result = it }
                }
            }
        }
    fun reset() { result = null; received = null; stages.clear(); calls.clear() }
}

/** Ввод через события того же ViewModel, что обслуживает кнопку отправки.
 * Никаких прямых вызовов executor или подмены model verification.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class LiveInputDriver(
    private val instrumentation: Instrumentation,
    application: KsenaxAndroidApplication,
    val observer: LiveTurnObserver,
    workspaceUri: String,
    workspacePath: String,
) {
    lateinit var viewModel: KsenaxAgenticChatViewModel
    init {
        instrumentation.runOnMainSync {
            viewModel = KsenaxAgenticChatViewModel(null, workspaceUri, workspacePath, SavedStateHandle(),
                application.chatRepository, observer, application.agenticModelsIntegrityController, "Gemma-4 + FunctionGemma")
        }
    }
    fun submit(input: String, timeoutMs: Long): JSONObject {
        instrumentation.runOnMainSync {
            viewModel.onInputTextChanged(input)
            check(viewModel.uiState.value.inputText == input) { "INPUT_INJECTION_FAILURE" }
            viewModel.onSendClick()
        }
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = viewModel.uiState.value
            if (state.modelGateState is KsenaxBasicModelGateState.Failure) {
                return JSONObject().put("gateFailure", state.errorMessage).put("gate", state.modelGateState.toString())
            }
            if (observer.result != null && !state.isRunning && state.transientUserText == null) {
                return JSONObject().put("gate", state.modelGateState.toString())
                    .put("visibleResult", state.activeChat?.messages?.lastOrNull()?.text)
            }
            Thread.sleep(100)
        }
        instrumentation.runOnMainSync { viewModel.onStopTurn() }
        return JSONObject().put("timeout", true)
    }
    fun stop() = instrumentation.runOnMainSync { viewModel.onStopTurn() }
}

/** Отдельно оценивает выбор FG, фактический путь, итог и отсутствие side effects.
 * Локальный veto или переход в G4 не превращает неверный выбор FG в верный.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
object LiveResultEvaluator {
    fun evaluate(case: LiveRoutingCase, turn: LiveTurnObserver, fg: LiveModelObserver, g4: LiveModelObserver, ui: JSONObject, caseStarted: ZonedDateTime): JSONObject {
        val result = turn.result
        val rootCalls = fg.functions.firstOrNull()?.optJSONArray("calls")
        val rootCall = rootCalls?.takeIf { it.length() == 1 }?.optJSONObject(0)
        val rootChoice = rootCall?.let {
            if (it.optString("name") == "route_request") it.optJSONObject("arguments")?.optString("action")
            else it.optString("name")
        }?.takeIf(String::isNotBlank)
        val refinement = fg.functions.drop(1).firstOrNull { event ->
            (event.optJSONArray("functionNames")?.length() ?: 0) > 1 &&
                event.optJSONArray("functionNames")?.toString()?.contains("route_") != true
        }
        val refinementCalls = refinement?.optJSONArray("calls")
        val refinementChoice = refinementCalls?.takeIf { it.length() == 1 }?.optJSONObject(0)?.optString("name")
        val rootRoute = when (rootChoice) {
            null -> "FAILURE"
            "route_planned_work" -> "LLM_BOUND"
            "route_clarification" -> "NEEDS_CLARIFICATION"
            "route_unsupported" -> "UNSUPPORTED"
            else -> "FAST_TOOL"
        }
        val route = if (g4.plans.isNotEmpty()) "LLM_BOUND" else rootRoute
        val outcome = when (result) {
            is KsenaxAgentTurnResult.Clarification -> "NEEDS_CLARIFICATION"
            is KsenaxAgentTurnResult.Refusal -> "UNSUPPORTED"
            is KsenaxAgentTurnResult.ToolExecution -> "TOOL_EXECUTION"
            else -> "FAILURE"
        }
        val sourceVeto = rootRoute in setOf("FAST_TOOL", "LLM_BOUND") && g4.plans.isEmpty() &&
            result is KsenaxAgentTurnResult.Clarification && turn.calls.isEmpty()
        val noAction = case.route == "NO_ACTION"
        val budgetForced = case.input.toByteArray(Charsets.UTF_8).size > FunctionGemmaRoutingProtocol.MAX_ROUTING_INPUT_BYTES &&
            fg.functions.firstOrNull()?.optJSONArray("functionNames")?.let {
                it.length() == 1 && it.optString(0) == "route_planned_work"
            } == true
        val semanticMatch = when (case.route) {
            "FAST_TOOL" -> rootRoute == "FAST_TOOL" && rootChoice == case.tools.singleOrNull()
            "LLM_BOUND" -> rootChoice == "route_planned_work"
            "NEEDS_CLARIFICATION" -> rootChoice == "route_clarification"
            "NO_ACTION" -> rootChoice in setOf("route_clarification", "route_unsupported")
            else -> rootRoute == case.route
        }
        val noActionSafety = turn.calls.isEmpty() &&
            (result !is KsenaxAgentTurnResult.ToolExecution || result.toolResult.executed.isEmpty())
        // Ответ модели, отвергнутый parser/domain, не является падением native runtime.
        // При отсутствии ответа нужной стадии причину не угадываем по предыдущему FG.
        val returnedOutputAtFailure = when (turn.stages.lastOrNull()) {
            "Routing" -> fg.functions.lastOrNull()?.has("calls") == true
            "Planning" -> g4.plans.lastOrNull()?.has("output") == true
            "CompilingAction" -> fg.functions.size > 1 && fg.functions.lastOrNull()?.has("calls") == true
            else -> false
        }
        val failureLayer = when {
            ui.has("gateFailure") -> "MODEL_VERIFICATION"
            ui.optBoolean("timeout") -> "MODEL_TIMEOUT"
            fg.functions.any { it.has("error") } || g4.plans.any { it.has("error") } -> "MODEL_INFERENCE"
            outcome != "FAILURE" -> "NONE"
            returnedOutputAtFailure -> "MODEL_OUTPUT"
            else -> "UNKNOWN"
        }
        val issues = mutableListOf<String>()
        if (failureLayer in setOf("MODEL_VERIFICATION", "MODEL_TIMEOUT", "MODEL_INFERENCE")) issues.add("MODEL_RUNTIME_FAILURE")
        if (failureLayer == "MODEL_OUTPUT") issues.add("MODEL_OUTPUT_FAILURE")
        if (failureLayer == "UNKNOWN") issues.add("PIPELINE_FAILURE_UNKNOWN")
        if (turn.received != case.input) issues.add(if (ui.has("gateFailure")) "MODEL_RUNTIME_FAILURE" else "INPUT_INJECTION_FAILURE")
        if (fg.functions.isEmpty()) issues.add("MODEL_INVOCATION_NOT_OBSERVED")
        if (!budgetForced && !semanticMatch) issues.add("MODEL_ROUTING_MISCLASSIFICATION")
        if (case.route in setOf("FAST_TOOL", "LLM_BOUND") && route != case.route) issues.add("ROUTING_MISCLASSIFICATION")
        if (noAction && !noActionSafety) issues.add("WRONG_TOOL")
        if (case.route == "FAST_TOOL" && g4.plans.isNotEmpty()) issues.add("UNEXPECTED_LLM_ESCALATION")
        if (case.route == "LLM_BOUND" && g4.plans.isEmpty()) issues.add("MISSING_LLM_ESCALATION")
        val toolNames = turn.calls.map { it.getString("name") }
        if (case.tools.isNotEmpty() && toolNames != case.tools) issues.add("WRONG_TOOL")
        case.arguments.forEach { (key, value) ->
            val actual = turn.calls.firstOrNull()?.getJSONObject("arguments")?.opt(key)?.toString()
            val numericMatch = actual?.toDoubleOrNull()?.let { it == value.toDoubleOrNull() } == true
            if (actual != value && !numericMatch) issues.add("ARGUMENT_EXTRACTION_FAILURE:$key")
        }
        val expectedArguments = case.arguments.toMutableMap()
        if (case.titleFragments.isNotEmpty()) {
            val title = turn.calls.firstOrNull { it.optString("name") == "calendar_event_create" }
                ?.optJSONObject("arguments")?.optString("title").orEmpty()
            if (case.titleFragments.any { !title.contains(it, ignoreCase = true) }) issues.add("CALENDAR_TITLE_FACT_LOSS")
        }
        case.temporal?.let { temporal ->
            val expected = temporal.expectedValue(caseStarted)
            expectedArguments[temporal.fieldName] = expected
            val actual = turn.calls.firstOrNull()?.optJSONObject("arguments")?.optString(temporal.fieldName)
            val matches = runCatching {
                val observed = requireNotNull(actual)
                if (temporal.expectedDateTime(caseStarted) != null) LocalDateTime.parse(observed) == LocalDateTime.parse(expected)
                else LocalTime.parse(observed) == LocalTime.parse(expected)
            }.getOrDefault(false)
            if (!matches) issues.add("TEMPORAL_ARGUMENT_FAILURE:${temporal.fieldName}")
        }
        if (result is KsenaxAgentTurnResult.ToolExecution) {
            if (result.toolResult.executed.any { it is KsenaxToolResult.Failure } || result.toolResult.denied.isNotEmpty() || result.toolResult.pendingConfirmation.isNotEmpty()) issues.add("EXECUTION_FAILURE")
            if (result.toolResult.executed.size != case.tools.size) issues.add("MISSING_TOOL")
        }
        if (case.route in setOf("FAST_TOOL", "LLM_BOUND") && result !is KsenaxAgentTurnResult.ToolExecution) issues.add("MISSING_TOOL_EXECUTION")
        if (case.route == "NEEDS_CLARIFICATION" && turn.calls.isNotEmpty()) issues.add("CLARIFICATION_FAILURE")
        if (case.route == "NEEDS_CLARIFICATION" && result !is KsenaxAgentTurnResult.Clarification) issues.add("CLARIFICATION_FAILURE")
        val pipelineIssues = issues.filter { it != "MODEL_ROUTING_MISCLASSIFICATION" }
        return JSONObject().put("id", case.id).put("input", case.input).put("expectedRoute", case.route)
            .put("actualRoute", route).put("expectedTools", JSONArray(case.tools)).put("actualTools", JSONArray(toolNames))
            .put("rootChoice", rootChoice ?: JSONObject.NULL).put("rootRoute", rootRoute).put("effectivePath", route)
            .put("outcome", outcome).put("sourceVeto", sourceVeto)
            .put("refinementInvoked", refinement != null).put("refinementChoice", refinementChoice ?: JSONObject.NULL)
            .put("fallback", if (rootRoute == "FAST_TOOL" && g4.plans.isNotEmpty()) "LOCAL_CONSTRAINT_TO_G4" else "NONE")
            .put("sourceVetoMeaning", "Local non-model clarification after a model choice; exact veto reason is not instrumented.")
            .put("evaluationVersion", 2).put("routingBudgetForced", budgetForced).put("failureLayer", failureLayer)
            .put("semanticStatus", if (budgetForced) "NOT_APPLICABLE" else if (semanticMatch) "PASS" else "FAIL")
            .put("pipelineStatus", if (pipelineIssues.isEmpty()) "PASS" else "FAIL")
            .put("safetyStatus", if (!noAction && case.route != "NEEDS_CLARIFICATION") "NOT_APPLICABLE" else if (noActionSafety) "PASS" else "FAIL")
            .put("executedTools", JSONArray((result as? KsenaxAgentTurnResult.ToolExecution)?.toolResult?.executed?.map { it.toolName } ?: emptyList<String>()))
            .put("expectedArguments", JSONObject(expectedArguments)).put("caseStartedAt", caseStarted.toString())
            .put("expectedTitleFragments", JSONArray(case.titleFragments))
            .put("temporalExpectation", case.temporal?.let { temporal -> JSONObject()
                .put("expectedTime", temporal.expectedTime).put("fieldName", temporal.fieldName)
                .put("dateOffsetDays", temporal.dateOffsetDays ?: JSONObject.NULL).put("weekday", temporal.weekday?.name ?: JSONObject.NULL)
                .put("resolvedValue", temporal.expectedValue(caseStarted)) } ?: JSONObject.NULL)
            .put("gemma4Invoked", g4.plans.isNotEmpty())
            .put("inputReceived", turn.received).put("stages", JSONArray(turn.stages)).put("calls", JSONArray(turn.calls))
            .put("functionGemma", JSONArray(fg.functions)).put("gemma4", JSONArray(g4.plans))
            .put("result", result.toString()).put("ui", ui).put("issues", JSONArray(issues.distinct()))
            .put("status", if (issues.isEmpty()) "PASS" else "FAIL")
    }
}

/** Сохраняет каждый законченный случай сразу, в том числе перед возможным native crash.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class LiveArtifactWriter(directory: File, runId: String) {
    val file = File(directory.apply { mkdirs() }, "$runId.jsonl")
    init { check(file.createNewFile()) { "RunId already exists: $runId" } }
    fun append(event: JSONObject) { file.appendText(event.toString() + "\n", Charsets.UTF_8) }
}
