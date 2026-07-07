package com.kolesnikovprod.ksetaorch.download.domain.data

/**
 * Стабильный идентификатор устанавливаемого локального артефакта.
 *
 * Target нужен не для UI-красоты, а для архитектуры: coordinator может работать
 * с абстрактным use case, а конкретная реализация сама знает, какую модель,
 * директорию и runtime-назначение она обслуживает.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxInstallTarget(
    val id:                   String,
    val displayName:          String,
    val storageDirectoryName: String,
) {
    /**
     * Основная local LLM-модель агента.
     */
    GEMMA_4_E2B(
        id                   = "gemma-4-e2b",
        displayName          = "Gemma 4 E2B",
        storageDirectoryName = "gemma-4-e2b",
    ),

    /**
     * Маленькая специализированная модель выбора Android-действий.
     */
    FUNCTION_GEMMA_270M(
        id                   = "functiongemma-270m",
        displayName          = "FunctionGemma 270M Mobile Actions",
        storageDirectoryName = "functiongemma-270m",
    ),

    /**
     * Маленькая русская Vosk-модель для offline STT.
     */
    VOSK_RU_SMALL(
        id                   = "vosk-ru-small",
        displayName          = "Vosk Russian Small",
        storageDirectoryName = "vosk",
    ),
}