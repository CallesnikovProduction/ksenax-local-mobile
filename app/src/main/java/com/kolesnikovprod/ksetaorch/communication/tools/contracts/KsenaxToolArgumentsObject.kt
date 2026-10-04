package com.kolesnikovprod.ksetaorch.communication.tools.contracts

/**
 * JSON-object контракт аргументов tool-а.
 *
 * В [KsenaxToolCall] контракт хранит заполненный JSON аргументов после
 * OneShot-компиляции и нормализации.
 * Конкретный tool сам решает, как парсить ИМЕННО ЭТИ поля внутри executor-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
fun interface KsenaxToolArgumentsObject {

    /**
     * Возвращает JSON-object как строку.
     *
     * Executor разбирает возвращённый JSON своим кодом.
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
