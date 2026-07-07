package com.kolesnikovprod.ksetaorch.communication.model

import com.kolesnikovprod.ksetaorch.communication.model.internal.transcription.RussianVoiceTranscriptionPrompt
import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt
import kotlinx.coroutines.flow.Flow

/**
 * Контракт runtime-сессии локальной модели.
 *
 * Этот слой отвечает только за связь с моделью: загрузить runtime, отправить
 * prompt и вернуть сырой текст ответа. Сессия не парсит tool-call, не применяет
 * policy, не исполняет действия и не знает про Android UI.
 *
 * [askStateless] нужен для технических одноразовых задач с системным
 * контрактом. [askPersistent] нужен для обычного диалога с историей.
 * [streamEphemeral] выделен для сырых one-shot запросов без системной
 * инструкции и без наследования прошлых сообщений.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxModelSession {

    /**
     * Текущие параметры model runtime.
     *
     * Runtime-реализации должны переопределять значение, если поддерживают
     * изменение конфигурации через [configureRuntime].
     *
     * @since 0.2
     */
    val runtimeConfig: KsenaxModelRuntimeConfig
        get() = KsenaxModelRuntimeConfig.DEFAULT

    /**
     * Возможности модели и runtime, стоящих за этой сессией.
     *
     * В частности, [KsenaxModelSessionCapabilities.supportsAudioInput] не
     * гарантирован для каждой модели: text-only и action-router модели могут
     * работать без audio backend. Реализация обязана отклонить [transcribe] до
     * обращения к runtime, если audio input не поддерживается.
     *
     * Значение по умолчанию сохраняет совместимость существующих fake-сессий.
     * Новые runtime-реализации должны объявлять возможности явно.
     *
     * @since 0.2
     */
    val capabilities: KsenaxModelSessionCapabilities
        get() = KsenaxModelSessionCapabilities.UNKNOWN

    /**
     * Загружает модельный runtime и готовит ресурсы для последующих запросов.
     *
     * Реализация должна выдерживать повторный вызов без пересоздания уже
     * активного engine. UI может вызвать этот метод заранее, чтобы отделить
     * холодный старт модели от первого пользовательского запроса.
     *
     * @since 0.2
     */
    suspend fun initializeEngine()

    /**
     * Применяет новую конфигурацию model runtime.
     *
     * Параметры [Engine][com.google.ai.edge.litertlm.Engine] нельзя изменить
     * после инициализации. Поэтому LiteRT-реализация дожидается завершения
     * текущего inference, закрывает engine и persistent conversation, а новый
     * engine создаёт лениво при следующем запросе или [initializeEngine].
     *
     * Повторная передача текущей конфигурации ничего не пересоздаёт.
     *
     * @throws UnsupportedOperationException если реализация сессии не
     *         поддерживает runtime-конфигурацию.
     *
     * @since 0.2
     */
    suspend fun configureRuntime(config: KsenaxModelRuntimeConfig) {
        throw UnsupportedOperationException(
            "This model session does not support runtime configuration."
        )
    }

    /**
     * Отправляет запрос в одноразовую conversation поверх уже загруженного
     * runtime-а.
     *
     * Метод подходит для action-router: каждый вызов получает текущий prompt,
     * текущую системную инструкцию и текущий список доступных tools без
     * наследования прошлых JSON-ответов.
     *
     * **Иными словами, ИСПОЛЬЗОВАТЬ ДЛЯ АГЕНТНЫХ ЗАПРОСОВ.**
     *
     * @since 0.2
     */
    suspend fun askStateless(request: KsenaxModelRequest): KsenaxModelResponse

    /**
     * Отправляет запрос в persistent conversation, где runtime хранит историю
     * сообщений до [resetPersistentConversation] или [close].
     *
     * Этот режим нельзя использовать как основной router-контур, пока prompt
     * содержит строгую JSON-схему и tool definitions. Для router-а безопаснее
     * [askStateless].
     *
     * **Иными словами, ИСПОЛЬЗОВАТЬ ТОЛЬКО ДЛЯ РЕЖИМА ОБЫЧНОЙ ПЕРЕПИСКИ.**
     *
     * @since 0.2
     */
    suspend fun askPersistent(request: KsenaxModelRequest): KsenaxModelResponse

    /**
     * Потоково генерирует ответ внутри persistent conversation.
     *
     * Возвращаемый [Flow] холодный: запрос к модели начинается после вызова
     * `collect`. Во время генерации Flow отдаёт
     * [KsenaxModelStreamEvent.TextDelta], а после завершения одно событие
     * [KsenaxModelStreamEvent.Completed].
     *
     * Метод предназначен для обычного чата, где интерфейс показывает ответ по
     * мере генерации. Router и voice transcription продолжают использовать
     * [askStateless], потому что им нужен полный технический ответ.
     *
     * Отмена корутины, которая собирает Flow, должна остановить текущую
     * генерацию LiteRT-LM. Реализация может сбросить persistent conversation,
     * если runtime не гарантирует откат частично сгенерированного turn-а.
     *
     * @since 0.2
     */
    fun streamPersistent(request: KsenaxModelRequest): Flow<KsenaxModelStreamEvent>

    /**
     * Потоково выполняет один сырой текстовый запрос без системной инструкции
     * и без сохранения conversation между вызовами.
     *
     * Каждый `collect` должен создать новую одноразовую conversation. Этот вход
     * предназначен для TEMPORARIC_PATTERN-режима тестирования модели: предыдущие
     * пользовательские сообщения и ответы не могут попасть в новый inference.
     *
     * @since 0.2
     */
    fun streamEphemeral(userText: String): Flow<KsenaxModelStreamEvent>

    /**
     * Расшифровывает голосовое сообщение через multimodal-вход модели.
     *
     * Метод остаётся частью model-session, потому что транскрибация требует
     * доступа к LiteRT-LM conversation и audio payload. Внешний voice-контур
     * отвечает за запись WAV и состояние записи, а session отвечает за сам
     * inference.
     *
     * По умолчанию переводит на *русский* язык.
     *
     * Метод доступен только когда [capabilities] объявляет
     * [KsenaxModelSessionCapabilities.supportsAudioInput]. Общий контракт
     * содержит его для единообразного доступа к сессии, но text-only модель
     * обязана завершить вызов понятной ошибкой до запуска inference.
     *
     * **Иными словами, ИСПОЛЬЗОВАТЬ ДЛЯ ТРАНСКРИБАЦИИ ЧЕРЕЗ МОДЕЛЬ.**
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    suspend fun transcribe(
        voiceMessage: KsenaxVoiceMessage,
        prompt:       KsenaxVoiceTranscriptionPrompt = RussianVoiceTranscriptionPrompt,
    ): KsenaxModelResponse

    /**
     * Сбрасывает историю persistent conversation, не выгружая модельный engine.
     *
     * Метод нужен, когда приложение хочет начать новый диалог дешевле, чем
     * через полный [close] и повторную загрузку модели.
     *
     * @since 0.2
     */
    suspend fun resetPersistentConversation()

    /**
     * Освобождает ресурсы runtime-а и делает сессию непригодной для запросов
     * до следующего [initializeEngine].
     *
     * **Реализация ДОЛЖНА ЗАКРЫВАТЬ нативные ресурсы LiteRT-LM и очищать ссылки
     * на conversation, чтобы экран или сервис не держал модель в памяти дольше
     * нужного.**
     *
     * @since 0.2
     */
    suspend fun close()
}
