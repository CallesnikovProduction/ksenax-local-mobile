package com.kolesnikovprod.ksetaorch.ui.main.settings

/**
 * Модели, которые приложение умеет использовать как основной текстовый
 * runtime. Этот выбор не связан с моделью распознавания речи.
 */
enum class KsenaxSupportedTextModel(
    val title: String,
    val description: String,
) {
    Gemma(
        title = "Gemma 4 E2B",
        description =
            "Основная локальная модель OpenKsenax для обычного текстового чата.",
    ),
    FunctionGemma(
        title = "FunctionGemma 270M",
        description =
            "Компактная Mobile Actions-модель для tool routing и коротких ответов.",
    ),
}
