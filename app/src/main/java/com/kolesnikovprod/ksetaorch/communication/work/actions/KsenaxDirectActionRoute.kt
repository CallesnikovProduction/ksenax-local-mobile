package com.kolesnikovprod.ksetaorch.communication.work.actions

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords

/**
 * Настройка быстрого входа в один набор атомарных действий.
 *
 * [keywords] только находят подходящий набор после решения FunctionGemma.
 * Конкретную функцию они не выбирают и не запускают.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
data class KsenaxDirectActionRoute(
    val description: String,
    val keywords: KsenaxOneShotKeywords,
) {
    init {
        require(description.isNotBlank()) {
            "Direct action route description must not be blank."
        }
    }
}
