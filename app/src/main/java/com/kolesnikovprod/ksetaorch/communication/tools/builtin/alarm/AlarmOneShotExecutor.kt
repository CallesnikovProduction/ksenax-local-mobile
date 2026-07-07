package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import org.json.JSONObject

/**
 * Исполнитель маленьких FunctionGemma alarm-вызовов.
 *
 * One-shot функции держат аргументы простыми (`hours`, `minutes`, `time`), а
 * этот adapter переводит их в старый `alarm_tool` контракт, который уже умеет
 * создавать системные Android-будильники.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class AlarmOneShotExecutor(context: Context) : KsenaxToolExecutor {

    private val appContext: Context = context.applicationContext
    private val alarmToolExecutor by lazy { AlarmToolExecutor(context) }

    override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult {
        if (call.name == AlarmToolOneShot.ClearAll.codeName) {
            return dismissAllAlarms(call)
        }

        val legacyArguments = runCatching {
            translateArguments(
                toolName = call.name,
                arguments = JSONObject(call.arguments.JSONtoString()),
            )
        }.getOrElse { error ->
            return KsenaxToolResult.Failure(
                callId = call.id,
                toolName = call.name,
                reason = error.message ?: "Неверные one-shot аргументы будильника.",
                errorCode = "INVALID_ARGUMENTS",
            )
        }

        val legacyCall = call.copy(
            name = AlarmToolExecutor.TOOL_NAME,
            arguments = KsenaxRawToolArgumentsObject(legacyArguments.toString()),
        )

        return alarmToolExecutor.execute(legacyCall).withToolName(call.name)
    }

    private fun translateArguments(
        toolName: String,
        arguments: JSONObject,
    ): JSONObject =
        JSONObject().also { output ->
            when (toolName) {
                AlarmToolOneShot.AtTime.codeName ->
                    output.put("start_local_time", arguments.requiredClockTime("time", "start_local_time"))
                AlarmToolOneShot.AtDateTime.codeName ->
                    output.put(
                        "start_local_date_time",
                        arguments.requiredString("date_time", "start_local_date_time"),
                    )
                AlarmToolOneShot.AfterHours.codeName ->
                    output.put("start_delay_hours", arguments.requiredDouble("hours", "start_delay_hours"))
                AlarmToolOneShot.AfterMinutes.codeName ->
                    output.put("start_delay_minutes", arguments.requiredInt("minutes", "start_delay_minutes"))
                else -> error("AlarmOneShotExecutor cannot execute tool: $toolName.")
            }

            arguments.optionalInt("count")?.let { count -> output.put("count", count) }
            arguments.optionalString("label")?.let { label -> output.put("label", label) }
        }

    private fun JSONObject.requiredString(vararg names: String): String =
        names.firstNotNullOfOrNull { name -> optionalString(name) }
            ?: throw IllegalArgumentException("Argument `${names.joinToString("|")}` must be a string.")

    private fun JSONObject.requiredInt(vararg names: String): Int =
        names.firstNotNullOfOrNull { name -> optionalInt(name) }
            ?: throw IllegalArgumentException("Argument `${names.joinToString("|")}` must be an integer.")

    private fun JSONObject.requiredDouble(vararg names: String): Double =
        names.firstNotNullOfOrNull { name -> optionalDouble(name) }
            ?: throw IllegalArgumentException("Argument `${names.joinToString("|")}` must be a number.")

    private fun JSONObject.optionalString(name: String): String? =
        optString(name).trim().takeIf { value -> value.isNotBlank() }

    private fun JSONObject.optionalInt(name: String): Int? =
        when (val value = opt(name)) {
            null,
            JSONObject.NULL -> null
            is Number -> value.toDouble().takeIf { number -> number % 1.0 == 0.0 }?.toInt()
            is String -> value.trim()
                .substringBefore(' ')
                .replace(',', '.')
                .let { number -> number.toIntOrNull() ?: number.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt() }
            else -> null
        }

    private fun JSONObject.optionalDouble(name: String): Double? =
        when (val value = opt(name)) {
            null,
            JSONObject.NULL -> null
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }

    private fun JSONObject.requiredClockTime(vararg names: String): String {
        val raw = requiredString(*names)
            .lowercase()
            .replace("часов", "")
            .replace("часа", "")
            .replace("час", "")
            .replace("ч.", "")
            .replace("ч", "")
            .trim()

        if (':' in raw) {
            val hour = raw.substringBefore(':').trim().toIntOrNull()
            val minute = raw.substringAfter(':').trim().toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                return "%02d:%02d".format(hour, minute)
            }
        }

        raw.toIntOrNull()?.let { hour ->
            if (hour in 0..23) {
                return "%02d:00".format(hour)
            }
        }

        throw IllegalArgumentException("Argument `${names.joinToString("|")}` must be HH:mm or a valid hour.")
    }

    private fun KsenaxToolResult.withToolName(toolName: String): KsenaxToolResult =
        when (this) {
            is KsenaxToolResult.Success -> copy(toolName = toolName)
            is KsenaxToolResult.Failure -> copy(toolName = toolName)
        }

    private fun dismissAllAlarms(call: KsenaxToolCall): KsenaxToolResult =
        try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM)
                .putExtra(
                    AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_ALL,
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            KsenaxToolResult.Success(
                callId = call.id,
                toolName = call.name,
                message = "Отправил системным Часам запрос на отключение всех будильников.",
            )
        } catch (_: ActivityNotFoundException) {
            KsenaxToolResult.Failure(
                callId = call.id,
                toolName = call.name,
                reason = "На устройстве не найдено приложение часов, которое поддерживает отключение всех будильников через Android AlarmClock API.",
                errorCode = "CLEAR_ALL_ALARMS_ACTIVITY_NOT_FOUND",
            )
        } catch (error: SecurityException) {
            KsenaxToolResult.Failure(
                callId = call.id,
                toolName = call.name,
                reason = error.message ?: "Системные Часы запретили отключение будильников.",
                errorCode = "CLEAR_ALL_ALARMS_SECURITY_ERROR",
            )
        }
}
