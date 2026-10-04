package com.kolesnikovprod.ksetaorch.communication.work.oneshot

import kotlinx.serialization.json.*

/**
 * Проверяет используемое подмножество схем: поля, типы, обязательность и границы.
 * Не исправляет типы и не подставляет отсутствующие значения.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal object ActionArgumentsValidator {
    fun validate(declaration: KsenaxOneShotDeclaration, raw: String) {
        val args = Json.parseToJsonElement(raw) as? JsonObject
            ?: throw IllegalArgumentException("Аргументы действия должны быть объектом.")
        val schema = declaration.parameters?.let { Json.parseToJsonElement(it).jsonObject }
        if (schema == null) {
            require(args.isEmpty()) { "Действие ${declaration.codeName} не принимает аргументы." }
            return
        }
        val properties = schema["properties"]!!.jsonObject
        require(args.keys.all { it in properties }) { "Неизвестные аргументы действия ${declaration.codeName}." }
        schema["required"]?.jsonArray?.forEach { field ->
            val name = field.jsonPrimitive.content
            if (args[name] == null || args[name] == JsonNull) throw MissingActionArgument(name)
        }
        args.forEach { (name, value) ->
            val property = properties.getValue(name).jsonObject
            val primitive = value as? JsonPrimitive
                ?: throw IllegalArgumentException("Аргумент '$name' должен быть скалярным.")
            val type = property.getValue("type").jsonPrimitive.content
            require(when (type) {
                "string" -> primitive.isString && primitive.content.isNotBlank()
                "integer" -> !primitive.isString && primitive.longOrNull != null
                "number" -> !primitive.isString && primitive.doubleOrNull?.isFinite() == true
                "boolean" -> !primitive.isString && primitive.booleanOrNull != null
                else -> false
            }) { "Неверный тип аргумента '$name': ожидается $type." }
            if (type == "number" || type == "integer") {
                val number = primitive.double
                property["minimum"]?.jsonPrimitive?.double?.let { require(number >= it) { "Аргумент '$name' меньше $it." } }
                property["maximum"]?.jsonPrimitive?.double?.let { require(number <= it) { "Аргумент '$name' больше $it." } }
            }
        }
    }
}

/**
 * Отсутствующее поле означает уточнение, а не попытку исполнения.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class MissingActionArgument(val field: String) : IllegalArgumentException("Уточни '$field' для действия.")
