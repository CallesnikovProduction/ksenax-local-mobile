package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Возможности конкретной runtime-сессии модели.
 *
 * Контракт отделяет наличие метода в общем API от фактических возможностей
 * загруженной модели. Например, FunctionGemma остаётся полноценной текстовой
 * сессией, но должна объявлять [supportsAudioInput] как `false`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxModelSessionCapabilities(
    val audioInput: KsenaxModelAudioInputCapability,
) {
    /**
     * Удобная проверка для caller-а, которому нужен именно audio input.
     *
     * `UNKNOWN` не считается поддержкой: capability должна быть объявлена
     * реализацией явно.
     */
    val supportsAudioInput: Boolean
        get() = audioInput == KsenaxModelAudioInputCapability.SUPPORTED

    companion object {

        /**
         * Необъявленные возможности существующей fake/custom реализации.
         *
         * Новые реализации должны переопределять
         * [KsenaxModelSession.capabilities] явно.
         *
         * @since 0.2
         */
        val UNKNOWN: KsenaxModelSessionCapabilities =
            KsenaxModelSessionCapabilities(
                audioInput = KsenaxModelAudioInputCapability.UNKNOWN,
            )
    }
}

/**
 * Состояние поддержки audio input конкретной model-session.
 *
 * [UNKNOWN] нужен только для обратной совместимости реализаций, созданных до
 * появления capability-контракта. Production runtime должен выбирать
 * [SUPPORTED] или [UNSUPPORTED] явно.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxModelAudioInputCapability {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}
