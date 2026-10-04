package com.kolesnikovprod.ksetaorch.communication.model.internal.litert

import com.google.ai.edge.litertlm.OpenApiTool
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelFunctionDeclaration
import kotlinx.serialization.json.*

/**
 * Мост данных к LiteRT: не содержит исполняемых действий.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class FunctionCallAdapter(declaration: KsenaxModelFunctionDeclaration) : OpenApiTool {
    private val description = buildJsonObject {
        put("name", declaration.name)
        put("description", declaration.description)
        put("parameters", declaration.parametersJson?.let(Json::parseToJsonElement)
            ?: buildJsonObject { put("type", "object"); put("properties", JsonObject(emptyMap())) })
    }.toString()

    override fun getToolDescriptionJsonString(): String = description

    override fun execute(paramsJsonString: String): String =
        error("Model runtime must never execute application actions.")
}

internal fun Any?.toFunctionJson(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> {
        val number = toDouble()
        require(number.isFinite()) { "Non-finite function argument." }
        // Gson представляет целые JSON-числа как Double; сохраняем их точный тип.
        if (number % 1.0 == 0.0 && number >= Long.MIN_VALUE && number < Long.MAX_VALUE)
            JsonPrimitive(number.toLong()) else JsonPrimitive(number)
    }
    is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
        require(key is String) { "Function argument key must be a string." }
        key to value.toFunctionJson()
    })
    is List<*> -> JsonArray(map { it.toFunctionJson() })
    else -> throw IllegalArgumentException("Unsupported native function argument type.")
}
