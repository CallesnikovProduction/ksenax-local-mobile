package com.kolesnikovprod.ksetaorch.communication.tools.contracts

/**
 * JSON-object контракт аргументов tool-а.
 *
 * Для [KsenaxToolDefinition] этот объект описывает JSON-schema аргументов для
 * prompt-а. В [KsenaxToolCall] этот же контракт хранит уже заполненный JSON
 * аргументов, который вернула модель.
 * Конкретный tool сам решает, как парсить ИМЕННО ЭТИ поля внутри executor-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
fun interface KsenaxToolArgumentsObject {

    /**
     * Возвращает JSON-object как строку.
     *
     * - Для definition это JSON-схема, которая попадает в маршрутизированный промпт.
     * - Для tool call это JSON-значения аргументов, которые executor разбирает
     * своим кодом.
     *
     * @since 0.2
     */
    fun JSONtoString(): String
}

/**
 * Простой immutable arguments object для уже готовой JSON-строки.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxRawToolArgumentsObject(
    private val json: String,
) : KsenaxToolArgumentsObject {

    init {
        require(json.isNotBlank()) {
            "Tool arguments JSON must not be blank."
        }
        require(json.trim().startsWith("{") && json.trim().endsWith("}")) {
            "Tool arguments JSON must be an object."
        }
    }

    override fun JSONtoString(): String = json
}
