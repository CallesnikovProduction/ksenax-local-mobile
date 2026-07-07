package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Поддерживаемый backend аудиовхода для [LiteRtKsenaxModelSession].
 *
 * Сам факт наличия значения означает, что сессия создаётся с поддержкой audio
 * input. `null` в параметре `audioBackend` означает text-only runtime.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxLiteRtAudioBackend {

    /**
     * Обработка audio input на CPU.
     *
     * @since 0.2
     */
    CPU,
}
