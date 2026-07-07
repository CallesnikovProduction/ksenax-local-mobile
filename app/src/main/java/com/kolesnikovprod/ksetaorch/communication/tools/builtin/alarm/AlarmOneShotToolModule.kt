package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionPlanningMode
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionInputDraft
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep

class AlarmOneShotToolModule(
    override val executor: KsenaxToolExecutor,
) : KsenaxOneShotActionKit {

    override val id: String = "system.alarm.oneshot"

    override val namespace: String = "system"

    override val planningMode: KsenaxActionPlanningMode =
        KsenaxActionPlanningMode.NonPlanable

    override val actionSpecs: List<KsenaxWorkActionSpec> =
        listOf(
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AtTime.codeName,
                description = "Creates Android alarms at a local HH:mm clock time.",
                inputHint = """{"time":"HH:mm","count":optional integer,"label":optional string}""",
                examples = listOf("Поставь будильник на 19:00 -> alarm_at_time"),
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AfterHours.codeName,
                description = "Creates Android alarms after a number of hours. Do not convert hours to clock time.",
                inputHint = """{"hours":number,"count":optional integer,"label":optional string}""",
                examples = listOf("Поставь 3 будильника через 9 часов -> alarm_after_hours"),
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AfterMinutes.codeName,
                description = "Creates Android alarms after a number of minutes.",
                inputHint = """{"minutes":integer,"count":optional integer,"label":optional string}""",
                examples = listOf("Разбуди через 30 минут -> alarm_after_minutes"),
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.AtDateTime.codeName,
                description = "Creates Android alarms at exact local date-time.",
                inputHint = """{"date_time":"yyyy-MM-dd'T'HH:mm","count":optional integer,"label":optional string}""",
            ),
            KsenaxWorkActionSpec(
                name = AlarmToolOneShot.ClearAll.codeName,
                description = "Explains that deleting all alarms is not available to normal Android apps.",
                inputHint = "No input object is needed.",
                examples = listOf("Очисти будильники -> alarm_clear_all"),
            ),
        )

    override val keywords: KsenaxOneShotKeywords = AlarmOneShotKeywords

    override val protocol: KsenaxOneShotToolProtocol = AlarmOneShotProtocol

    override fun buildDirectActionDraft(userMessage: String): KsenaxActionInputDraft? =
        AlarmUserPromptDraft.build(userMessage)

    override fun preferredDirectActionName(userMessage: String): String? {
        val text = userMessage.lowercase()
        return when {
            ("очист" in text || "удал" in text) && "будильник" in text ->
                AlarmToolOneShot.ClearAll.codeName
            else -> null
        }
    }

    override fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall =
        step.plannerInputJson
            ?.takeIf(String::isNotBlank)
            ?.let { plannerInputJson ->
                compiledCall.copy(
                    name = step.actionName,
                    arguments = KsenaxRawToolArgumentsObject(plannerInputJson),
                )
            }
            ?: compiledCall
}
