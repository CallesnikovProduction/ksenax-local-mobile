package com.kolesnikovprod.ksetaorch.communication.work.runtime

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelResponse
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.driver.KsenaxDeniedToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.driver.KsenaxPendingToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.driver.KsenaxToolOrchestrationResult
import com.kolesnikovprod.ksetaorch.communication.tools.policy.KsenaxPolicyContext
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionPlanningMode
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotPromptInput
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxG4PlanningPromptFactory
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxG4PlanningResponseParser
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxPlanningParseResult
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlan
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnResult
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnRuntime
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Новый agentic runtime: G4 планирует, FunctionGemma компилирует один action.
 *
 * Поток:
 * 1. G4 получает короткий action catalog и возвращает compact JSON plan.
 * 2. Каждый plan step выбирает один FG action kit.
 * 3. FG получает только declarations выбранного kit-а и input текущего step-а.
 * 4. Android executor выполняет распарсенный [KsenaxToolCall].
 *
 * Ни один шаг не использует persistent chat. Agentic-режим всегда работает
 * fresh-turn моделью.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxAgenticWorkRuntime(
    private val plannerSession: KsenaxModelSession,
    private val actionSession: KsenaxModelSession,
    private val actionKits: List<KsenaxOneShotActionKit>,
) : KsenaxAgentTurnRuntime {

    private val planningPromptFactory = KsenaxG4PlanningPromptFactory(
        actionSpecs = actionKits
            .filter { kit -> kit.planningMode == KsenaxActionPlanningMode.Planable }
            .flatMap(KsenaxOneShotActionKit::actionSpecs),
    )

    init {
        require(actionKits.isNotEmpty()) {
            "Agentic work runtime requires at least one FG action kit."
        }
        require(actionKits.map(KsenaxOneShotActionKit::id).toSet().size == actionKits.size) {
            "FG action kit ids must be unique."
        }
    }

    override suspend fun prepare() {
        // Для быстрых NonPlanable actions не поднимаем G4 на этапе
        // MODEL VERIFICATION / PreparingModel. G4 стартует лениво только когда
        // действительно нужен planning.
        actionSession.initializeEngine()
    }

    override suspend fun handleUserText(
        text: String,
        policyContext: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult {
        if (text.isBlank()) {
            return KsenaxAgentTurnResult.Clarification(
                question = "Сформулируй действие для agentic-режима.",
            )
        }

        onStage(KsenaxAgentTurnStage.RequestReceived)

        findDirectKit(text)?.let { kit ->
            return executeDirect(
                userText = text,
                kit = kit,
                policyContext = policyContext,
                onStage = onStage,
            )
        }

        onStage(KsenaxAgentTurnStage.Planning)

        val plan = when (val planning = planUserRequest(text)) {
            is PlanningOutcome.Failure -> {
                return KsenaxAgentTurnResult.ModelFailure(
                    reason = planning.reason,
                    rawText = planning.rawText,
                )
            }
            is PlanningOutcome.Success -> planning.plan
        }

        return when (plan) {
            is KsenaxWorkPlan.ActionPlan ->
                executePlan(
                    userText = text,
                    plan = plan,
                    policyContext = policyContext,
                    onStage = onStage,
                )
            is KsenaxWorkPlan.Clarification ->
                KsenaxAgentTurnResult.Clarification(
                    question = plan.question,
                    plan = plan,
                )
            is KsenaxWorkPlan.Refusal ->
                KsenaxAgentTurnResult.Refusal(
                    message = plan.reason,
                    plan = plan,
                )
            is KsenaxWorkPlan.AssistantMessage ->
                KsenaxAgentTurnResult.AssistantMessage(
                    message = plan.message,
                    plan = plan,
                )
        }
    }

    private suspend fun planUserRequest(userText: String): PlanningOutcome {
        val request = planningPromptFactory.buildPlanningRequest(userText = userText)
        val response = try {
            plannerSession.initializeEngine()
            plannerSession.askStateless(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return PlanningOutcome.Failure(
                reason = error.message ?: "G4 planning inference failed.",
                rawText = null,
            )
        }

        return when (val parseResult = KsenaxG4PlanningResponseParser.parse(response.text)) {
            is KsenaxPlanningParseResult.Success -> PlanningOutcome.Success(parseResult.plan)
            is KsenaxPlanningParseResult.Failure -> PlanningOutcome.Failure(
                reason = parseResult.reason,
                rawText = parseResult.rawText,
            )
        }
    }

    private fun findDirectKit(userText: String): KsenaxOneShotActionKit? =
        actionKits.firstOrNull { kit ->
            kit.planningMode == KsenaxActionPlanningMode.NonPlanable &&
                kit.supports(userText)
        }

    private suspend fun executeDirect(
        userText: String,
        kit: KsenaxOneShotActionKit,
        policyContext: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult.ToolExecution {
        onStage(KsenaxAgentTurnStage.CompilingAction)
        val directDraft = kit.buildDirectActionDraft(userText)

        val response = try {
            askActionModel(
                kit.protocol.buildOneShotPrompt(
                    KsenaxOneShotPromptInput(
                        userMessage = userText,
                        stepInstruction = directDraft?.instruction ?: userText,
                        preferredActionName = directDraft?.preferredActionName,
                        plannerInputJson = directDraft?.plannerInputJson,
                    )
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val fallbackCall = KsenaxToolCall(
                id = "direct_call",
                name = kit.id,
                arguments = { "{}" },
                requiresConfirmation = false,
                riskLevel = com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel.LOW,
            )
            return KsenaxAgentTurnResult.ToolExecution(
                toolCalls = listOf(fallbackCall),
                toolResult = KsenaxToolOrchestrationResult(
                    executed = emptyList(),
                    denied = listOf(
                        KsenaxDeniedToolCall(
                            call = fallbackCall,
                            reason = error.message ?: "FunctionGemma direct action inference failed.",
                            code = "FG_DIRECT_INFERENCE_FAILED",
                        )
                    ),
                    pendingConfirmation = emptyList(),
                ),
            )
        }

        val call = try {
            kit.protocol.parseOneShotResponse(response.text)
        } catch (error: IllegalArgumentException) {
            val fallbackCall = KsenaxToolCall(
                id = "direct_call",
                name = kit.id,
                arguments = { "{}" },
                requiresConfirmation = false,
                riskLevel = com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel.LOW,
            )
            return KsenaxAgentTurnResult.ToolExecution(
                toolCalls = listOf(fallbackCall),
                toolResult = KsenaxToolOrchestrationResult(
                    executed = emptyList(),
                    denied = listOf(
                        KsenaxDeniedToolCall(
                            call = fallbackCall,
                            reason = error.message ?: "FunctionGemma returned invalid direct action call.",
                            code = "FG_DIRECT_CALL_PARSE_FAILED",
                        )
                    ),
                    pendingConfirmation = emptyList(),
                ),
            )
        }

        val executableCall = kit.resolveExecutableCall(
            userMessage = userText,
            step = KsenaxWorkPlanStep(
                id = call.id,
                actionName = directDraft?.preferredActionName ?: call.name,
                instruction = directDraft?.instruction ?: userText,
                plannerInputJson = directDraft?.plannerInputJson,
            ),
            compiledCall = call,
        )
        onStage(KsenaxAgentTurnStage.ExecutingTools(calls = listOf(executableCall)))
        return executeSingleCall(
            kit = kit,
            call = executableCall,
            policyContext = policyContext,
            plan = null,
            calls = mutableListOf(executableCall),
            executed = mutableListOf(),
            denied = mutableListOf(),
            pending = mutableListOf(),
        )
    }

    private suspend fun executePlan(
        userText: String,
        plan: KsenaxWorkPlan.ActionPlan,
        policyContext: KsenaxPolicyContext,
        onStage: suspend (KsenaxAgentTurnStage) -> Unit,
    ): KsenaxAgentTurnResult.ToolExecution {
        val calls = mutableListOf<KsenaxToolCall>()
        val executed = mutableListOf<KsenaxToolResult>()
        val denied = mutableListOf<KsenaxDeniedToolCall>()
        val pending = mutableListOf<KsenaxPendingToolCall>()

        plan.steps.forEach { step ->
            val kit = actionKits.firstOrNull { kit -> kit.supportsAction(step.actionName) }
            if (kit == null) {
                denied += KsenaxDeniedToolCall(
                    call = step.toMissingKitCall(),
                    reason = "FG action kit was not found for `${step.actionName}`.",
                    code = "MISSING_ACTION_KIT",
                )
                return@forEach
            }

            onStage(KsenaxAgentTurnStage.CompilingAction)
            val plannerInputJson = step.plannerInputJson
                ?: kit.buildFallbackPlannerInputJson(
                    userMessage = userText,
                    step = step,
                )
            val effectiveStep = step.copy(plannerInputJson = plannerInputJson)
            val prompt = kit.protocol.buildOneShotPrompt(
                KsenaxOneShotPromptInput(
                    userMessage = userText,
                    stepInstruction = effectiveStep.instruction,
                    preferredActionName = effectiveStep.actionName,
                    plannerInputJson = effectiveStep.plannerInputJson
                        .takeIf { kit.exposePlannerInputToFunctionGemma },
                    plannerComment = effectiveStep.comment ?: plan.plannerComment,
                )
            )

            val response = try {
                askActionModel(prompt)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                denied += KsenaxDeniedToolCall(
                    call = step.toMissingKitCall(),
                    reason = error.message ?: "FunctionGemma action inference failed.",
                    code = "FG_INFERENCE_FAILED",
                )
                return@forEach
            }

            val call = try {
                kit.protocol.parseOneShotResponse(response.text)
            } catch (error: IllegalArgumentException) {
                denied += KsenaxDeniedToolCall(
                    call = step.toMissingKitCall(),
                    reason = error.message ?: "FunctionGemma returned invalid action call.",
                    code = "FG_CALL_PARSE_FAILED",
                )
                return@forEach
            }
            val executableCall = kit.resolveExecutableCall(
                userMessage = userText,
                step = effectiveStep,
                compiledCall = call,
            )
            calls += executableCall
            onStage(KsenaxAgentTurnStage.ExecutingTools(calls = listOf(executableCall)))

            executeSingleCallInto(
                kit = kit,
                call = executableCall,
                policyContext = policyContext,
                executed = executed,
                denied = denied,
                pending = pending,
            )
            if (executed.lastOrNull() is KsenaxToolResult.Failure) {
                return@forEach
            }
        }

        return KsenaxAgentTurnResult.ToolExecution(
            toolCalls = calls,
            toolResult = KsenaxToolOrchestrationResult(
                executed = executed,
                denied = denied,
                pendingConfirmation = pending,
            ),
            plan = plan,
        )
    }

    private suspend fun executeSingleCall(
        kit: KsenaxOneShotActionKit,
        call: KsenaxToolCall,
        policyContext: KsenaxPolicyContext,
        plan: KsenaxWorkPlan.ActionPlan?,
        calls: MutableList<KsenaxToolCall>,
        executed: MutableList<KsenaxToolResult>,
        denied: MutableList<KsenaxDeniedToolCall>,
        pending: MutableList<KsenaxPendingToolCall>,
    ): KsenaxAgentTurnResult.ToolExecution {
        executeSingleCallInto(
            kit = kit,
            call = call,
            policyContext = policyContext,
            executed = executed,
            denied = denied,
            pending = pending,
        )
        return KsenaxAgentTurnResult.ToolExecution(
            toolCalls = calls,
            toolResult = KsenaxToolOrchestrationResult(
                executed = executed,
                denied = denied,
                pendingConfirmation = pending,
            ),
            plan = plan,
        )
    }

    private suspend fun executeSingleCallInto(
        kit: KsenaxOneShotActionKit,
        call: KsenaxToolCall,
        policyContext: KsenaxPolicyContext,
        executed: MutableList<KsenaxToolResult>,
        denied: MutableList<KsenaxDeniedToolCall>,
        pending: MutableList<KsenaxPendingToolCall>,
    ) {
        when {
            kit.namespace in policyContext.blockedNamespaces ||
                call.name in policyContext.blockedToolNames -> {
                denied += KsenaxDeniedToolCall(
                    call = call,
                    reason = "Action is blocked by runtime policy context.",
                    code = "BLOCKED_TOOL",
                )
            }
            call.requiresConfirmation && !policyContext.userConfirmedRequiredActions -> {
                pending += KsenaxPendingToolCall(
                    call = call,
                    reason = "Action requires user confirmation.",
                )
            }
            else -> executed += kit.executor.execute(call)
        }
    }

    private suspend fun askActionModel(prompt: String): KsenaxModelResponse {
        var completedResponse: KsenaxModelResponse? = null
        actionSession.streamEphemeral(prompt).collect { event ->
            if (event is KsenaxModelStreamEvent.Completed) {
                completedResponse = event.response
            }
        }
        return requireNotNull(completedResponse) {
            "FunctionGemma stream completed without final response."
        }
    }

    private fun KsenaxWorkPlanStep.toMissingKitCall(): KsenaxToolCall =
        KsenaxToolCall(
            id = id,
            name = actionName,
            arguments = { plannerInputJson ?: "{}" },
            requiresConfirmation = false,
            riskLevel = com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel.LOW,
        )

    private sealed interface PlanningOutcome {
        data class Success(val plan: KsenaxWorkPlan) : PlanningOutcome
        data class Failure(val reason: String, val rawText: String?) : PlanningOutcome
    }
}
