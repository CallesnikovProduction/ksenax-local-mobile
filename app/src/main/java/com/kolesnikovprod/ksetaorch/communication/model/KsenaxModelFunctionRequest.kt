package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Одноразовый запрос с объявлениями функций; шаблоном диалога владеет runtime.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
data class KsenaxModelFunctionRequest(
    val userMessage: String,
    val functions: List<KsenaxModelFunctionDeclaration>,
    val systemInstruction: String = "You are a model that can do function calling with the following functions",
) {
    init {
        require(userMessage.isNotBlank())
        require(functions.isNotEmpty())
        require(functions.map { it.name }.distinct().size == functions.size)
    }
}

/**
 * Данные функции, без ссылки на исполнитель или Android.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
data class KsenaxModelFunctionDeclaration(
    val name: String,
    val description: String,
    val parametersJson: String? = null,
)

/**
 * Неавторизованное предложение модели и длительность inference.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
data class KsenaxModelFunctionResponse(
    val calls: List<KsenaxModelFunctionCall>,
    val latencyMs: Long,
)

/**
 * Имя и аргументы из native tool-call. Проверяются вызывающим контуром.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
data class KsenaxModelFunctionCall(val name: String, val argumentsJson: String)
