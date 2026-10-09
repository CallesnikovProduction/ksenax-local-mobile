package com.kolesnikovprod.ksetaorch.live

import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.*
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionDeclaration
import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.FunctionCallAdapter
import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.toFunctionJson
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.*
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.*
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.*
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.*
import com.kolesnikovprod.ksetaorch.communication.work.planning.G4PlanningPromptFactory
import com.kolesnikovprod.ksetaorch.communication.work.planning.G4PlanningResponseParser
import com.kolesnikovprod.ksetaorch.communication.work.planning.PlanningParseResult
import com.kolesnikovprod.ksetaorch.storage.resolve.text.UserSelectedTextWorkspaceStorage
import java.io.File
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Диагностика сырого G4-ответа и бюджета SDK; ни один executor не вызывается.
 * Не является доказательством маршрутизации или исполнения инструмента.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalApi::class)
class LivePlannerDiagnosticsTest {
    @Test fun inspectRawPlannerResponse() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        check(app.gemmaModelSession.runtimeConfig.maxContextTokens == 4096)
        val constrained = args.getString("constrained") != "false"
        val nativePlan = args.getString("mode") == "native_plan"
        require(!nativePlan || constrained) { "Native plan probe requires constrained decoding" }
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"),
            args.getString("runId") ?: "g4-raw-$constrained")
        val now = ZonedDateTime.now()
        val noteWriter = ObsidianWriterToolExecutor {
            UserSelectedTextWorkspaceStorage(app, Uri.parse(
                "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FOpenKsenax-live-tests-20261004"))
        }
        // Тот же каталог, что у production controller; здесь используются только spec-данные.
        val kits = listOf(TorchToolModule(TorchExecutor(app)), AlarmOneShotToolModule(AlarmOneShotExecutor(app)),
            CalendarEventOneShotToolModule(CalendarEventOneShotExecutor(CalendarEventToolExecutor(app))),
            ObsidianNoteOneShotToolModule(ObsidianNoteOneShotExecutor(noteWriter)))
        val specs = kits.flatMap { it.actionSpecs }
        val case = LiveRoutingCorpus.cases.single { it.id == (args.getString("caseId") ?: "note_0") }
        val request = G4PlanningPromptFactory(specs).buildPlanningRequest(case.input, now.toString())
        val planSchema = """{"type":"object","properties":{
            "type":{"type":"string","enum":["plan","clarification","refusal"]},
            "steps":{"type":"array","minItems":1,"maxItems":32,"items":{"type":"object","properties":{
                "action":{"type":"string","enum":${JSONArray(specs.map { it.name })}},
                "input":{"type":"object","properties":{
                    "title":{"type":"string"},"markdown_body":{"type":"string"},"analysis_markdown":{"type":"string"},
                    "time":{"type":"string"},"date_time":{"type":"string"},"start_local_date_time":{"type":"string"},
                    "minutes":{"type":"integer"},"hours":{"type":"number"},"count":{"type":"integer"},
                    "duration_minutes":{"type":"integer"},"location":{"type":"string"},"description":{"type":"string"},"label":{"type":"string"}
                },"additionalProperties":false}
            },"required":["action","input"],"additionalProperties":false}},
            "question":{"type":"string"},"reason":{"type":"string"},"comment":{"type":"string"}
        },"required":["type"],"additionalProperties":false}"""
        val systemInstruction = if (nativePlan) "You are a deterministic Android action planner. " +
            "Return exactly one emit_work_plan function call. Its arguments ARE the complete JSON response " +
            "specified in the user message. Do not execute actions or defer writing finished Russian note content."
            else request.systemInstruction
        writer.append(JSONObject().put("type", "environment").put("timestamp", now.toString())
            .put("context", 4096).put("backend", "CPU").put("constrained", constrained)
            .put("mode", if (nativePlan) "native_plan" else "raw_text").put("caseId", case.id)
            .put("prompt", request.prompt).put("systemInstruction", systemInstruction)
            .put("executorsInvoked", false))
        val previousConstrained = ExperimentalFlags.enableConversationConstrainedDecoding
        val previousBenchmark = ExperimentalFlags.enableBenchmark
        try {
            ExperimentalFlags.enableConversationConstrainedDecoding = constrained
            ExperimentalFlags.enableBenchmark = true
            Engine(EngineConfig(modelPath = app.gemmaInstallUseCase.getGemma4E2BModelPath(),
                backend = Backend.CPU(), audioBackend = Backend.CPU(), maxNumTokens = 4096,
                cacheDir = File(app.cacheDir, "live-g4-diagnostic").apply { mkdirs() }.absolutePath)).use { engine ->
                engine.initialize()
                val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction),
                    tools = if (nativePlan) listOf(tool(FunctionCallAdapter(KsenaxModelFunctionDeclaration(
                        "emit_work_plan", "Return a complete plan, clarification or refusal as data, without executing it.", planSchema))))
                        else emptyList(),
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    automaticToolCalling = false, extraContext = mapOf("enable_thinking" to false))
                // Renderer меняет свою историю: не используем эту conversation для inference.
                engine.createConversation(config).use { renderer ->
                    writer.append(JSONObject().put("type", "rendered_prompt")
                        .put("text", renderer.renderMessageIntoString(Message.user(request.prompt))))
                }
                engine.createConversation(config).use { conversation ->
                    val started = SystemClock.elapsedRealtime()
                    val proposedCalls = mutableListOf<ToolCall>()
                    try {
                        withTimeout(360_000) {
                            conversation.sendMessageAsync(request.prompt).collect { message ->
                                proposedCalls += message.toolCalls
                                writer.append(JSONObject().put("type", "sdk_message")
                                    .put("contents", message.toString()).put("channels", JSONObject(message.channels))
                                    .put("toolCalls", JSONArray(message.toolCalls.map { call ->
                                        JSONObject().put("name", call.name).put("arguments", JSONObject(call.arguments))
                                    })))
                            }
                        }
                        if (nativePlan) {
                            val call = proposedCalls.singleOrNull()?.takeIf { it.name == "emit_work_plan" }
                            val parsed = call?.let { G4PlanningResponseParser.parse(
                                it.arguments.toFunctionJson().toString(), specs.map { spec -> spec.name }.toSet()) }
                            writer.append(JSONObject().put("type", "plan_structure_verdict")
                                .put("callCount", proposedCalls.size).put("valid", parsed is PlanningParseResult.Success)
                                .put("reason", (parsed as? PlanningParseResult.Failure)?.reason
                                    ?: if (call == null) "Expected exactly one emit_work_plan call" else JSONObject.NULL)
                                .put("domainAndExecutionVerified", false))
                        }
                        val benchmark = conversation.getBenchmarkInfo()
                        writer.append(JSONObject().put("type", "completed")
                            .put("latencyMs", SystemClock.elapsedRealtime() - started)
                            .put("tokenCount", conversation.getTokenCount())
                            .put("prefillTokens", benchmark.lastPrefillTokenCount)
                            .put("decodeTokens", benchmark.lastDecodeTokenCount)
                            .put("timeToFirstTokenSeconds", benchmark.timeToFirstTokenInSecond)
                            .put("prefillTokensPerSecond", benchmark.lastPrefillTokensPerSecond)
                            .put("decodeTokensPerSecond", benchmark.lastDecodeTokensPerSecond))
                    } catch (error: Exception) {
                        if (error is CancellationException) runCatching { conversation.cancelProcess() }
                        writer.append(JSONObject().put("type", "error").put("error", error.toString()))
                        throw error
                    }
                }
            }
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = previousConstrained
            ExperimentalFlags.enableBenchmark = previousBenchmark
        }
    }
}
