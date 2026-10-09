package com.kolesnikovprod.ksetaorch.live

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Проверяет базовый вывод кандидата без инструментов и сложного контракта.
 * Ожидаемое слово используется только после inference; Android-действий нет.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalApi::class)
class LiveQwenBasicDiagnosticsTest {
    @Test fun compareLanguagesAndSampling() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val candidate = LiveCandidateModel.resolve(context, args)
        val backend = args.getString("backend") ?: "CPU"
        val thinkingMode = args.getString("thinkingMode") ?: "metadata"
        require(thinkingMode in setOf("metadata", "template_non_thinking"))
        val writer = LiveArtifactWriter(File(context.getExternalFilesDir(null), "live-routing"), requireNotNull(args.getString("runId")))
        val previousConstrained = ExperimentalFlags.enableConversationConstrainedDecoding
        val previousBenchmark = ExperimentalFlags.enableBenchmark
        var completed = 0
        try {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
            ExperimentalFlags.enableBenchmark = true
            writer.append(JSONObject().put("type", "environment").put("modelSha256", candidate.sha256)
                .put("sdk", "0.13.1").put("backend", backend).put("contextTokens", 1024)
                .put("thinkingMode", thinkingMode).put("extraContextThinking", false)
                .put("executorsInvoked", false).put("gemma4Invoked", false).put("expectedResults", 4))
            val engine = Engine(candidate.engineConfig(context, backend = backend))
            try {
                engine.initialize()
            } catch (error: Exception) {
                writer.append(JSONObject().put("type", "engine_error").put("status", "BLOCKED")
                    .put("failureStage", "ENGINE_INITIALIZATION").put("error", error.toString()))
                throw error
            }
            // SDK запрещает close() до успешного initialize().
            engine.use {
                val samples = listOf("greedy" to SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    "qwen_non_thinking" to SamplerConfig(topK = 20, topP = 0.8, temperature = 0.7))
                val controls = listOf(Triple("english", "Name the capital of France. Answer with one word.", "paris"),
                    Triple("russian", "Назови столицу Франции. Ответь одним словом.", "париж"))
                for ((sampling, sampler) in samples) for ((id, prompt, expected) in controls) {
                    val config = ConversationConfig(systemInstruction = Contents.of("You are a helpful assistant."),
                        tools = emptyList(), automaticToolCalling = false, samplerConfig = sampler,
                        extraContext = mapOf("enable_thinking" to false))
                    if (thinkingMode == "metadata") engine.createConversation(config).use { renderer ->
                        writer.append(JSONObject().put("type", "rendered_prompt").put("id", id).put("sampling", sampling)
                            .put("text", renderer.renderMessageIntoString(Message.user(prompt))))
                    }
                    val prepared = if (thinkingMode == "template_non_thinking") LiveQwenConversation.prepare(engine, config, prompt) else null
                    (prepared?.conversation ?: engine.createConversation(config)).use { conversation ->
                        if (prepared != null) writer.append(JSONObject().put("type", "rendered_prompt").put("id", id).put("sampling", sampling)
                            .put("text", prepared.prompt))
                        val started = SystemClock.elapsedRealtime()
                        val text = StringBuilder()
                        try {
                            withTimeout(60_000L) {
                                conversation.sendMessageAsync(prepared?.prompt ?: prompt).collect { message ->
                                    check(message.toolCalls.isEmpty())
                                    text.append(message.toString())
                                }
                            }
                            val benchmark = conversation.getBenchmarkInfo()
                            val actual = text.toString().trim().trimEnd('.', '!').lowercase()
                            writer.append(JSONObject().put("type", "basic_result").put("id", id).put("sampling", sampling)
                                .put("input", prompt).put("text", text.toString()).put("expected", expected)
                                .put("status", if (actual == expected) "PASS" else "FAIL")
                                .put("prefillTokens", benchmark.lastPrefillTokenCount).put("decodeTokens", benchmark.lastDecodeTokenCount)
                                .put("wallLatencyMs", SystemClock.elapsedRealtime() - started))
                            completed++
                        } catch (error: Exception) {
                            runCatching { conversation.cancelProcess() }
                            writer.append(JSONObject().put("type", "basic_result").put("id", id).put("sampling", sampling)
                                .put("text", text.toString()).put("status", "BLOCKED").put("error", error.toString()))
                            throw error
                        }
                    }
                }
            }
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = previousConstrained
            ExperimentalFlags.enableBenchmark = previousBenchmark
            writer.append(JSONObject().put("type", "suite_finished").put("complete", completed == 4).put("completed", completed))
        }
    }
}
