package com.kolesnikovprod.ksetaorch.communication.work.actions

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.MissingActionArgument
import kotlinx.serialization.json.*

/**
 * Сопоставляет исходные ограничения с упорядоченным набором шагов, не выбирая функции.
 * Одну исходную команду нельзя потерять или использовать для двух шагов.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
object KsenaxPlannedInputValidator {
    fun validate(calls: List<KsenaxToolCall>, sources: List<KsenaxActionInputDraft>, defaults: JsonObject = JsonObject(emptyMap())) {
        if (calls.size != sources.size) throw MissingActionArgument("полный план без потерянных или повторённых команд")
        val expectedInputs = sources.map { source ->
            requireNotNull(source.expectedActionName) to
                Json.parseToJsonElement(source.argumentsJson ?: "{}").jsonObject
        }
        for ((call, expectedInput) in calls.zip(expectedInputs)) {
            val actual = Json.parseToJsonElement(call.arguments.JSONtoString()).jsonObject
            val (name, expected) = expectedInput
            if (name != call.name || expected.any { (key, value) -> !equivalent(value, actual[key] ?: defaults[key]) }) {
                throw MissingActionArgument("порядок и параметры плана, соответствующие исходным числам, единицам, дате и времени")
            }
        }
    }

    private fun equivalent(expected: JsonElement, actual: JsonElement?): Boolean {
        if (expected == actual) return true
        if (expected !is JsonPrimitive || actual !is JsonPrimitive || expected.isString || actual.isString) return false
        val number = expected.doubleOrNull ?: return false
        return number.isFinite() && number == actual.doubleOrNull
    }
}
