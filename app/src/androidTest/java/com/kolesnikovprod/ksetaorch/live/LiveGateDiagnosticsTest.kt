package com.kolesnikovprod.ksetaorch.live

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionDeclaration
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionRequest
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchOneShotProtocol
import com.kolesnikovprod.ksetaorch.communication.work.routing.FunctionGemmaRoutingProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime

/** Проверяет выбор семейства и затем функции двумя реальными FG-запросами.
 * Не вызывает G4, source-policy или executors; результат не доказывает работу инструмента.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@RunWith(AndroidJUnit4::class)
class LiveGateDiagnosticsTest {
    @Test fun compareModelSelectedKit() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KsenaxAndroidApplication
        val selected = requireNotNull(args.getString("cases")).split(",").toSet()
        val cases = LiveRoutingCorpus.cases.filter { it.id in selected }
        check(cases.map { it.id }.toSet() == selected)
        val variants = args.getString("variants")?.split(",") ?: listOf("production_catalog", "model_kit_gate")
        require(variants.isNotEmpty() && variants.distinct().size == variants.size &&
            variants.all { it in setOf("production_catalog", "model_kit_gate", "torch_catalog", "torch_precise", "torch_state", "torch_state_ru") }) { "Unknown or repeated diagnostic variant" }
        check(cases.all { it.input.toByteArray(Charsets.UTF_8).size <= FunctionGemmaRoutingProtocol.MAX_ROUTING_INPUT_BYTES }) {
            "This diagnostic compares semantic choices, not forced budget routing."
        }
        val kits = linkedMapOf("flashlight" to TorchOneShotProtocol.declarations,
            "alarm" to AlarmOneShotProtocol.declarations, "calendar" to CalendarEventOneShotProtocol.declarations)
        val catalog = FunctionGemmaRoutingProtocol(kits.values.flatten())
        val torchCatalog = FunctionGemmaRoutingProtocol(TorchOneShotProtocol.declarations)
        // Сужение задано экспериментом, не ожидаемым ответом конкретного запроса.
        // Модель по-прежнему выбирает направление, планирование или отказ.
        val preciseTorchFunctions = listOf(
            KsenaxModelFunctionDeclaration("torch_on", "Set the device flashlight ON. Choose for a request to illuminate, enable or light it. Never choose for switching it off."),
            KsenaxModelFunctionDeclaration("torch_off", "Set the device flashlight OFF. Choose for a request to extinguish, disable or turn it off. Never choose for switching it on."),
            KsenaxModelFunctionDeclaration("torch_toggle", "Invert the current flashlight state ONLY when no ON or OFF direction is requested. Do not use for an explicit direction."),
            KsenaxModelFunctionDeclaration("route_planned_work", "Generate or analyze a note or text, or perform several different actions."),
            KsenaxModelFunctionDeclaration("route_clarification", "Ask for details when the requested action is ambiguous."),
            KsenaxModelFunctionDeclaration("route_unsupported", "Reject a factual statement, an informational question, a negated command or an unsupported action."),
        )
        val preciseTorchInstruction = "You are a model that can do function calling with the following functions. " +
            "Select exactly one function for the user's actual intent. The user may write in Russian. " +
            "An explicit ON or OFF request must preserve that direction; toggle is not a substitute. " +
            "A negation or a question asking how something works does not authorize switching the flashlight. " +
            "Requests to create or analyze text need planning. Return only the function call with empty arguments."
        val stateTorchFunctions = listOf(
            KsenaxModelFunctionDeclaration("set_flashlight_state",
                "Change the device flashlight state: on to illuminate, off to extinguish, toggle to invert without an explicit direction.",
                """{"type":"object","properties":{"state":{"type":"string","enum":["on","off","toggle"],"description":"The requested resulting flashlight state."}},"required":["state"],"additionalProperties":false}"""),
        ) + preciseTorchFunctions.filter { it.name.startsWith("route_") }
        val russianStateFunctions = listOf(
            KsenaxModelFunctionDeclaration("set_flashlight_state",
                "Устанавливает состояние фонарика: on — включён и светит, off — выключен и не светит, toggle — инверсия без указанного направления.",
                """{"type":"object","properties":{"state":{"type":"string","enum":["on","off","toggle"],"description":"Запрошенное конечное состояние фонарика."}},"required":["state"],"additionalProperties":false}"""),
            KsenaxModelFunctionDeclaration("route_planned_work", "Создание или анализ текста, заметки; несколько разных действий."),
            KsenaxModelFunctionDeclaration("route_clarification", "Непонятная команда: нужно уточнить смысл."),
            KsenaxModelFunctionDeclaration("route_unsupported", "Вопрос о работе функции, факт, отрицание команды или неподдерживаемое действие. Ничего не исполнять."),
        )
        val russianStateInstruction = "You are a model that can do function calling with the following functions. " +
            "Выбери ровно одну функцию по смыслу запроса. Включение и выключение — разные состояния. " +
            "Отрицание не разрешает ни исходное, ни противоположное действие: выбери route_unsupported. " +
            "Информационный вопрос не является командой. Создание текста и несколько разных действий требуют route_planned_work."
        val gate = listOf(
            KsenaxModelFunctionDeclaration("execute_device_action",
                "One device command: switch flashlight, set one or several alarms with the same schedule, or create a calendar event. Choose its kit. No writing or analysis.",
                """{"type":"object","properties":{"kit":{"type":"string","enum":["flashlight","alarm","calendar"]}},"required":["kit"],"additionalProperties":false}"""),
            KsenaxModelFunctionDeclaration("generate_or_plan_work",
                "Write, generate or analyze a note or text; or perform several different actions."),
            KsenaxModelFunctionDeclaration("ask_for_missing_details",
                "A device command without required time or with unclear meaning."),
            KsenaxModelFunctionDeclaration("reject_non_command",
                "A question, a fact, a negation or an unsupported command. Do not perform a device action."),
        )
        val writer = LiveArtifactWriter(File(app.getExternalFilesDir(null), "live-routing"),
            requireNotNull(args.getString("runId")))
        writer.append(JSONObject().put("type", "environment").put("timestamp", ZonedDateTime.now().toString())
            .put("appLastUpdateTime", app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime)
            .put("backend", "CPU").put("executorsInvoked", false).put("gemma4Invoked", false)
            .put("evaluationVersion", 2).put("expectedResults", cases.size * variants.size)
            .put("variants", JSONArray(variants))
            .put("gateDeclarations", JSONArray(gate.map { JSONObject().put("name", it.name)
                .put("description", it.description).put("parametersJson", it.parametersJson ?: JSONObject.NULL) }))
            .put("selectedCases", JSONArray(cases.map { it.id })))
        var completed = 0
        var complete = false
        try {
            for ((index, case) in cases.withIndex()) {
                for (variant in if (index % 2 == 0) variants else variants.reversed()) {
                    val started = SystemClock.elapsedRealtime()
                    val event = JSONObject().put("type", "diagnostic_result").put("id", case.id)
                        .put("input", case.input).put("variant", variant).put("expectedRoute", case.route)
                        .put("expectedTools", JSONArray(case.tools))
                    val calls = JSONArray()
                    event.put("inferences", calls)
                    try {
                        suspend fun ask(request: KsenaxModelFunctionRequest): com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionResponse {
                            val inference = JSONObject().put("functions", JSONArray(request.functions.map { it.name }))
                                .put("systemInstruction", request.systemInstruction).put("userMessage", request.userMessage)
                                .put("declarations", JSONArray(request.functions.map { declaration ->
                                    JSONObject().put("name", declaration.name).put("description", declaration.description)
                                        .put("parametersJson", declaration.parametersJson ?: JSONObject.NULL)
                                }))
                                .put("status", "REQUESTED")
                            calls.put(inference)
                            val response = withTimeout(60_000L) { app.functionGemmaModelSession.askFunctions(request) }
                            inference.put("status", "COMPLETED")
                                .put("calls", JSONArray(response.calls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson) }))
                                .put("latencyMs", response.latencyMs)
                            return response
                        }
                        val rootRequest = when (variant) {
                            "production_catalog" -> catalog.buildRequest(case.input)
                            "torch_catalog" -> torchCatalog.buildRequest(case.input)
                            "torch_precise" -> KsenaxModelFunctionRequest(case.input, preciseTorchFunctions, preciseTorchInstruction)
                            "torch_state" -> KsenaxModelFunctionRequest(case.input, stateTorchFunctions)
                            "torch_state_ru" -> KsenaxModelFunctionRequest(case.input, russianStateFunctions, russianStateInstruction)
                            else -> KsenaxModelFunctionRequest(case.input, gate)
                        }
                        val response = ask(rootRequest)
                        require(response.calls.size == 1) { "Expected exactly one root call." }
                        val root = response.calls.single()
                        event.put("rootChoice", root.name)
                        require(root.name in rootRequest.functions.map { it.name }) { "Root call was not declared in this variant." }
                        val arguments = JSONObject(root.argumentsJson)
                        val actualTools: List<String>
                        val route: String
                        if (variant in setOf("torch_state", "torch_state_ru") && root.name == "set_flashlight_state") {
                            require(arguments.length() == 1 && arguments.has("state")) { "Expected only state." }
                            val state = arguments.get("state") as? String ?: error("State must be a string.")
                            // Это преобразование модельного enum, не интерпретация UP.
                            val action = when (state) {
                                "on" -> "torch_on"
                                "off" -> "torch_off"
                                "toggle" -> "torch_toggle"
                                else -> error("Unknown flashlight state.")
                            }
                            event.put("modelSelectedState", state)
                            route = "FAST_TOOL"
                            actualTools = listOf(action)
                        } else if (variant == "model_kit_gate" && root.name == "execute_device_action") {
                            require(arguments.length() == 1 && arguments.has("kit")) { "Expected only kit." }
                            val kit = arguments.get("kit") as? String ?: error("Kit must be a string.")
                            val declarations = requireNotNull(kits[kit]) { "Unknown model-selected kit." }
                            event.put("modelSelectedKit", kit)
                            // Только модельный kit сужает список. Ожидания корпуса не участвуют в запросе.
                            val atomic = ask(catalog.buildRefinementRequest(case.input, declarations.map { it.codeName }.toSet()))
                            require(atomic.calls.size == 1 && atomic.calls.single().name in declarations.map { it.codeName } &&
                                JSONObject(atomic.calls.single().argumentsJson).length() == 0) { "Invalid atomic selection." }
                            route = "FAST_TOOL"
                            actualTools = listOf(atomic.calls.single().name)
                        } else {
                            require(arguments.length() == 0) { "Unexpected root arguments." }
                            route = when (root.name) {
                                "generate_or_plan_work", "route_planned_work" -> "LLM_BOUND"
                                "ask_for_missing_details", "route_clarification" -> "NEEDS_CLARIFICATION"
                                "reject_non_command", "route_unsupported" -> "NO_ACTION"
                                in kits.values.flatten().map { it.codeName } -> "FAST_TOOL"
                                else -> error("Unknown root call.")
                            }
                            actualTools = if (route == "FAST_TOOL") listOf(root.name) else emptyList()
                        }
                        event.put("actualRoute", route).put("actualTools", JSONArray(actualTools))
                            .put("acceptableTerminalOutcome", case.route == "NO_ACTION" && route in setOf("NO_ACTION", "NEEDS_CLARIFICATION"))
                            .put("semanticStatus", if (route == case.route && (case.route != "FAST_TOOL" || actualTools == case.tools)) "PASS" else "FAIL")
                    } catch (timeout: TimeoutCancellationException) {
                        writer.append(event.put("semanticStatus", "NOT_EVALUATED").put("status", "BLOCKED")
                            .put("error", timeout.toString()).put("wallLatencyMs", SystemClock.elapsedRealtime() - started))
                        completed++
                        throw timeout // Не продолжаем серию после отмены native inference.
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { event.put("semanticStatus", "FAIL").put("error", error.toString()) }
                    writer.append(event.put("wallLatencyMs", SystemClock.elapsedRealtime() - started))
                    completed++
                }
            }
            complete = true
        } finally {
            writer.append(JSONObject().put("type", "suite_finished").put("complete", complete)
                .put("completed", completed).put("expected", cases.size * variants.size))
        }
    }
}
