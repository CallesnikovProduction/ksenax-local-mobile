package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Запрос к локальной model-session.
 *
 * [prompt] всегда содержит текущую задачу для модели. Для router-а это полный
 * prompt-контракт, для чата это текст пользователя, для voice transcription это
 * инструкция по расшифровке аудио. [voiceMessage] заполняется только для
 * [KsenaxModelTaskProfile.VOICE_TRANSCRIPTION].
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 * @see KsenaxModelSession
 * @see LiteRtKsenaxModelSession
 */
data class KsenaxModelRequest(
    val prompt:            String,
    val systemInstruction: String,
    val profile:           KsenaxModelTaskProfile,
    val voiceMessage:      KsenaxVoiceMessage? = null,
)
