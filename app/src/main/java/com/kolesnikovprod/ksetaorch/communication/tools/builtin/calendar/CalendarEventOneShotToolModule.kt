package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import org.json.JSONObject

class CalendarEventOneShotToolModule(
    override val executor: KsenaxToolExecutor,
) : KsenaxOneShotActionKit {

    override val id: String = "system.calendar.oneshot"

    override val namespace: String = "system"

    override val actionSpecs: List<KsenaxWorkActionSpec> =
        listOf(
            KsenaxWorkActionSpec(
                name = CalendarEventOneShot.codeName,
                description = "Prepares a calendar event through Android Calendar insert screen.",
                inputHint = """{"title":"short event title","start_local_date_time":"yyyy-MM-dd'T'HH:mm" or "start_local_date"+"start_local_time","duration_minutes":optional,"location":optional,"description":optional}. Prefer local date/time, do not calculate epoch millis.""",
                examples = listOf("8 июля в 17 часов -> {\"start_local_date_time\":\"2026-07-08T17:00\"}"),
            )
        )

    override val keywords: KsenaxOneShotKeywords = CalendarEventOneShotKeywords

    override val protocol: KsenaxOneShotToolProtocol = CalendarEventOneShotProtocol

    override fun buildFallbackPlannerInputJson(
        userMessage: String,
        step: KsenaxWorkPlanStep,
    ): String? =
        CalendarEventUserPromptDraft.buildJson(userMessage)

    override fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall {
        val plannerArguments = runCatching { JSONObject(step.plannerInputJson ?: "{}") }
            .getOrNull()
            ?.takeIf { arguments -> arguments.length() > 0 }
        val fallbackArguments = CalendarEventUserPromptDraft.buildJson(userMessage)
            ?.let { json -> runCatching { JSONObject(json) }.getOrNull() }
        val mergedArguments = plannerArguments?.withFallbackStartFields(fallbackArguments)
            ?: fallbackArguments
            ?: runCatching { JSONObject(compiledCall.arguments.JSONtoString()) }
                .getOrDefault(JSONObject())
        return compiledCall.copy(
            arguments = KsenaxRawToolArgumentsObject(mergedArguments.toString()),
        )
    }

    private fun JSONObject.withFallbackStartFields(fallback: JSONObject?): JSONObject {
        if (fallback == null || hasAnyStartField()) return this
        return JSONObject(toString()).apply {
            fallback.keys().forEach { key ->
                if (!has(key) || optString(key).isBlank()) {
                    put(key, fallback.opt(key))
                }
            }
        }
    }

    private fun JSONObject.hasAnyStartField(): Boolean =
        listOf(
            "start_local_date_time",
            "startLocalDateTime",
            "start_local_date",
            "startLocalDate",
            "start_local_time",
            "startLocalTime",
            "start_at_millis",
            "startAtMillis",
            "start_delay_minutes",
            "startDelayMinutes",
            "start_delay_hours",
            "startDelayHours",
        ).any(::has)
}
