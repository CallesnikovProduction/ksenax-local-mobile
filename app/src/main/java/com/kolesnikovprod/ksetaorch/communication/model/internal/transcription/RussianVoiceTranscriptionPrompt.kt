package com.kolesnikovprod.ksetaorch.communication.model.internal.transcription

import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt

/**
 * Русский prompt-контракт для voice transcription.
 *
 * Это дефолтный профиль для [KsenaxVoiceTranscriptionPrompt]. Он просит модель
 * вернуть только русский текст распознанной речи. Другие языки должны получать
 * отдельные реализации этого интерфейса.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal object RussianVoiceTranscriptionPrompt : KsenaxVoiceTranscriptionPrompt {

    /**
     * Locale русского результата.
     *
     * @since 0.2
     */
    override val targetLocale: String = "ru-RU"

    /**
     * Имя языка для логов и диагностики.
     *
     * @since 0.2
     */
    override val targetLanguageName: String = "Russian"

    /**
     * Системная инструкция для русской транскрибации.
     *
     * @since 0.2
     */
    override fun systemInstruction(): String =
        """
        You are the local Ksenax speech transcription worker.
        Transcribe the provided audio into Russian text.
        Return only the transcript text.
        """.trimIndent()

    /**
     * Короткая инструкция, которую session отправляет вместе с аудио.
     *
     * @since 0.2
     */
    override fun userPrompt(): String =
        """
        Transcribe this audio message into Russian.
        Do not explain, summarize, translate to another language, add labels,
        wrap the answer in quotes, or mention that this is a transcript.
        """.trimIndent()
}
