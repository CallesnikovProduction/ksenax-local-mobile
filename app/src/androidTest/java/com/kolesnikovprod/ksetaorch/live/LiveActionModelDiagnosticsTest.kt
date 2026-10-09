package com.kolesnikovprod.ksetaorch.live

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.*
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionCall
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionResponse
import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.FunctionCallAdapter
import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.toFunctionJson
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.work.routing.FunctionGemmaRoutingProtocol
import com.kolesnikovprod.ksetaorch.communication.work.routing.KsenaxRoutingDecision
import com.kolesnikovprod.ksetaorch.communication.work.routing.QwenRoutingProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Сравнивает native-вызов и короткий текстовый выбор на отдельном файле модели.
 * Не меняет установленные модели, не запускает G4 и Android-действия.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalApi::class)
class LiveActionModelDiagnosticsTest {
    @Test fun compareCandidateRouting() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        val candidate = LiveCandidateModel.resolve(app, args)
        val model = candidate.file
        val expectedSha = candidate.sha256
        val contextTokens = args.getString("contextTokens")?.toInt() ?: 1024
        val thinkingArgument = args.getString("enableThinking") ?: "false"
        require(thinkingArgument in setOf("true", "false"))
        val enableThinking = thinkingArgument == "true"
        val thinkingMode = args.getString("thinkingMode") ?: "metadata"
        require(thinkingMode in setOf("metadata", "template_non_thinking"))
        val templateMode = args.getString("templateMode") ?: "metadata"
        require(templateMode in setOf("metadata", "qwen_official"))
        val officialTemplate = if (templateMode == "qwen_official") InstrumentationRegistry.getInstrumentation().context.assets
            .open("qwen3-0.6b-0.13.1.jinja").bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        val selected = requireNotNull(args.getString("cases")).split(",").toSet()
        val cases = LiveRoutingCorpus.cases.filter { it.id in selected }
        require(cases.isNotEmpty() && cases.map { it.id }.toSet() == selected)
        require(cases.all { it.input.toByteArray(Charsets.UTF_8).size <= FunctionGemmaRoutingProtocol.MAX_ROUTING_INPUT_BYTES })
        val variants = args.getString("variants")?.split(",") ?: listOf("native_catalog", "text_choice")
        require(variants.isNotEmpty() && variants.distinct().size == variants.size &&
            variants.all { it in setOf("native_catalog", "text_choice", "raw_native", "structured_json", "structured_english", "native_complete", "native_envelope", "json_prefill", "hermes_text") })
        require(thinkingMode == "metadata" || variants.none { it in setOf("raw_native", "json_prefill") })
        require(officialTemplate == null || thinkingMode == "metadata" && variants.none { it in setOf("raw_native", "json_prefill") })
        require(!enableThinking || thinkingMode == "metadata" && variants.none { it in setOf("raw_native", "json_prefill") })
        val sampling = args.getString("sampling") ?: "greedy"
        require(sampling in setOf("greedy", "qwen_non_thinking", "qwen_thinking"))
        val sampler = when (sampling) {
            "qwen_non_thinking" -> SamplerConfig(topK = 20, topP = 0.8, temperature = 0.7)
            "qwen_thinking" -> SamplerConfig(topK = 20, topP = 0.95, temperature = 0.6)
            else -> SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)
        }
        val declarations = TorchOneShotProtocol.declarations + AlarmOneShotProtocol.declarations + CalendarEventOneShotProtocol.declarations
        val protocol = FunctionGemmaRoutingProtocol(declarations)
        val qwenProtocol = QwenRoutingProtocol(declarations)
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"), requireNotNull(args.getString("runId")))
        writer.append(JSONObject().put("type", "environment").put("timestamp", ZonedDateTime.now().toString())
            .put("modelPath", model.path).put("modelSha256", expectedSha).put("modelBytes", model.length())
            .put("sdk", "0.13.1").put("backend", "CPU").put("contextTokens", contextTokens)
            .put("thinkingRequested", enableThinking).put("thinkingMode", thinkingMode).put("constrainedDecoding", false).put("executorsInvoked", false)
            .put("templateMode", templateMode)
            .put("sampling", sampling).put("topK", sampler.topK).put("topP", sampler.topP).put("temperature", sampler.temperature)
            .put("gemma4Invoked", false).put("expectedResults", cases.size * variants.size)
            .put("selectedCases", JSONArray(cases.map { it.id })).put("variants", JSONArray(variants)))
        val previousConstrained = ExperimentalFlags.enableConversationConstrainedDecoding
        val previousBenchmark = ExperimentalFlags.enableBenchmark
        var completed = 0
        var complete = false
        try {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
            ExperimentalFlags.enableBenchmark = true
            Engine(candidate.engineConfig(app, contextTokens)).use { engine ->
                val loadStarted = SystemClock.elapsedRealtime()
                engine.initialize()
                writer.append(JSONObject().put("type", "engine_ready").put("loadLatencyMs", SystemClock.elapsedRealtime() - loadStarted)
                    .put("processPssKb", Debug.getPss()))
                for ((index, case) in cases.withIndex()) for (variant in if (index % 2 == 0) variants else variants.reversed()) {
                    val caseStarted = ZonedDateTime.now()
                    val request = protocol.buildRequest(case.input)
                    val structuredRequest = qwenProtocol.buildRequest(case.input, caseStarted)
                    val names = request.functions.map { it.name }.toSet()
                    val native = variant in setOf("native_catalog", "raw_native", "native_complete", "native_envelope", "hermes_text")
                    val evaluatedArguments = variant in setOf("structured_json", "structured_english", "native_complete", "native_envelope", "json_prefill", "hermes_text")
                    val instruction = when (variant) {
                        "structured_json" -> structuredRequest.systemInstruction
                        "structured_english", "json_prefill" -> QwenDiagnosticPrompts.englishSystem(declarations, caseStarted)
                        "native_complete", "hermes_text" -> QwenDiagnosticPrompts.nativeSystem(caseStarted)
                        "native_envelope" -> QwenDiagnosticPrompts.envelopeSystem(declarations, caseStarted)
                        else -> if (native) request.systemInstruction else
                        "Classify the user request. Reply with exactly one catalogue code and nothing else. " +
                            "Use route_planned_work for writing/analysis or different operations, not for a quantity of one operation. " +
                            "Use route_clarification for missing required values, route_unsupported for questions, facts, negations or unsupported commands."
                    }
                    val prompt = if (evaluatedArguments || native) case.input else buildString {
                        appendLine("Catalogue:")
                        request.functions.forEach { appendLine("${it.name}: ${it.description}") }
                        appendLine("User request:"); append(case.input)
                    }
                    val functions = when (variant) {
                        "native_complete", "hermes_text" -> QwenDiagnosticPrompts.nativeDeclarations(declarations)
                        "native_envelope" -> QwenDiagnosticPrompts.envelopeDeclaration(declarations)
                        else -> request.functions
                    }
                    val config = ConversationConfig(systemInstruction = Contents.of(instruction),
                        tools = if (native) functions.map { tool(FunctionCallAdapter(it)) } else emptyList(),
                        automaticToolCalling = false, samplerConfig = sampler,
                        channels = if (enableThinking) listOf(Channel("thinking", "<think>", "</think>")) else null,
                        extraContext = mapOf("enable_thinking" to enableThinking))
                    writer.append(JSONObject().put("type", "case_started").put("id", case.id).put("variant", variant)
                        .put("input", case.input).put("systemInstruction", instruction).put("prompt", prompt))
                    val event = JSONObject().put("type", "diagnostic_result").put("id", case.id).put("variant", variant)
                        .put("expectedRoute", case.route).put("expectedTools", JSONArray(case.tools))
                    fun verifyDeclarations(rendered: String) {
                        val present = rendered.contains("<tools>") && functions.all { function ->
                            Regex("\"name\"\\s*:\\s*\"${Regex.escape(function.name)}\"").containsMatchIn(rendered)
                        }
                        if (!present) {
                            writer.append(event.put("status", "BLOCKED").put("failureStage", "DECLARATION_RENDER")
                                .put("semanticStatus", "NOT_EVALUATED").put("structuredValidity", "NOT_EVALUATED"))
                            error("Model template omitted declared functions; inference is not a routing test")
                        }
                    }
                    if (variant == "raw_native") {
                        val rendered = engine.createConversation(config).use { renderer ->
                            renderer.renderMessageIntoString(Message.user(prompt))
                        }
                        writer.append(JSONObject().put("type", "rendered_prompt").put("id", case.id)
                            .put("variant", variant).put("text", rendered))
                        // Renderer меняет историю; равенство проверяем на отдельном экземпляре.
                        identityConversation(engine, config).use { checker ->
                            check(checker.renderMessageIntoString(Message.user(rendered)) == rendered) {
                                "Raw replay would modify the already rendered prompt"
                            }
                        }
                        identityConversation(engine, config).use { conversation ->
                            val started = SystemClock.elapsedRealtime()
                            val raw = StringBuilder()
                            try {
                                withTimeout(60_000L) {
                                    conversation.sendMessageAsync(rendered).collect { message ->
                                        check(message.toolCalls.isEmpty()) { "Raw capture unexpectedly parsed a tool call" }
                                        val chunk = message.toString()
                                        raw.append(chunk)
                                        writer.append(JSONObject().put("type", "raw_chunk").put("id", case.id).put("text", chunk))
                                    }
                                }
                                val benchmark = conversation.getBenchmarkInfo()
                                event.put("rawDecode", raw.toString()).put("semanticStatus", "NOT_EVALUATED")
                                    .put("toolParserInvoked", false).put("templateApplied", "RENDER_ONCE_IDENTITY_REPLAY")
                                    .put("prefillTokens", benchmark.lastPrefillTokenCount).put("decodeTokens", benchmark.lastDecodeTokenCount)
                                    .put("contextTokensUsed", conversation.getTokenCount())
                            } catch (cancelled: CancellationException) {
                                runCatching { conversation.cancelProcess() }
                                event.put("status", "BLOCKED").put("error", cancelled.toString())
                                throw cancelled
                            } catch (error: Exception) { event.put("error", error.toString()) }
                            finally {
                                writer.append(event.put("wallLatencyMs", SystemClock.elapsedRealtime() - started)
                                    .put("processPssKb", Debug.getPss()))
                                completed++
                            }
                        }
                        continue
                    }
                    if (variant != "json_prefill" && thinkingMode == "metadata" && (index == 0 || native)) LiveQwenConversation.create(engine, config, officialTemplate).use { renderer ->
                        val rendered = renderer.renderMessageIntoString(Message.user(prompt))
                        writer.append(JSONObject().put("type", "rendered_prompt").put("variant", variant)
                            .put("id", case.id).put("text", rendered))
                        if (native) verifyDeclarations(rendered)
                    }
                    val prefix = if (variant == "json_prefill") "{\"route\":\"" else ""
                    val nonThinking = if (thinkingMode == "template_non_thinking") LiveQwenConversation.prepare(engine, config, prompt) else null
                    val inferencePrompt = if (nonThinking != null) nonThinking.prompt else if (variant == "json_prefill") {
                        val rendered = engine.createConversation(config).use { it.renderMessageIntoString(Message.user(prompt)) } + prefix
                        writer.append(JSONObject().put("type", "rendered_prompt").put("id", case.id).put("variant", variant)
                            .put("assistantPrefix", prefix).put("text", rendered))
                        identityConversation(engine, config).use { checker ->
                            check(checker.renderMessageIntoString(Message.user(rendered)) == rendered)
                        }
                        rendered
                    } else prompt
                    val inference = nonThinking?.conversation ?: if (variant == "json_prefill") identityConversation(engine, config) else LiveQwenConversation.create(engine, config, officialTemplate)
                    inference.use { conversation ->
                        if (nonThinking != null) writer.append(JSONObject().put("type", "rendered_prompt").put("id", case.id)
                            .put("variant", variant).put("text", nonThinking.prompt))
                        if (nonThinking != null && native) verifyDeclarations(nonThinking.prompt)
                        val started = SystemClock.elapsedRealtime()
                        val text = StringBuilder(prefix)
                        val calls = mutableListOf<KsenaxModelFunctionCall>()
                        var failureStage = "INFERENCE"
                        try {
                            withTimeout(60_000L) {
                                conversation.sendMessageAsync(inferencePrompt).collect { message ->
                                    text.append(message.toString())
                                    calls += message.toolCalls.map { KsenaxModelFunctionCall(it.name, it.arguments.toFunctionJson().toString()) }
                                    writer.append(JSONObject().put("type", "sdk_message").put("id", case.id).put("variant", variant)
                                        .put("text", message.toString()).put("channels", JSONObject(message.channels))
                                        .put("calls", JSONArray(message.toolCalls.map { JSONObject().put("name", it.name)
                                            .put("arguments", it.arguments.toFunctionJson().toString()) })))
                                }
                            }
                            event.put("text", text.toString()).put("calls", JSONArray(calls.map {
                                JSONObject().put("name", it.name).put("arguments", it.argumentsJson)
                            }))
                            if (variant == "json_prefill") event.put("assistantPrefix", prefix)
                                .put("rawDecode", text.toString().removePrefix(prefix))
                            failureStage = "METRICS"
                            val benchmark = conversation.getBenchmarkInfo()
                            event.put("prefillTokens", benchmark.lastPrefillTokenCount).put("decodeTokens", benchmark.lastDecodeTokenCount)
                                .put("contextTokensUsed", conversation.getTokenCount()).put("ttftSeconds", benchmark.timeToFirstTokenInSecond)
                            failureStage = "VALIDATION"
                            if (evaluatedArguments) {
                                val decision = if (variant == "hermes_text") {
                                    require(calls.isEmpty()) { "Hermes text adapter must receive raw text, not native SDK calls" }
                                    qwenProtocol.parseHermesResponse(text.toString())
                                } else if (variant == "native_envelope") {
                                    require(calls.size == 1 && calls.single().name == "route_request")
                                    qwenProtocol.parseResponse(calls.single().argumentsJson)
                                } else if (variant == "native_complete") {
                                    require(calls.size == 1) { "Qwen must propose exactly one routing call" }
                                    val call = calls.single()
                                    val arguments = Json.parseToJsonElement(call.argumentsJson) as? JsonObject
                                        ?: error("Native routing arguments must be an object")
                                    when (call.name) {
                                        "route_planned_work", "route_clarification", "route_unsupported" -> {
                                            require(arguments.isEmpty())
                                            when (call.name) {
                                                "route_planned_work" -> KsenaxRoutingDecision.PlannedWork
                                                "route_clarification" -> KsenaxRoutingDecision.Clarification(null)
                                                else -> KsenaxRoutingDecision.Unsupported(null)
                                            }
                                        }
                                        else -> qwenProtocol.parseResponse(buildJsonObject {
                                            put("route", "FAST_TOOL"); put("tool", call.name); put("arguments", arguments)
                                        }.toString())
                                    }
                                } else {
                                    require(calls.isEmpty()) { "Structured router unexpectedly returned native calls" }
                                    qwenProtocol.parseResponse(text.toString())
                                }
                                val actual = when (decision) {
                                    is KsenaxRoutingDecision.FastTool -> "FAST_TOOL"
                                    KsenaxRoutingDecision.PlannedWork -> "LLM_BOUND"
                                    is KsenaxRoutingDecision.Clarification -> "NEEDS_CLARIFICATION"
                                    is KsenaxRoutingDecision.Unsupported -> "NO_ACTION"
                                }
                                val tools = (decision as? KsenaxRoutingDecision.FastTool)?.let { listOf(it.toolName) }.orEmpty()
                                val semanticMatch = actual == case.route && (actual != "FAST_TOOL" || tools == case.tools)
                                event.put("actualRoute", actual).put("actualTools", JSONArray(tools)).put("structuredValidity", "PASS")
                                    .put("semanticStatus", if (semanticMatch) "PASS" else "FAIL")
                                if (decision is KsenaxRoutingDecision.FastTool) {
                                    event.put("arguments", decision.arguments.toString())
                                    val expected = case.arguments.toMutableMap()
                                    case.temporal?.let { expected[it.fieldName] = it.expectedValue(caseStarted) }
                                    val expectedValuesMatch = expected.all { (key, value) ->
                                        val actualValue = decision.arguments[key]?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                                        if (actualValue == null) key == "count" && value == "1"
                                        else if (actualValue.isString) actualValue.content == value
                                        else actualValue.content.toDoubleOrNull()?.let { number ->
                                            value.toDoubleOrNull()?.let { expectedNumber -> number == expectedNumber }
                                        } == true
                                    }
                                    val title = (decision.arguments["title"] as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.takeIf { it.isString }?.content?.lowercase()?.replace('ё', 'е').orEmpty()
                                    val titleFactsMatch = case.titleFragments.all { title.contains(it.lowercase().replace('ё', 'е')) }
                                    val argumentMatch = expectedValuesMatch && titleFactsMatch
                                    event.put("expectedTitleFragments", JSONArray(case.titleFragments))
                                        .put("titleFactsStatus", if (titleFactsMatch) "PASS" else "FAIL")
                                    val selectedProtocol = listOf(TorchOneShotProtocol, AlarmOneShotProtocol, CalendarEventOneShotProtocol)
                                        .single { it.declarations.any { declaration -> declaration.codeName == decision.toolName } }
                                    selectedProtocol.parseFunctionCall(KsenaxModelFunctionCall(decision.toolName, decision.arguments.toString()))
                                    event.put("argumentSchemaStatus", "PASS").put("argumentAccuracy", if (argumentMatch) "PASS" else "FAIL")
                                    event.put("status", if (semanticMatch && argumentMatch) "PASS" else "FAIL")
                                } else event.put("status", if (semanticMatch) "PASS" else "FAIL")
                            } else {
                            val code = if (native) {
                                protocol.parseResponse(KsenaxModelFunctionResponse(calls, 0))
                                calls.single().name
                            } else {
                                require(calls.isEmpty()) { "Text classifier returned a tool call" }
                                text.toString().trim().also { choice ->
                                    require(choice in names) { "Text classifier must return one exact catalogue code" }
                                    event.put("textChoice", choice)
                                }
                            }
                            val actual = when (code) {
                                "route_planned_work" -> "LLM_BOUND"
                                "route_clarification" -> "NEEDS_CLARIFICATION"
                                "route_unsupported" -> "NO_ACTION"
                                else -> "FAST_TOOL"
                            }
                            val tools = if (actual == "FAST_TOOL") listOf(code) else emptyList()
                            event.put("actualRoute", actual).put("actualTools", JSONArray(tools))
                                .put("semanticStatus", if (actual == case.route && (actual != "FAST_TOOL" || tools == case.tools)) "PASS" else "FAIL")
                            }
                        } catch (cancelled: CancellationException) {
                            runCatching { conversation.cancelProcess() }
                            event.put("status", "BLOCKED").put("semanticStatus", "NOT_EVALUATED").put("error", cancelled.toString())
                            throw cancelled
                        } catch (error: Exception) {
                            // До decode нет ответа модели, поэтому нехватку контекста
                            // нельзя засчитать как ошибку смысла или JSON-контракта.
                            if (error.message?.contains("Input token ids are too long") == true) {
                                event.put("status", "BLOCKED").put("semanticStatus", "NOT_EVALUATED")
                                    .put("structuredValidity", "NOT_EVALUATED").put("failureStage", "PREFILL_CAPACITY")
                            } else if (failureStage != "VALIDATION") {
                                event.put("status", "BLOCKED").put("semanticStatus", "NOT_EVALUATED")
                                    .put("structuredValidity", "NOT_EVALUATED").put("failureStage", failureStage)
                                event.put("error", error.toString())
                                throw error
                            } else if (evaluatedArguments) {
                                if (!event.has("semanticStatus")) event.put("semanticStatus", "NOT_EVALUATED")
                                if (!event.has("structuredValidity")) event.put("structuredValidity", "FAIL")
                                else event.put("argumentSchemaStatus", "FAIL")
                                event.put("status", "FAIL")
                            } else event.put("semanticStatus", "FAIL")
                            event.put("error", error.toString())
                        } finally {
                            writer.append(event.put("wallLatencyMs", SystemClock.elapsedRealtime() - started).put("processPssKb", Debug.getPss()))
                            completed++
                        }
                    }
                }
                complete = true
            }
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = previousConstrained
            ExperimentalFlags.enableBenchmark = previousBenchmark
            writer.append(JSONObject().put("type", "suite_finished").put("complete", complete)
                .put("completed", completed).put("expected", cases.size * variants.size))
        }
    }

    // Префикс — только синтаксис протокола. Значения маршрута/аргументов не задаются.
    // Флаг template восстанавливается сразу; параллельных Conversations в этом тесте нет.
    private fun identityConversation(engine: Engine, config: ConversationConfig): Conversation {
        val previous = ExperimentalFlags.overwritePromptTemplate
        ExperimentalFlags.overwritePromptTemplate = "{{ messages[-1].content }}"
        return try {
            engine.createConversation(config.copy(systemInstruction = null, tools = emptyList(), channels = emptyList()))
        } finally { ExperimentalFlags.overwritePromptTemplate = previous }
    }
}
