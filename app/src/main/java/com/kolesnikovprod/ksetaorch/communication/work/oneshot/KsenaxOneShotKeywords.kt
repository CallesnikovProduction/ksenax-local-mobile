package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Детерминированный фильтр набора FG one-shot actions.
 *
 * Фильтр применяется только после того, как FunctionGemma классифицировала
 * запрос как direct. Он не выбирает конкретную функцию, не формирует ответ и
 * не запускает executor; его единственная роль — не отправлять 270M-модели
 * несвязанные declarations.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
fun interface KsenaxOneShotKeywords {
    fun matches(userMessage: String): Boolean
}
