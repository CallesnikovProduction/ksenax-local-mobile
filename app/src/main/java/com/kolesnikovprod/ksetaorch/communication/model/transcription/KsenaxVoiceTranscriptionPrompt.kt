package com.kolesnikovprod.ksetaorch.communication.model.transcription

/**
 * Prompt-контракт для расшифровки голосового сообщения.
 *
 * Контракт описывает язык результата и две строки, которые
 * [com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession]
 * передаёт в LiteRT-LM: system instruction и user prompt. Он не записывает
 * звук, не читает WAV и не запускает model runtime.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxVoiceTranscriptionPrompt {

    /**
     * Локаль текста, который должна вернуть модель.
     *
     * Значение нужно для диагностики, UI и будущего выбора профиля
     * транскрибации.
     *
     * @since 0.2
     */
    val targetLocale: String

    /**
     * Человекочитаемое имя языка результата НА АНГЛИЙСКОМ ЯЗЫКЕ:
     * ```
     * Russian
     * English
     * Brazilian
     * ```
     *
     * Его можно использовать в логах, настройках и сообщениях об ошибках.
     *
     * @since 0.2
     */
    val targetLanguageName: String

    /**
     * Возвращает системную инструкцию для multimodal conversation.
     *
     * Инструкция задаёт роль короткого transcription worker и фиксирует язык
     * ответа.
     *
     * @since 0.2
     */
    fun systemInstruction(): String

    /**
     * Возвращает пользовательскую инструкцию, которая идёт вместе с audio payload.
     *
     * Prompt должен просить модель вернуть только текст распознанной речи без
     * пояснений, заголовков и служебных пометок.
     *
     * @since 0.2
     */
    fun userPrompt(): String
}
