package com.kolesnikovprod.ksetaorch.communication.model.internal.litert

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxLiteRtAudioBackend
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRequest
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelResponse
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelAudioInputCapability
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSessionCapabilities
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRuntimeConfig
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelStreamEvent
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelTaskProfile
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val NANOSECONDS_PER_MILLISECOND = 1_000_000L

/**
 * Внутренний LiteRT-LM engine для публичного фасада
 * [com.kolesnikovprod.ksetaorch.communication.model.LiteRtKsenaxModelSession].
 *
 * Класс держит один долгоживущий [Engine] и одну активную [Conversation] для
 * persistent-чата. Агентный маршрутизатор и расшифровка голоса используют одноразовые
 * чаты, чтобы прошлые ответы модели не попадали в следующий запрос (и не засирали контекст).
 *
 * [engineMutex] защищает загрузку и закрытие engine. [convMutex] защищает
 * persistent conversation, потому что один объект conversation нельзя считать
 * безопасным для параллельных `sendMessage`.
 *
 * Реализация не парсит ответы, не применяет политик и не исполняет tools. Она
 * возвращает сырой текст модели в [KsenaxModelResponse].
 *
 * @param modelPath путь к `.litertlm` модели.
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal class LiteRtModelSessionEngine(
    private val modelPath:    String,
    private val cacheDirPath: String,
    private val audioBackend: KsenaxLiteRtAudioBackend?,
    initialRuntimeConfig: KsenaxModelRuntimeConfig,
) : KsenaxModelSession {

    @Volatile
    private var configuredRuntime: KsenaxModelRuntimeConfig = initialRuntimeConfig

    override val runtimeConfig: KsenaxModelRuntimeConfig
        get() = configuredRuntime

    override val capabilities: KsenaxModelSessionCapabilities =
        KsenaxModelSessionCapabilities(
            audioInput = if (audioBackend == null) {
                KsenaxModelAudioInputCapability.UNSUPPORTED
            } else {
                KsenaxModelAudioInputCapability.SUPPORTED
            },
        )

    init {
        require(modelPath.isNotBlank()) {
            "modelPath must not be blank."
        }
        require(cacheDirPath.isNotBlank()) {
            "cacheDirPath must not be blank."
        }
    }

    /**
     * Загруженный LiteRT-LM runtime.
     *
     * Пока [engine] не равен `null`, модель уже поднята и может создавать
     * conversation без повторной загрузки `.litertlm` файла.
     */
    private var engine: Engine? = null

    /**
     * Conversation для обычного chat-режима.
     *
     * Router и voice transcription не используют это поле: они создают
     * одноразовую conversation на каждый запрос.
     */
    private var activeConversation: Conversation? = null

    /**
     * System instruction, с которой создана [activeConversation].
     *
     * При смене instruction session создаёт новую persistent conversation,
     * чтобы история чата не продолжалась под другим системным контрактом.
     */
    private var activeConversationSystemInstruction: String? = null

    /**
     * Защищает [activeConversation] и [activeConversationSystemInstruction].
     *
     * Persistent conversation хранит историю, поэтому параллельные `sendMessage`
     * могут перемешать порядок сообщений.
     *
     * @since 0.2
     */
    private val convMutex = Mutex()

    /**
     * Не даёт одному LiteRT engine одновременно выполнять chat-generation и
     * Gemma speech-to-text. Vosk работает вне этого mutex.
     */
    private val inferenceMutex = Mutex()

    /**
     * Защищает создание и закрытие [engine].
     *
     * Без этого два параллельных запроса могут одновременно увидеть
     * `engine == null` и начать двойную загрузку модели.
     *
     * @since 0.2
     */
    private val engineMutex = Mutex()

    override suspend fun initializeEngine() {
        // Публичный lifecycle-метод оставляет всю защиту от гонок в одном месте.
        initEngineIfNeeded()
    }

    override suspend fun configureRuntime(config: KsenaxModelRuntimeConfig) {
        if (configuredRuntime == config) return

        convMutex.withLock {
            inferenceMutex.withLock {
                engineMutex.withLock {
                    if (configuredRuntime == config) return

                    clearActiveConversation()
                    runCatching { engine?.close() }
                    engine = null
                    configuredRuntime = config
                }
            }
        }
    }

    /**
     * Загружает LiteRT-LM engine один раз на lifetime этой session.
     *
     * Метод можно вызывать перед каждым запросом. Первый вызов создаёт cache
     * directory, собирает [EngineConfig], инициализирует [Engine] и сохраняет
     * ссылку. Следующие вызовы сразу выходят.
     *
     * [engineMutex] не даёт двум корутинам одновременно создать два engine.
     * Тяжёлая работа уходит в [Dispatchers.Default], чтобы не блокировать
     * caller thread.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    private suspend fun initEngineIfNeeded() {
        engineMutex.withLock {
            if (engine != null) return

            // LiteRT загружает модель и нативные ресурсы, поэтому уводим работу с caller thread.
            withContext(Dispatchers.Default) {
                if (engine != null) return@withContext

                validateEnginePaths()

                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        audioBackend = audioBackend?.toLiteRtBackend(),
                        maxNumTokens = configuredRuntime.maxContextTokens,
                        cacheDir = cacheDirPath,
                    )
                )

                try {
                    newEngine.initialize()
                    engine = newEngine
                } catch (error: Throwable) {
                    runCatching { newEngine.close() }
                    throw error
                }
            }
        }
    }

    override suspend fun askStateless(
        request: KsenaxModelRequest,
    ): KsenaxModelResponse {
        // Fail fast: CHAT не должен случайно попасть в one-shot conversation.
        validateStateless(request)

        return inferenceMutex.withLock {
            initEngineIfNeeded()
            withContext(Dispatchers.Default) {
                val startedAtNanos = System.nanoTime()
                val text = createConversation(request.systemInstruction).use { oneShotConversation ->
                    try {
                        when (request.profile) {
                            KsenaxModelTaskProfile.ROUTER              -> {
                                val responseText = StringBuilder()
                                oneShotConversation.sendMessageAsync(request.prompt)
                                    .collect { message ->
                                        responseText.append(message.toString())
                                    }
                                responseText.toString()
                            }

                            KsenaxModelTaskProfile.VOICE_TRANSCRIPTION -> {
                                val voiceMessage = requireNotNull(request.voiceMessage) {
                                    "VOICE_TRANSCRIPTION request must contain voiceMessage."
                                }
                                oneShotConversation.sendMessage(
                                    Contents.of(
                                        Content.AudioFile(voiceMessage.file.absolutePath),
                                        Content.Text(request.prompt),
                                    )
                                ).toString()
                            }

                            KsenaxModelTaskProfile.CHAT                -> {
                                error("CHAT profile must use askPersistent(), not askStateless().")
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        runCatching { oneShotConversation.cancelProcess() }
                        throw cancellation
                    }
                }
                val latencyMs = (System.nanoTime() - startedAtNanos) /
                    NANOSECONDS_PER_MILLISECOND

                KsenaxModelResponse(
                    text      = text.trim(),
                    latencyMs = latencyMs,
                    profile   = request.profile,
                )
            }
        }
    }

    override suspend fun askPersistent(
        request: KsenaxModelRequest,
    ): KsenaxModelResponse {
        var completedResponse: KsenaxModelResponse? = null

        streamPersistent(request).collect { event ->
            if (event is KsenaxModelStreamEvent.Completed) {
                completedResponse = event.response
            }
        }

        return checkNotNull(completedResponse) {
            "Persistent model stream completed without a final response."
        }
    }

    override fun streamPersistent(
        request: KsenaxModelRequest,
    ): Flow<KsenaxModelStreamEvent> = flow {
        // Проверка и запуск engine происходят при collect, потому что Flow холодный.
        validatePersistent(request)

        convMutex.withLock {
            inferenceMutex.withLock {
                initEngineIfNeeded()
                val conversation = getOrCreateActiveConversation(request.systemInstruction)
                val responseText = StringBuilder()
                val startedAtNanos = System.nanoTime()

                try {
                    conversation.sendMessageAsync(request.prompt).collect { message ->
                        val textDelta = message.toString()
                        if (textDelta.isNotEmpty()) {
                            responseText.append(textDelta)
                            emit(KsenaxModelStreamEvent.TextDelta(textDelta))
                        }
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) {
                        runCatching { conversation.cancelProcess() }
                    }
                    // LiteRT-LM 0.13.1 не гарантирует rollback незавершённого turn-а.
                    clearActiveConversation()
                    throw error
                }

                emit(
                    KsenaxModelStreamEvent.Completed(
                        response = KsenaxModelResponse(
                            text = responseText.toString().trim(),
                            latencyMs = (System.nanoTime() - startedAtNanos) /
                                NANOSECONDS_PER_MILLISECOND,
                            profile = request.profile,
                        ),
                    )
                )
            }
        }
    }.flowOn(Dispatchers.Default)

    override fun streamEphemeral(
        userText: String,
    ): Flow<KsenaxModelStreamEvent> = flow {
        val normalizedText = userText.trim()
        require(normalizedText.isNotEmpty()) {
            "Ephemeral user text must not be blank."
        }
        inferenceMutex.withLock {
            initEngineIfNeeded()
            val conversation = requireNotNullEngine().createConversation(
                ConversationConfig(),
            )
            val responseText = StringBuilder()
            val startedAtNanos = System.nanoTime()

            try {
                conversation.sendMessageAsync(normalizedText).collect { message ->
                    val textDelta = message.toString()
                    if (textDelta.isNotEmpty()) {
                        responseText.append(textDelta)
                        emit(KsenaxModelStreamEvent.TextDelta(textDelta))
                    }
                }
            } catch (cancellation: CancellationException) {
                runCatching { conversation.cancelProcess() }
                throw cancellation
            } finally {
                runCatching { conversation.close() }
            }

            emit(
                KsenaxModelStreamEvent.Completed(
                    response = KsenaxModelResponse(
                        text = responseText.toString().trim(),
                        latencyMs = (System.nanoTime() - startedAtNanos) /
                            NANOSECONDS_PER_MILLISECOND,
                        profile = KsenaxModelTaskProfile.CHAT,
                    ),
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun transcribe(
        voiceMessage: KsenaxVoiceMessage,
        prompt      : KsenaxVoiceTranscriptionPrompt,
    ): KsenaxModelResponse {
        check(capabilities.supportsAudioInput) {
            "This model session has no audio backend and cannot transcribe voice messages."
        }
        // Транскрибация остаётся stateless: история чата не должна влиять на распознанный текст.
        return askStateless(
            KsenaxModelRequest(
                prompt            = prompt.userPrompt(),
                systemInstruction = prompt.systemInstruction(),
                profile           = KsenaxModelTaskProfile.VOICE_TRANSCRIPTION,
                voiceMessage      = voiceMessage,
            )
        )
    }

    override suspend fun resetPersistentConversation() {
        // Engine остаётся прогретым, очищается только chat history.
        convMutex.withLock {
            clearActiveConversation()
        }
    }

    override suspend fun close() {
        convMutex.withLock {
            inferenceMutex.withLock {
                engineMutex.withLock {
                    clearActiveConversation()
                    runCatching { engine?.close() }
                    engine = null
                }
            }
        }
    }

    /**
     * Возвращает активный [Engine] или падает с понятной ошибкой.
     *
     * Ошибка означает нарушение lifecycle: caller вызвал helper до
     * [initEngineIfNeeded] или после [close].
     *
     * @since 0.2
     */
    private fun requireNotNullEngine(): Engine = requireNotNull(engine) {
        "LiteRT-LM Engine is not initialized"
    }

    /**
     * Проверяет пути перед созданием LiteRT-LM engine.
     *
     * Constructor проверяет только пустые строки. Этот helper проверяет файловую
     * систему ближе к моменту запуска, когда модель уже должна быть скачана и
     * провалидирована download-слоем.
     *
     * @since 0.2
     */
    private fun validateEnginePaths() {
        val modelFile = File(modelPath)
        require(modelFile.isFile) {
            "LiteRT-LM model file must exist: ${modelFile.absolutePath}"
        }
        require(modelFile.canRead()) {
            "LiteRT-LM model file must be readable: ${modelFile.absolutePath}"
        }
        require(modelFile.length() > 0L) {
            "LiteRT-LM model file must not be empty: ${modelFile.absolutePath}"
        }

        val cacheDir = File(cacheDirPath)
        require(!cacheDir.exists() || cacheDir.isDirectory) {
            "LiteRT-LM cache path must be a directory: ${cacheDir.absolutePath}"
        }
        require(cacheDir.exists() || cacheDir.mkdirs()) {
            "LiteRT-LM cache directory could not be created: ${cacheDir.absolutePath}"
        }
        require(cacheDir.canWrite()) {
            "LiteRT-LM cache directory must be writable: ${cacheDir.absolutePath}"
        }
    }

    /**
     * Создаёт LiteRT-LM conversation с указанной system instruction.
     *
     * Stateless-запросы получают новую conversation на каждый вызов.
     * Persistent chat использует этот helper только при первом сообщении или
     * после смены system instruction.
     *
     * @since 0.2
     */
    private fun createConversation(systemInstruction: String): Conversation =
        requireNotNullEngine().createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
            )
        )

    /**
     * Возвращает active persistent conversation для chat-режима.
     *
     * Если system instruction не менялась, метод переиспользует conversation и
     * сохраняет её историю. Если instruction поменялась, метод создаёт новую
     * conversation, потому что старая история собрана под другой системный
     * контракт.
     *
     * @since 0.2
     */
    private fun getOrCreateActiveConversation(systemInstruction: String): Conversation {
        val existingConversation = activeConversation
        if (
            existingConversation != null &&
            activeConversationSystemInstruction == systemInstruction
        ) {
            return existingConversation
        }

        clearActiveConversation()

        return createConversation(systemInstruction).also { conversation ->
            activeConversation                  = conversation
            activeConversationSystemInstruction = systemInstruction
        }
    }

    /**
     * Закрывает persistent conversation и забывает связанную system instruction.
     *
     * Вызывающий код должен уже удерживать [convMutex].
     *
     * @since 0.2
     */
    private fun clearActiveConversation() {
        val conversation = activeConversation
        activeConversation = null
        activeConversationSystemInstruction = null
        runCatching { conversation?.close() }
    }

    /**
     * Проверяет request для one-shot model call.
     *
     * Stateless путь принимает router-запросы и voice transcription. Chat
     * отклоняется здесь, чтобы обычная переписка не потеряла историю из-за
     * случайного вызова [askStateless].
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    private fun validateStateless(request: KsenaxModelRequest) {
        requireSystemInstruction(request)

        when (request.profile) {
            KsenaxModelTaskProfile.ROUTER -> {
                requirePrompt(request)
                requireNoVoiceMessage(request)
            }

            KsenaxModelTaskProfile.VOICE_TRANSCRIPTION -> {
                requirePrompt(request)
                requireVoiceMessage(request)
            }

            KsenaxModelTaskProfile.CHAT -> {
                error("CHAT profile must use askPersistent(), not askStateless().")
            }
        }
    }

    /**
     * Проверяет request для persistent conversation.
     *
     * Persistent путь принимает только [KsenaxModelTaskProfile.CHAT].
     * Audio payload и router-запросы должны идти через stateless API, иначе
     * chat history начнёт смешиваться с техническими prompt-контрактами.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    private fun validatePersistent(request: KsenaxModelRequest) {
        require(request.profile == KsenaxModelTaskProfile.CHAT) {
            "Only CHAT profile can use askPersistent(). Actual profile: ${request.profile}."
        }
        requireSystemInstruction(request)
        requirePrompt(request)
        requireNoVoiceMessage(request)
    }

    /**
     * Проверяет system instruction в model request.
     *
     * Пустая instruction ломает разделение режимов: router теряет контракт
     * ответа, chat теряет роль ассистента, voice transcription теряет язык и
     * формат результата.
     *
     * @since 0.2
     */
    private fun requireSystemInstruction(request: KsenaxModelRequest) {
        require(request.systemInstruction.isNotBlank()) {
            "${request.profile} request systemInstruction must not be blank."
        }
    }

    /**
     * Проверяет user/model prompt в request.
     *
     * Для router-а это полный prompt-контракт. Для chat-а это сообщение
     * пользователя. Для voice transcription это инструкция к аудио.
     *
     * @since 0.2
     */
    private fun requirePrompt(request: KsenaxModelRequest) {
        require(request.prompt.isNotBlank()) {
            "${request.profile} request prompt must not be blank."
        }
    }

    /**
     * Запрещает audio payload там, где session ждёт только текст.
     *
     * Router и chat не должны принимать [KsenaxVoiceMessage]. Голос сначала
     * проходит через [transcribe], а дальше в систему уходит обычный текст.
     *
     * @since 0.2
     */
    private fun requireNoVoiceMessage(request: KsenaxModelRequest) {
        require(request.voiceMessage == null) {
            "${request.profile} request must not contain voiceMessage."
        }
    }

    /**
     * Проверяет audio payload для voice transcription.
     *
     * Модель получает файл через LiteRT-LM [Content.AudioFile], поэтому файл
     * должен существовать, читаться и содержать байты.
     *
     * @since 0.2
     */
    private fun requireVoiceMessage(request: KsenaxModelRequest) {
        check(capabilities.supportsAudioInput) {
            "VOICE_TRANSCRIPTION requires an enabled audio backend."
        }
        val voiceMessage = requireNotNull(request.voiceMessage) {
            "VOICE_TRANSCRIPTION request must contain voiceMessage."
        }
        require(voiceMessage.file.isFile) {
            "VOICE_TRANSCRIPTION voiceMessage file must exist: ${voiceMessage.file.absolutePath}"
        }
        require(voiceMessage.file.canRead()) {
            "VOICE_TRANSCRIPTION voiceMessage file must be readable: ${voiceMessage.file.absolutePath}"
        }
        require(voiceMessage.file.length() > 0L) {
            "VOICE_TRANSCRIPTION voiceMessage file must not be empty: ${voiceMessage.file.absolutePath}"
        }
        voiceMessage.sampleRateHz?.let { sampleRateHz ->
            require(sampleRateHz > 0) {
                "VOICE_TRANSCRIPTION sampleRateHz must be positive."
            }
        }
        voiceMessage.channelCount?.let { channelCount ->
            require(channelCount > 0) {
                "VOICE_TRANSCRIPTION channelCount must be positive."
            }
        }
        voiceMessage.durationMillis?.let { durationMillis ->
            require(durationMillis > 0L) {
                "VOICE_TRANSCRIPTION durationMillis must be positive."
            }
        }
    }

    private fun KsenaxLiteRtAudioBackend.toLiteRtBackend(): Backend =
        when (this) {
            KsenaxLiteRtAudioBackend.CPU -> Backend.CPU()
        }
}
