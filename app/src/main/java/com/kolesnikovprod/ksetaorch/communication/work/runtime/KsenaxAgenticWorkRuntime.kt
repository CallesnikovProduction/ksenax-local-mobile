package com.kolesnikovprod.ksetaorch.communication.work.runtime

import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.*
import com.kolesnikovprod.ksetaorch.communication.tools.driver.*
import com.kolesnikovprod.ksetaorch.communication.tools.policy.KsenaxPolicyContext
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.*
import com.kolesnikovprod.ksetaorch.communication.work.planning.*
import com.kolesnikovprod.ksetaorch.communication.work.routing.*
import com.kolesnikovprod.ksetaorch.communication.work.turn.*
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/**
 * FG предлагает маршрут; Kotlin проверяет данные, policy и последовательное исполнение.
 * Один запрос не разделяет Conversation с другим. G4 загружается только для плана.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class KsenaxAgenticWorkRuntime(
    private val plannerSession: KsenaxModelSession,
    private val actionSession: KsenaxModelSession,
    private val actionKits: List<KsenaxOneShotActionKit>,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val onDiagnostic: (KsenaxWorkDiagnostic) -> Unit = {},
) : KsenaxAgentTurnRuntime {
    private val turnMutex = Mutex()
    private val allActionNames = actionKits.flatMap { it.actionSpecs }.map { it.name }.toSet()
    private val routingProtocol = FunctionGemmaRoutingProtocol(actionKits.filter { it.supportsFastPath }.flatMap { it.protocol.declarations })
    private val planningPromptFactory = G4PlanningPromptFactory(actionKits.flatMap { it.actionSpecs })

    init {
        require(actionKits.isNotEmpty())
        require(actionKits.map { it.id }.distinct().size == actionKits.size)
        require(allActionNames.size == actionKits.sumOf { it.actionSpecs.size })
        require(actionKits.all { kit -> kit.protocol.declarations.map { it.codeName }.toSet() == kit.actionSpecs.map { it.name }.toSet() })
    }

    override suspend fun prepare() = actionSession.initializeEngine()

    override suspend fun handleUserText(
        text: String,
        policyContext: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult = turnMutex.withLock {
        val userText = text.trim()
        if (userText.isEmpty()) return@withLock KsenaxAgentTurnResult.Clarification("Сформулируй действие.")
        val started = System.nanoTime()
        // «Завтра» относится к моменту запроса, даже если inference пересёк полночь.
        val requestTime = now()
        try {
            onStage(KsenaxAgentTurnStage.RequestReceived)
            onStage(KsenaxAgentTurnStage.Routing)
            diagnostic("routing_started")
            val response = actionSession.askFunctions(routingProtocol.buildRequest(userText, requestTime))
            val route = routingProtocol.parseResponse(response)
            if (userText.toByteArray(Charsets.UTF_8).size > FunctionGemmaRoutingProtocol.MAX_ROUTING_INPUT_BYTES) {
                require(route == RequestRoute.PlannedWork) { "Long input must be processed by the planner." }
            }
            diagnostic("routing", (route as? RequestRoute.FastTool)?.call?.name, response.latencyMs, route.javaClass.simpleName)
            when (route) {
                is RequestRoute.FastTool -> {
                    if (FastRequestConstraints.isUnsafeStatement(userText)) {
                        KsenaxAgentTurnResult.Clarification("Нужно выполнить действие? Уточни команду.")
                    } else if (FastRequestConstraints.requiresPlanning(userText)) {
                        diagnostic("escalation", category = "compound_or_generative")
                        executePlannedRequest(userText, requestTime, policyContext, onStage)
                    } else executeDirect(userText, route.call, requestTime, policyContext, onStage)
                }
                RequestRoute.PlannedWork -> executePlannedRequest(userText, requestTime, policyContext, onStage)
                is RequestRoute.Clarification -> KsenaxAgentTurnResult.Clarification(route.question)
                is RequestRoute.Unsupported -> KsenaxAgentTurnResult.Refusal(route.reason)
            }
        } catch (cancellation: CancellationException) {
            diagnostic("cancelled")
            throw cancellation
        } catch (missing: MissingActionArgument) {
            diagnostic("clarification", category = "missing_argument")
            KsenaxAgentTurnResult.Clarification(missing.message ?: "Уточни параметры действия.")
        } catch (error: Exception) {
            diagnostic("failure", category = error.javaClass.simpleName)
            KsenaxAgentTurnResult.ModelFailure("Не удалось обработать запрос: ${error.message ?: error.javaClass.simpleName}", null)
        } finally {
            diagnostic("turn_finished", latencyMs = (System.nanoTime() - started) / 1_000_000)
        }
    }

    private suspend fun executeDirect(
        userText: String,
        proposal: KsenaxModelFunctionCall,
        requestTime: ZonedDateTime,
        policy: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
        refinementAllowed: Boolean = true,
    ): KsenaxAgentTurnResult {
        val kit = actionKits.single { it.supportsFastPath && it.supportsAction(proposal.name) }
        kit.validateSourceRequest(userText)
        val draft = kit.buildFastActionDraft(userText, proposal.name, requestTime)
        if (draft?.expectedActionName != null && draft.expectedActionName != proposal.name) {
            if (refinementAllowed) {
                // После FG-выбора kit даём модели один меньший каталог этого же kit.
                // Ожидаемое локальное имя не подставляется вместо модельного ответа.
                val response = actionSession.askFunctions(routingProtocol.buildRefinementRequest(userText,
                    kit.actionSpecs.map { it.name }.toSet()))
                diagnostic("routing_refinement", proposal.name, response.latencyMs)
                val refined = routingProtocol.parseResponse(response) as? RequestRoute.FastTool
                if (refined != null && kit.supportsAction(refined.call.name)) {
                    return executeDirect(userText, refined.call, requestTime, policy, onStage, refinementAllowed = false)
                }
            }
            diagnostic("clarification", proposal.name, category = "source_action_mismatch")
            return KsenaxAgentTurnResult.Clarification("Уточни время и способ постановки действия: ответ модели противоречит запросу.")
        }
        onStage(KsenaxAgentTurnStage.CompilingAction)
        val declaration = kit.protocol.declarations.single { it.codeName == proposal.name }
        val argumentProposal = if (declaration.parameters == null) proposal else {
            val response = actionSession.askFunctions(kit.protocol.buildFunctionRequest(proposal.name,
                KsenaxOneShotPromptInput(userMessage = userText, stepInstruction = userText, inputJson = draft?.argumentsJson)))
            require(response.calls.size == 1 && response.calls.single().name == proposal.name) { "FunctionGemma changed the selected action." }
            diagnostic("arguments_parsed", proposal.name, response.latencyMs)
            response.calls.single()
        }
        // Проверяем сырой ответ до нормализации: она не должна скрывать неверный тип.
        val compiled = kit.protocol.parseFunctionCall(argumentProposal)
        val compiledArgs = Json.parseToJsonElement(compiled.arguments.JSONtoString()).jsonObject
        val draftArgs = draft?.argumentsJson?.let { Json.parseToJsonElement(it).jsonObject }
        val input = draftArgs?.let { source ->
            // Единый локальный datetime заменяет альтернативные способы задания start.
            val retained = if ("start_local_date_time" in source) compiledArgs.filterKeys { !it.startsWith("start_") } else compiledArgs
            JsonObject(retained + source).toString()
        } ?: compiled.arguments.JSONtoString()
        val call = kit.resolveExecutableCall(userText,
            KsenaxWorkPlanStep("direct_1", compiled.name, userText, input), compiled).copy(id = "direct_1")
        kit.validateSourceCall(userText, call.name)
        validate(kit, call)
        diagnostic("fast_selected", call.name)
        return executeCalls(listOf(kit to call), null, policy, onStage)
    }

    private suspend fun executePlannedRequest(
        text: String,
        requestTime: ZonedDateTime,
        policy: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult {
        // Проверка допуска не выбирает kit: запрещаем дорогую генерацию плана,
        // если исходный текст не разрешает ни одного зарегистрированного действия.
        val sourcePermitted = actionKits.filter { kit ->
            try { kit.validateSourceRequest(text); true }
            catch (_: MissingActionArgument) { false }
        }
        if (sourcePermitted.isEmpty()) {
            diagnostic("clarification", category = "no_source_permission")
            return KsenaxAgentTurnResult.Clarification("Уточни команду для доступного инструмента.")
        }
        // Неполная поздняя команда останавливает весь запрос до G4, а не только
        // своё исполнение. Эти ограничения не выбирают и не создают шаги плана.
        sourcePermitted.forEach { it.validatePlanningSource(text, requestTime) }
        onStage(KsenaxAgentTurnStage.Planning)
        diagnostic("planning_started")
        plannerSession.initializeEngine()
        val response = plannerSession.askStateless(planningPromptFactory.buildPlanningRequest(text, requestTime.toString()))
        diagnostic("planning_completed", latencyMs = response.latencyMs)
        val parsed = G4PlanningResponseParser.parse(response.text, allActionNames)
        val plan = when (parsed) {
            is PlanningParseResult.Success -> parsed.plan
            is PlanningParseResult.Failure -> return KsenaxAgentTurnResult.ModelFailure(parsed.reason, null)
        }
        return when (plan) {
            is KsenaxWorkPlan.Clarification -> KsenaxAgentTurnResult.Clarification(plan.question, plan)
            is KsenaxWorkPlan.Refusal -> KsenaxAgentTurnResult.Refusal(plan.reason, plan)
            is KsenaxWorkPlan.ActionPlan -> executePlan(text, plan, requestTime, policy, onStage)
        }
    }

    private suspend fun executePlan(
        text: String,
        plan: KsenaxWorkPlan.ActionPlan,
        requestTime: ZonedDateTime,
        policy: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult {
        // Предварительная проверка ВСЕХ входов не допускает частичного исполнения
        // плана, в котором более поздний шаг уже содержит неверные аргументы.
        val prepared = plan.steps.map { step ->
            val kit = actionKits.single { it.supportsAction(step.actionName) }
            kit.validateSourceCall(text, step.actionName)
            val proposal = KsenaxToolCall(step.id, step.actionName, KsenaxRawToolArgumentsObject("{}"), kit.protocol.requiresConfirmation, kit.protocol.riskLevel)
            val call = kit.resolveExecutableCall(text, step, proposal).copy(id = step.id)
            validate(kit, call)
            Triple(kit, step, call)
        }
        // Сверяем набор целиком: отдельная проверка шага не замечает дубликат
        // первого будильника вместо второй исходной команды.
        actionKits.forEach { kit ->
            kit.validatePlannedInputs(text, requestTime, prepared.filter { it.first === kit }.map { it.third })
        }
        val calls = mutableListOf<KsenaxToolCall>()
        val executed = mutableListOf<KsenaxToolResult>()
        val denied = mutableListOf<KsenaxDeniedToolCall>()
        val pending = mutableListOf<KsenaxPendingToolCall>()
        for ((kit, step, call) in prepared) {
            onStage(KsenaxAgentTurnStage.CompilingAction)
            val response = try {
                actionSession.askFunctions(kit.protocol.buildFunctionRequest(step.actionName,
                    KsenaxOneShotPromptInput(userMessage = text, stepInstruction = step.instruction,
                        inputJson = call.arguments.JSONtoString().takeIf { kit.exposePlannerInputToFunctionGemma })))
            } catch (cancellation: CancellationException) { throw cancellation }
            catch (error: Exception) {
                denied += KsenaxDeniedToolCall(call, error.message ?: "Ошибка FunctionGemma.", "FG_INFERENCE_FAILED")
                return executionResult(emptyList(), executed, denied, pending, plan)
            }
            diagnostic("action_compiled", step.actionName, response.latencyMs)
            if (response.calls.size != 1 || response.calls.single().name != step.actionName) {
                denied += KsenaxDeniedToolCall(call, "FunctionGemma вернула не тот атомарный вызов.", "FG_ACTION_MISMATCH")
                return executionResult(emptyList(), executed, denied, pending, plan)
            }
            // Ответ FG валидируется даже если данные executor получает напрямую из G4.
            try { kit.protocol.parseFunctionCall(response.calls.single()) }
            catch (error: IllegalArgumentException) {
                denied += KsenaxDeniedToolCall(call, error.message ?: "Неверные аргументы FG.", "FG_INVALID_ARGUMENTS")
                return executionResult(emptyList(), executed, denied, pending, plan)
            }
        }
        for ((kit, _, call) in prepared) {
            // Только после проверки всех модельных предложений разрешены side effects.
            try { validate(kit, call) }
            catch (error: IllegalArgumentException) {
                // Время могло истечь после preflight. Ранее выполненные шаги
                // остаются в результате, но этот и следующие не исполняются.
                denied += KsenaxDeniedToolCall(call, error.message ?: "Параметры действия больше недействительны.", "INVALID_ARGUMENTS")
                break
            }
            calls += call
            onStage(KsenaxAgentTurnStage.ExecutingTools(listOf(call)))
            executeSingleCallInto(kit, call, policy, executed, denied, pending)
            if (executed.lastOrNull() is KsenaxToolResult.Failure || denied.isNotEmpty() || pending.isNotEmpty()) break
        }
        return executionResult(calls, executed, denied, pending, plan)
    }

    private fun validate(kit: KsenaxOneShotActionKit, call: KsenaxToolCall) {
        require(kit.supportsAction(call.name))
        // Notes имеют пустую FG-схему, но отдельный обязательный payload G4.
        if (kit.exposePlannerInputToFunctionGemma) {
            val declaration = kit.protocol.declarations.single { it.codeName == call.name }
            ActionArgumentsValidator.validate(declaration, call.arguments.JSONtoString())
        }
        kit.validateExecutableCall(call, now())
        diagnostic("validation_passed", call.name)
    }

    private suspend fun executeCalls(
        calls: List<Pair<KsenaxOneShotActionKit, KsenaxToolCall>>,
        plan: KsenaxWorkPlan.ActionPlan?,
        policy: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult.ToolExecution {
        val executed = mutableListOf<KsenaxToolResult>()
        val denied = mutableListOf<KsenaxDeniedToolCall>()
        val pending = mutableListOf<KsenaxPendingToolCall>()
        for ((kit, call) in calls) {
            onStage(KsenaxAgentTurnStage.ExecutingTools(listOf(call)))
            executeSingleCallInto(kit, call, policy, executed, denied, pending)
        }
        return executionResult(calls.map { it.second }, executed, denied, pending, plan)
    }

    private suspend fun executeSingleCallInto(
        kit: KsenaxOneShotActionKit, call: KsenaxToolCall, policy: KsenaxPolicyContext,
        executed: MutableList<KsenaxToolResult>, denied: MutableList<KsenaxDeniedToolCall>, pending: MutableList<KsenaxPendingToolCall>,
    ) {
        when {
            kit.namespace in policy.blockedNamespaces || call.name in policy.blockedToolNames -> {
                denied += KsenaxDeniedToolCall(call, "Действие заблокировано политикой.", "BLOCKED_TOOL")
                diagnostic("policy_denied", call.name)
            }
            call.requiresConfirmation && !policy.userConfirmedRequiredActions -> {
                pending += KsenaxPendingToolCall(call, "Действие требует подтверждения.")
                diagnostic("confirmation_required", call.name)
            }
            else -> {
                val result = try { kit.executor.execute(call) }
                catch (cancellation: CancellationException) { throw cancellation }
                catch (error: Exception) { KsenaxToolResult.Failure(call.id, call.name, error.message ?: "Ошибка исполнения.", "EXECUTOR_FAILED") }
                executed += result
                diagnostic("execution", call.name, category = if (result is KsenaxToolResult.Success) "success" else "failure")
            }
        }
    }

    private fun executionResult(calls: List<KsenaxToolCall>, executed: List<KsenaxToolResult>, denied: List<KsenaxDeniedToolCall>, pending: List<KsenaxPendingToolCall>, plan: KsenaxWorkPlan.ActionPlan?) =
        KsenaxAgentTurnResult.ToolExecution(calls, KsenaxToolOrchestrationResult(executed, denied, pending), plan)

    private fun diagnostic(stage: String, action: String? = null, latencyMs: Long? = null, category: String? = null) {
        onDiagnostic(KsenaxWorkDiagnostic(stage, action, latencyMs, category))
    }
}
