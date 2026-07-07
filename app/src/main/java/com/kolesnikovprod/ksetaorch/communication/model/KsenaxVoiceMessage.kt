package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Аудиофайл, который можно передать в LiteRT-LM через multimodal message.
 *
 * Сейчас session использует только [file]. Остальные поля оставлены как
 * метаданные записанного WAV, чтобы UI и будущая валидация не парсили файл
 * повторно.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxVoiceMessage(
    val file:           java.io.File,
    val sampleRateHz:   Int?  = null,
    val channelCount:   Int?  = null,
    val durationMillis: Long? = null,
)
