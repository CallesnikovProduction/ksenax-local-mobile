package com.kolesnikovprod.ksetaorch.communication.work.routing

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import java.time.ZonedDateTime
import kotlinx.serialization.json.*

/** Разбор маршрута Qwen: собственный JSON или сырой Hermes из Generic processor.
 * Не исполняет действия, не подделывает SDK ToolCall и не использует токены FunctionGemma.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class QwenRoutingProtocol(private val actions: List<KsenaxOneShotDeclaration>) {
    private val names = actions.map { it.codeName }.toSet()

    init { require(actions.isNotEmpty() && names.size == actions.size) }

    fun buildRequest(userText: String, requestTime: ZonedDateTime): KsenaxModelRequest {
        require(userText.isNotBlank())
        val system = buildString {
            appendLine("Ты классифицируешь запрос для Android-приложения. Не исполняй его и не отвечай пользователю.")
            appendLine("Верни ровно один JSON-объект без Markdown, пояснений и <tool_call>.")
            appendLine("route: FAST_TOOL — одна готовая операция; LLM_BOUND — генерация текста/заметки или разные операции; NEEDS_CLARIFICATION — не хватает данных; UNSUPPORTED — вопрос, факт, отрицание или недоступное действие.")
            appendLine("Количество одинаковых будильников задаётся count и само по себе не требует планирования.")
            appendLine("FAST_TOOL требует tool (код из каталога) и arguments (объект полных аргументов). Другие маршруты не содержат tool/arguments; допускаются question для уточнения или reason для отказа.")
            appendLine("У FAST_TOOL ровно три корневых поля: route, tool, arguments. reason, question и count в корне запрещены.")
            appendLine("arguments содержит только поля выбранного инструмента. Если поля обозначены {}, верни arguments:{} без count и других полей.")
            appendLine("Не выдумывай время, дату или количество. Время HH:mm, дата-время yyyy-MM-dd'T'HH:mm; числовые поля — JSON-числа. count по умолчанию 1 только у инструментов, где поле count объявлено.")
            appendLine("Локальные дата и время: $requestTime")
            appendLine("Каталог (код: смысл; поля, ? означает необязательное):")
            actions.forEach { action ->
                val schema = action.parameters?.let { Json.parseToJsonElement(it) as JsonObject }
                val required = schema?.get("required")?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
                val fields = (schema?.get("properties") as? JsonObject)?.entries?.joinToString(", ") { (name, value) ->
                    val property = value.jsonObject
                    val type = property["type"]?.jsonPrimitive?.content ?: "value"
                    val description = property["description"]?.jsonPrimitive?.content
                    "$name:$type${if (name in required) "" else "?"}${description?.let { " ($it)" }.orEmpty()}"
                }.orEmpty()
                appendLine("${action.codeName}: ${action.description.substringBefore("Use for").trim()}; ${fields.ifEmpty { "{}" }}")
            }
        }
        return KsenaxModelRequest(prompt = userText, systemInstruction = system, profile = KsenaxModelTaskProfile.ROUTER)
    }

    fun parseResponse(text: String): KsenaxRoutingDecision {
        val root = readObject(text)
        fun string(key: String, optional: Boolean = false): String? {
            val value = root[key]
            if (value == null && optional) return null
            require(value is JsonPrimitive && value.isString && value.content.isNotBlank()) { "Routing $key must be a non-empty string." }
            return value.content
        }
        return when (string("route")) {
            "FAST_TOOL" -> {
                require(root.keys == setOf("route", "tool", "arguments")) { "Invalid FAST_TOOL fields." }
                val tool = requireNotNull(string("tool"))
                require(tool in names) { "Unknown routing tool: $tool." }
                val arguments = root["arguments"] as? JsonObject ?: error("Routing arguments must be an object.")
                KsenaxRoutingDecision.FastTool(tool, arguments)
            }
            "LLM_BOUND" -> { require(root.keys == setOf("route")); KsenaxRoutingDecision.PlannedWork }
            "NEEDS_CLARIFICATION" -> {
                require(root.keys.all { it in setOf("route", "question") })
                KsenaxRoutingDecision.Clarification(string("question", optional = true))
            }
            "UNSUPPORTED" -> {
                require(root.keys.all { it in setOf("route", "reason") })
                KsenaxRoutingDecision.Unsupported(string("reason", optional = true))
            }
            else -> error("Unknown routing class.")
        }
    }

    // Legacy INT8 имеет Generic processor: Hermes-вызов приходит как text,
    // не SDK ToolCall. Формат не ремонтируем, проверяем исходный JSON до DTO.
    fun parseHermesResponse(text: String): KsenaxRoutingDecision {
        require(text.length <= 16_384) { "Routing response exceeds the format budget." }
        val source = text.trim()
        require(source.startsWith("<tool_call>") && source.endsWith("</tool_call>")) { "Expected one Qwen tool_call block." }
        val root = readObject(source.removePrefix("<tool_call>").removeSuffix("</tool_call>"))
        require(root.keys == setOf("name", "arguments")) { "Invalid Qwen function fields." }
        val name = root["name"] as? JsonPrimitive
        require(name != null && name.isString && name.content.isNotBlank()) { "Qwen function name must be a string." }
        val arguments = root["arguments"] as? JsonObject ?: error("Qwen arguments must be an object.")
        return when (name.content) {
            "route_planned_work" -> { require(arguments.isEmpty()); KsenaxRoutingDecision.PlannedWork }
            "route_clarification" -> { require(arguments.isEmpty()); KsenaxRoutingDecision.Clarification(null) }
            "route_unsupported" -> { require(arguments.isEmpty()); KsenaxRoutingDecision.Unsupported(null) }
            else -> {
                require(name.content in names) { "Unknown Qwen tool: ${name.content}." }
                KsenaxRoutingDecision.FastTool(name.content, arguments)
            }
        }
    }

    private fun readObject(text: String): JsonObject {
        require(text.length <= 16_384) { "Routing response exceeds the format budget." }
        val source = text.trim()
        rejectDuplicateKeys(source)
        return Json.parseToJsonElement(source) as? JsonObject ?: error("Routing response must be one JSON object.")
    }

    // Ограничиваем глубину до Json-parser-а и запрещаем повторные ключи,
    // включая эквивалентные Unicode-escape и вложенные arguments.
    private fun rejectDuplicateKeys(source: String) {
        val stack = mutableListOf<MutableSet<String>?>()
        var i = 0
        while (i < source.length) {
            when (source[i]) {
                '{', '[' -> {
                    require(stack.size < 32) { "Routing JSON is too deeply nested." }
                    stack.add(if (source[i] == '{') mutableSetOf() else null)
                }
                '}', ']' -> { require(stack.isNotEmpty()); stack.removeAt(stack.lastIndex) }
                '"' -> {
                    val start = i++
                    while (i < source.length && source[i] != '"') { if (source[i] == '\\') i++; i++ }
                    require(i < source.length) { "Unterminated routing JSON string." }
                    var next = i + 1
                    while (next < source.length && source[next].isWhitespace()) next++
                    if (next < source.length && source[next] == ':') {
                        val key = Json.decodeFromString<String>(source.substring(start, i + 1))
                        require(requireNotNull(stack.lastOrNull()).add(key)) { "Duplicate routing argument: $key." }
                    }
                }
            }
            i++
        }
    }
}
