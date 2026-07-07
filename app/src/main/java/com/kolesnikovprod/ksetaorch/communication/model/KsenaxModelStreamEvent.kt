package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Событие потоковой генерации ответа локальной модели.
 *
 * [TextDelta] приносит очередной фрагмент текста. [Completed] завершает один
 * model turn и содержит полный ответ вместе с итоговой задержкой.
 *
 * События относятся к одному вызову [KsenaxModelSession.streamPersistent] и
 * приходят в порядке генерации.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxModelStreamEvent {

    /**
     * Очередной фрагмент ответа модели.
     *
     * LiteRT-LM сам определяет размер фрагмента. Это может быть токен, часть
     * слова или несколько слов, поэтому потребитель должен дописывать [text] к
     * уже полученному ответу без предположений о границах токенов.
     *
     * @since 0.2
     */
    data class TextDelta(
        val text: String,
    ) : KsenaxModelStreamEvent

    /**
     * Успешное завершение потоковой генерации.
     *
     * [response] содержит собранный полный текст и длительность model turn-а.
     *
     * @since 0.2
     */
    data class Completed(
        val response: KsenaxModelResponse,
    ) : KsenaxModelStreamEvent
}
