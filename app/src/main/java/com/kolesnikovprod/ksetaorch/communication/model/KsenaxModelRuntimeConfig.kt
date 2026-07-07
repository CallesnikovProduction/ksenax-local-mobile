package com.kolesnikovprod.ksetaorch.communication.model

private const val DEFAULT_MAX_CONTEXT_TOKENS = 1_024

/**
 * Изменяемые параметры runtime-а локальной модели.
 *
 * [maxContextTokens] соответствует `EngineConfig.maxNumTokens` в LiteRT-LM:
 * это общий бюджет input и output tokens и одновременно размер KV-cache, а не
 * отдельный лимит только пользовательского input. По умолчанию ядро резервирует
 * 1024 токена; явный `null` оставляет значение из metadata модели или дефолт
 * LiteRT-LM.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxModelRuntimeConfig(
    val maxContextTokens: Int? = DEFAULT_MAX_CONTEXT_TOKENS,
) {
    init {
        require(maxContextTokens == null || maxContextTokens > 0) {
            "maxContextTokens must be positive or null."
        }
    }

    companion object {

        /**
         * Базовая конфигурация ядра с бюджетом 1024 токена.
         *
         * @since 0.2
         */
        val DEFAULT: KsenaxModelRuntimeConfig = KsenaxModelRuntimeConfig()
    }
}
