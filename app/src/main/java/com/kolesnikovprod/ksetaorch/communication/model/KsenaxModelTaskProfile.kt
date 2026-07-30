package com.kolesnikovprod.ksetaorch.communication.model

/**
 * Тип задачи, с которой приложение обращается к локальной модели.
 *
 * Profile выбирает не только набор prompt-блоков. Он также задаёт допустимый
 * путь выполнения в [KsenaxModelSession]:
 *
 * - [ROUTER] идёт через `askStateless`, потому что router каждый раз получает
 *   полный контракт tools, policy и формат ответа.
 * - [CHAT] идёт через `askPersistent`, потому что обычный диалог должен
 *   сохранять историю внутри active conversation.
 * - [VOICE_TRANSCRIPTION] идёт через `transcribe` или stateless-запрос с
 *   [KsenaxVoiceMessage], потому что распознавание аудио не должно смешиваться
 *   с историей чата или router-а.
 * - [TEXT_GENERATION] идёт через `askStateless` и даёт аддонам
 *   одноразовую генерацию без доступа к persistent-истории OKx.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxModelTaskProfile {

    /**
     * **ЗАПРОС НА ДЕЙСТВИЕ АГЕНТА.**
     *
     * Модель должна вернуть структурированный router response, который дальше
     * разбирает parser. Этот режим не хранит историю между вызовами.
     *
     * @since 0.2
     */
    ROUTER,

    /**
     * **ОБЫЧНЫЙ ДИАЛОГ С МОДЕЛЬЮ.**
     *
     * Session использует persistent conversation и сбрасывает её только через
     * `resetPersistentConversation` или `close`.
     *
     * @since 0.2
     */
    CHAT,

    /**
     * **РАСШИФРОВКА ГОЛОСОВОГО НАБОРА ТЕКСТА.**
     *
     * Session отправляет аудио и короткую инструкцию в одноразовую conversation.
     * Ответ должен содержать только текст распознанной речи.
     *
     * @since 0.2
     */
    VOICE_TRANSCRIPTION,

    /**
     * **ОДНОРАЗОВАЯ ТЕКСТОВАЯ ГЕНЕРАЦИЯ ДЛЯ ADDON MODEL PROVIDER.**
     *
     * Профиль не использует persistent conversation и не несёт
     * router/tool-семантику. Каждый запрос самодостаточен.
     *
     * @since 0.3
     */
    TEXT_GENERATION,
}
