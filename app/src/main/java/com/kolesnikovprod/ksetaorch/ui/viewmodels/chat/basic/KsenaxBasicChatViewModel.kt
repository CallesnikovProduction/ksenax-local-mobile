package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxBasicChatCoordinator
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxBasicChatEvent
import com.kolesnikovprod.ksetaorch.storage.chat.domain.KsenaxChatRepository
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChat
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChatMode
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredMessage
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxGemmaIntegrityController
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxGemmaVerificationResult
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxGemmaVerificationStage
import com.kolesnikovprod.ksetaorch.ui.main.model.toPresentationChat
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.appendAssistantDelta
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onActiveChatCleared
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onActiveSelected
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onFinalizeGeneration
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onGenerationErrored
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onGenerationStarted
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onModelGateFailed
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onUserMsgPersistErrored
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.onVerificationCancelled
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal.toCoordinatorHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val CHAT_TITLE_MAX_LENGTH                 = 32
private const val ACTIVE_CHAT_ID_STATE_KEY              = "basic_chat_active_chat_id"
private const val UX_MODEL_PREPARED_CONFIRMATION_MILLIS = 250L

/**
 * Presentation-оркестратор экрана обычного Basic-чата с локальной моделью.
 *
 * ViewModel связывает Compose UI, сохранённую историю чатов, model gate и
 * streaming-координатор обычного диалога. Она не выполняет инференс сама и не
 * работает напрямую с DAO: генерация делегируется [KsenaxBasicChatCoordinator],
 * хранение истории — [KsenaxChatRepository], а проверка файла модели —
 * [KsenaxGemmaIntegrityController].
 *
 * Основные обязанности:
 * - держать [KsenaxBasicChatUiState] и одноразовые [KsenaxBasicChatEffect];
 * - восстанавливать и сохранять `activeChatId` через [SavedStateHandle];
 * - принимать пользовательский ввод, включая initial message и voice transcription;
 * - проверять наличие/целостность Gemma перед первой генерацией;
 * - подготавливать coordinator и сбрасывать runtime conversation при смене чата;
 * - сохранять user/assistant messages в repository;
 * - собирать streaming delta-события в transient UI-buffer;
 * - сохранять partial assistant response при пользовательской остановке,
 *   ошибке генерации или уничтожении ViewModel;
 * - безопасно обрабатывать выбор, удаление и выход из активного чата.
 *
 * Важная архитектурная граница: repository остаётся источником истины для
 * истории диалога, а [KsenaxBasicChatCoordinator] используется как временный
 * runtime-контур генерации. При каждом новом turn-е история заново собирается
 * из сохранённых сообщений активного чата и передаётся coordinator-у явно.
 *
 * ViewModel намеренно содержит только Basic Chat-сценарий: без tool-calling,
 * policy engine и Android action execution. Agent/tool режимы должны жить в
 * отдельном orchestration-контуре, чтобы обычный чат не смешивался с логикой
 * исполнения действий на устройстве.
 *
 * @param initialChatId стартовый идентификатор Basic-чата из навигационного
 * контекста, либо null, если экран открывается без выбранного чата.
 * @param savedStateHandle handle для восстановления `activeChatId` после
 * пересоздания ViewModel.
 * @param chatRepository repository-контракт для чтения списка чатов и
 * сохранения user/assistant сообщений.
 * @param chatCoordinator coordinator обычного локального диалога с моделью.
 * Отвечает за подготовку runtime, сброс conversation и streaming ответа.
 * @param integrityController controller проверки наличия и целостности файла
 * локальной Gemma перед запуском coordinator-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxBasicChatViewModel(
    private val initialChatId:       Long?,
    private val savedStateHandle:    SavedStateHandle,
    private val chatRepository:      KsenaxChatRepository,
    private val chatCoordinator:     KsenaxBasicChatCoordinator,
    private val integrityController: KsenaxGemmaIntegrityController,
    private val modelTitle:          String,
) : ViewModel() {

    /*
     * ╔════════════════════════════════════════════╗
     * ║           STATE / EFFECTS ZONE:            ║
     * ╠════════════════════════════════════════════╣
     * ║ Отвечает на два вопроса:                   ║
     * ║        1. Какое состояние экрана сейчас?   ║
     * ║        2. Какие одноразовые действия экран ║
     * ║           должен выполнять?                ║
     * ╚════════════════════════════════════════════╝
     */

    /**
     * Внутреннее изменяемое состояние экрана.
     *
     * [MutableStateFlow] — поток состояния, у которого всегда есть последнее
     * значение, на которое может подписаться UI и получать актуальное состояние.
     *
     * Закрытое свойство, чтобы другие слои не смогли менять состояние как попало.
     */
    private val mutableUiState = MutableStateFlow(
        KsenaxBasicChatUiState(
            activeChatId = savedStateHandle[ACTIVE_CHAT_ID_STATE_KEY] ?: initialChatId,
        ),
    )

    /**
     * Публичная read-only версия состояния, которая приходит от [mutableUiState].
     *
     * @since 0.2
     */
    val uiState = mutableUiState.asStateFlow()

    /**
     * Канал для одноразовых эффектов (команда UI что-то сделать одинажды).
     *
     * Выбрана ёмкость [Channel.BUFFERED], потому что UI в текущую миллисекунду не
     * готов принять эффект от [ViewModel]. Тогда канал может временно удержать событие.
     */
    private val effectChannel = Channel<KsenaxBasicChatEffect>(Channel.BUFFERED)

    /**
     * Публичный поток эффектов для UI.
     *
     * Снаружи можно только `collect`.
     *
     * @since 0.2
     * @see effectChannel
     */
    val effects = effectChannel.receiveAsFlow()


    /*
     * ╔════════════════════════════════════════════╗
     * ║            RUNTIME FLAGS ZONE:             ║
     * ╠════════════════════════════════════════════╣
     * ║  Флаги, которые регулируют жизнь асинхрон- ║
     * ║   ных процессов, которые нельзя выразить   ║
     * ║              через uiState.                ║
     * ╚════════════════════════════════════════════╝
     */

    /**
     * Локальный кэш последнего списка чатов из репозитория.
     */
    private var latestStoredChats: List<KsenaxStoredChat> = emptyList()

    /**
     * Корутинная работа валидации файла модели.
     */
    private var verificationJob: Job? = null

    /**
     * Корутинная работа текущей генерации ответа модели (ручка управления текущим
     * streaming-запросов к модели).
     */
    private var generationJob: Job? = null

    /**
     * Защитный флаг от повторного выполнения [onEnter].
     *
     * Если не защититься, то можно случайно отправить стартовое сообщение дважды.
     */
    private var initialEntryHandled = false

    /**
     * Флаг, имеющий смысл: «если генерация была отменена, то нужно ли будет
     * сохранять уже полученный частичный ответ?»
     *
     * Потенциально опасный флаг для дальнейшей работы (если будет система retry-ев).
     */
    private var shouldPersistCancelledGeneration = false

    /**
     * Когда пользователь хочет выйти/создать новый чат во время генерации, то
     * нужно сначала **остановить** генерацию, сохранить частичный ответ, а потом
     * уже отправить [KsenaxBasicChatEffect.ExitToMain]
     */
    private var exitAfterGenerationStops = false

    /**
     * Флаг для удаления активного чата во время генерации.
     *
     * Пайплайн:
     * 1. Запоминается id чата, который нужно удалить
     * 2. Остановка генерации
     * 3. В `finally` блоке выполняется удаление
     */
    private var deleteAfterGenerationStops: Long? = null


    /*
     * ╔════════════════════════════════════════════╗
     * ║       REPOSITORY OBSERVATION ZONE:         ║
     * ╚════════════════════════════════════════════╝
     */

    init {
        viewModelScope.launch {
            // Каждый раз, когда репозиторий меняется по спискам чатов, то сюда
            // приходит свежий chats.
            chatRepository.chats.collect { chats ->
                latestStoredChats = chats // локальный снапшот последнего состояния репо


                val basicChats = chats
                    .filter { chat -> chat.mode == KsenaxStoredChatMode.Basic }
                    .map(KsenaxStoredChat::toPresentationChat)

                mutableUiState.update { state ->
                    val activeStoredChat = chats.firstOrNull { chat ->
                        chat.id == state.activeChatId
                    }

                    state
                        .copy(chats = basicChats)
                        .reconcileWithStoredChat(activeStoredChat)
                }
            }
        }
    }

    private fun KsenaxBasicChatUiState.reconcileWithStoredChat(
        activeStoredChat: KsenaxStoredChat?,
    ): KsenaxBasicChatUiState {
        val transientWasPersisted = transientUserText != null &&
                activeStoredChat?.messages?.lastOrNull { message ->
                    message.role == KsenaxMessageRole.User
                }?.text == transientUserText

        val streamedResponseWasPersisted =
            streamingAssistantText.isNotEmpty() &&
                    activeStoredChat?.messages?.lastOrNull { message ->
                        message.role == KsenaxMessageRole.Assistant
                    }?.let { message ->
                        message.text == streamingAssistantText &&
                                message.generationDurationMillis == generationDurationMillis
                    } == true

        return copy(
            transientUserText        =
                if (transientWasPersisted) null        else transientUserText,
            streamingAssistantText   =
                if (streamedResponseWasPersisted) ""   else streamingAssistantText,
            generationDurationMillis =
                if (streamedResponseWasPersisted) null else generationDurationMillis,
        )
    }


    /*
     * ╔════════════════════════════════════════════╗
     * ║         ENTRY & MODEL GATE ZONE            ║
     * ╚════════════════════════════════════════════╝
     */

    /**
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun onEnter(initialMessage: String?) {
        if (initialEntryHandled) return

        initialEntryHandled = true

        // Убирание пробелов
        val normalizedMessage = initialMessage?.trim().orEmpty()

        if (normalizedMessage.isNotEmpty()) {
            mutableUiState.update { state ->
                // сохраняется transient (временное) сообщение
                state.copy(transientUserText = normalizedMessage)
            }
            // Запуск проверки модели
            startModelVerification(
                messageText      = normalizedMessage,
                isInitialMessage = true,
            )
        }
    }

    /**
     * Метод, который принимает сообщение, которое написал пользователь.
     * Его нужно отправить после успешной проверки.
     */
    private fun startModelVerification(
        messageText:      String,
        isInitialMessage: Boolean,
    ) {
        verificationJob?.cancel() // отмена предыдущей проверки
        verificationJob = viewModelScope.launch {
            val result = integrityController.verifyOnce { stage ->
                mutableUiState.update { state ->
                    state.copy(
                        modelGateState = stage.toBasicModelGateState()
                    )
                }
            }

            when (result) {
                KsenaxGemmaVerificationResult.Missing -> {
                    showGateFailure(
                        message = "Файл $modelTitle не найден. Установи модель заново.",
                        stage   = KsenaxBasicModelFailureStage.Presence,
                    )
                }

                KsenaxGemmaVerificationResult.Invalid -> {
                    showGateFailure(
                        message = "$modelTitle не прошла проверку SHA-256.",
                        stage   = KsenaxBasicModelFailureStage.Integrity,
                    )
                }

                KsenaxGemmaVerificationResult.Valid -> {
                    mutableUiState.update { state ->
                        state.copy(modelGateState = KsenaxBasicModelGateState.PreparingModel)
                    }

                    runCatching {
                        // Обязательный сброс предыдущей истории
                        chatCoordinator.resetConversation()
                        chatCoordinator.prepare()
                    }.onSuccess {
                        mutableUiState.update { state ->
                            state.copy(
                                modelGateState = KsenaxBasicModelGateState.ModelPrepared,
                            )
                        }

                        delay(UX_MODEL_PREPARED_CONFIRMATION_MILLIS.milliseconds)

                        mutableUiState.update { state ->
                            state.copy(modelGateState = KsenaxBasicModelGateState.Ready)
                        }
                        sendMessage(
                            messageText      = messageText,
                            isInitialMessage = isInitialMessage,
                        )
                    }.onFailure { error ->
                        // если корутину отменили, то нужно дать дорогу отмене наверх
                        if (error is CancellationException) throw error
                        showGateFailure(
                            message =
                                error.message ?: "Не удалось запустить локальную модель.",
                            stage   = KsenaxBasicModelFailureStage.Preparation,
                        )
                    }
                }
            }
        }
    }

    private fun KsenaxGemmaVerificationStage.toBasicModelGateState():
            KsenaxBasicModelGateState {
        return when (this) {
            KsenaxGemmaVerificationStage.CheckingPresence ->
                KsenaxBasicModelGateState.CheckingPresence

            KsenaxGemmaVerificationStage.CheckingIntegrity ->
                KsenaxBasicModelGateState.CheckingIntegrity
        }
    }


    /*
     * ╔════════════════════════════════════════════╗
     * ║             USER  INPUT  ZONE              ║
     * ╚════════════════════════════════════════════╝
     */

    /**
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun onInputTextChanged(value: String) {
        mutableUiState.update { state -> state.copy(inputText = value) }
    }

    /**
     * Склейка расшифрованного голосового в набранный текст.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun onVoiceTranscribed(transcription: String) {
        mutableUiState.update { state ->
            state.copy(inputText = appendTranscription(
                existInput    = state.inputText,
                transcription = transcription
            ))
        }
    }

    private fun appendTranscription(
        existInput:    String,
        transcription: String
    ): String {
        val normalized = transcription.trim() // чистка
        if (normalized.isEmpty()) return existInput

        val separator = when {
            existInput.isEmpty()             -> ""
            existInput.last().isWhitespace() -> ""
            else                                  -> " "
        }

        return existInput + separator + normalized
    }

    /**
     * Маршрутизатор пользовательского намерения отправки (кнопка «отправить»)
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun onSendClick() {
        val state = mutableUiState.value
        val messageText = state.inputText.trim()

        if (!state.canSubmit(messageText)) return

        mutableUiState.update { current ->
            // Сообщение должно быть показано в
            current.copy(transientUserText = messageText)
        }
        when (state.modelGateState) {
            KsenaxBasicModelGateState.Idle -> {
                startModelVerification(
                    messageText      = messageText,
                    isInitialMessage = false,
                )
            }

            KsenaxBasicModelGateState.Ready -> {
                sendMessage(messageText, isInitialMessage = false)
            }

            // это перебор, но синтаксис Kotlin не даёт игнорировать и убрать это.
            //
            KsenaxBasicModelGateState.CheckingPresence,
            KsenaxBasicModelGateState.CheckingIntegrity,
            KsenaxBasicModelGateState.PreparingModel,
            KsenaxBasicModelGateState.ModelPrepared,
            is KsenaxBasicModelGateState.Failure -> Unit
        }
    }

    /**
     * Вспомогательная функция, которая проверяет:
     * - [messageText] — непустой ли?
     * - не в состоянии генерации?
     * - не в блокировке по [KsenaxBasicModelGateState]?
     *
     * @since 0.2
     */
    private fun KsenaxBasicChatUiState.canSubmit(
        messageText: String
    ): Boolean {
        return messageText.isNotEmpty() && !isGenerating && !modelGateState.isBlockingSubmission
    }

    private val KsenaxBasicModelGateState.isBlockingSubmission: Boolean
        get() = when (this) {
            KsenaxBasicModelGateState.Idle,
            KsenaxBasicModelGateState.Ready -> false
            else -> true
        }

    /**
     * Основной метод, отправляющий сообщение реально в модель.
     *
     * Работает по следующему ходу диалога:
     * 1. Собирает историю диалога
     * 2. Сохраняет сообщение пользователя
     * 3. Запускает live-генерацию (streaming)
     * 4. Накапливает частичную генерацию
     * 5. Обрабатывает отмену / ошибку / частичный ответ
     * 6. Завершает UI-состояние
     * 7. Выполняет отложенный выход / удаление
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    private fun sendMessage(
        messageText:      String,
        isInitialMessage: Boolean,
    ) {
        // Отмена предыдущей генерации
        generationJob?.cancel()
        // сброс флага
        shouldPersistCancelledGeneration = false
        generationJob = viewModelScope.launch {
            val historyBeforeTurn = activeStoredChat()
                ?.messages
                .orEmpty()
                .toCoordinatorHistory()

            try {
                val chatId = persistUserMessage(messageText)
                savedStateHandle[ACTIVE_CHAT_ID_STATE_KEY] = chatId
                mutableUiState.update { state ->
                    state.onGenerationStarted(chatId)
                }

                // Если со старта кидается новое сообщение
                if (isInitialMessage) {
                    effectChannel.send(
                        KsenaxBasicChatEffect.InitialMessageCommitted(messageText),
                    )
                }

                // Не зависит от изменения системных часов.
                val startedAtMillis = SystemClock.elapsedRealtime()

                try {
                    chatCoordinator.streamReply(
                        userText = messageText,
                        history  = historyBeforeTurn,
                    ).collect { event ->
                        when (event) {
                            is KsenaxBasicChatEvent.TextDelta -> {
                                mutableUiState.update { state ->
                                    // каждый кусочек добавляется в конец...
                                    state.appendAssistantDelta(event.text)
                                }
                            }

                            is KsenaxBasicChatEvent.Completed -> {
                                val finalText = event.text.ifBlank {
                                    mutableUiState.value.streamingAssistantText
                                }
                                persistAssistantMessage(
                                    chatId                   = chatId,
                                    text                     = finalText,
                                    generationDurationMillis = event.latencyMs,
                                )
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    if (shouldPersistCancelledGeneration) {
                        persistCancelledAssistantMessage(chatId, startedAtMillis)
                    }
                } catch (error: Exception) {
                    val partialText = mutableUiState.value.streamingAssistantText
                    if (partialText.isNotBlank()) {
                        withContext(NonCancellable) {
                            persistAssistantMessage(
                                chatId                   = chatId,
                                text                     = partialText,
                                generationDurationMillis =
                                    SystemClock.elapsedRealtime() - startedAtMillis,
                            )
                        }
                    }
                    mutableUiState.update { state ->
                        state.onGenerationErrored(error)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableUiState.update { state ->
                    state.onUserMsgPersistErrored(error)
                }
            } finally {
                shouldPersistCancelledGeneration = false
                mutableUiState.update { state ->
                    state.onFinalizeGeneration()
                }
                if (exitAfterGenerationStops) {
                    exitAfterGenerationStops = false
                    effectChannel.trySend(KsenaxBasicChatEffect.ExitToMain)
                }
                deleteAfterGenerationStops?.let { chatId ->
                    deleteAfterGenerationStops = null
                    clearDeletedActiveChat(chatId)
                    effectChannel.trySend(
                        KsenaxBasicChatEffect.DeleteChat(
                            chatId       = chatId,
                            returnToMain = true,
                        ),
                    )
                }
            }
        }
    }

    private fun activeStoredChat(): KsenaxStoredChat? {
        val activeChatId = mutableUiState.value.activeChatId ?: return null
        return latestStoredChats.firstOrNull { chat -> chat.id == activeChatId }
    }

    fun onStopGeneration() {
        if (generationJob?.isActive != true) return
        shouldPersistCancelledGeneration = true // сохранение сгенерированного УЖЕ
        generationJob?.cancel()
    }

    // persist - сохранить
    private suspend fun persistUserMessage(messageText: String): Long {
        val now = System.currentTimeMillis()
        val activeChatId = mutableUiState.value.activeChatId
        val userMessage = KsenaxStoredMessage(
            role                 = KsenaxMessageRole.User,
            text                 = messageText,
            createdAtEpochMillis = now,
        )

        return if (activeChatId == null) {
            chatRepository.saveChat(
                KsenaxStoredChat(
                    mode                 = KsenaxStoredChatMode.Basic,
                    title                = messageText.toChatTitle(),
                    messages             = listOf(userMessage),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        } else {
            chatRepository.appendMessage(activeChatId, userMessage)
            activeChatId
        }
    }

    // persist - сохранить
    private suspend fun persistAssistantMessage(
        chatId:                   Long,
        text:                     String,
        generationDurationMillis: Long,
    ) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return

        mutableUiState.update { state ->
            state.copy(
                streamingAssistantText   = normalizedText,
                generationDurationMillis = generationDurationMillis,
            )
        }

        chatRepository.appendMessage(
            chatId  = chatId,
            message = KsenaxStoredMessage(
                role                     = KsenaxMessageRole.Assistant,
                text                     = normalizedText,
                createdAtEpochMillis     = System.currentTimeMillis(),
                generationDurationMillis = generationDurationMillis,
            ),
        )
    }

    private suspend fun persistCancelledAssistantMessage(
        chatId: Long,
        startedAtMillis: Long,
    ) {
        withContext(NonCancellable) {
            persistAssistantMessage(
                chatId = chatId,
                text = mutableUiState.value.streamingAssistantText,
                generationDurationMillis =
                    SystemClock.elapsedRealtime() - startedAtMillis,
            )
        }
    }


    /*
     * ╔══════════════════════════════════════════════════════════════╗
     * ║  CHAT SELECTION / EXIT / DELETE / VERIFICATION CANCELLATION  ║
     * ╚══════════════════════════════════════════════════════════════╝
     */

    /**
     * Выбирает существующий Basic-чат как активный для текущего экрана.
     *
     * Переключение блокируется во время генерации, потому что streaming-ответ
     * относится к текущему активному чату и не должен визуально или логически
     * смешиваться с другим диалогом. При успешном выборе очищает transient/streaming
     * состояние через reducer, сохраняет новый activeChatId в [SavedStateHandle]
     * и сбрасывает runtime-conversation coordinator-а.
     *
     * История диалога не хранится в coordinator-е как source of truth: при следующей
     * отправке сообщения она будет заново собрана из repository по активному чату.
     *
     * @param chatId идентификатор сохранённого Basic-чата, который нужно открыть.
     *
     * @since 0.2
     */
    fun onChatSelected(chatId: Long) {
        if (mutableUiState.value.isGenerating) return
        mutableUiState.update { state ->
            state.onActiveSelected(chatId)
        }

        // Новый активный чат сохраняется в SavedStateHandle, чтобы
        // после пересоздания ViewModel экран смог бы восстановить выбранный чат.
        savedStateHandle[ACTIVE_CHAT_ID_STATE_KEY] = chatId
        viewModelScope.launch {
            chatCoordinator.resetConversation()
        }
    }

    /**
     * Обрабатывает запрос пользователя начать новый Basic-чат.
     *
     * Если генерация не активна, отправляет одноразовый эффект выхода на главный
     * экран, где пользователь сможет создать новый диалог. Если генерация идёт,
     * выход откладывается: ViewModel сначала просит остановить генерацию через
     * [onStopGeneration], чтобы сохранить частичный ответ модели, а затем выполнит
     * [KsenaxBasicChatEffect.ExitToMain] в финализации generation job.
     *
     * Такой порядок защищает от потери partial response и от изменения экранного
     * контекста посреди streaming-сессии.
     *
     * @since 0.2
     */
    fun onNewChatClick() {
        if (mutableUiState.value.isGenerating) {
            exitAfterGenerationStops = true
            onStopGeneration()
            return
        }
        effectChannel.trySend(KsenaxBasicChatEffect.ExitToMain)
    }

    /**
     * Обрабатывает пользовательский запрос на удаление Basic-чата.
     *
     * Если удаляется активный чат во время генерации, удаление откладывается:
     * ViewModel сначала останавливает streaming, сохраняет частичный ответ модели
     * при необходимости, а затем в финализации generation job очищает активный чат
     * и отправляет эффект удаления.
     *
     * Если удаляется активный чат без генерации, локальное состояние экрана сразу
     * очищается через [clearDeletedActiveChat], чтобы не держать ссылку на уже
     * удаляемый диалог. Если удаляется неактивный чат, текущий экран не очищается.
     *
     * Само удаление передаётся наружу через [KsenaxBasicChatEffect.DeleteChat],
     * потому что удаление связано не только с repository-действием, но и с
     * навигационным решением: нужно ли возвращаться на главный экран.
     *
     * @param chatId идентификатор чата, который пользователь запросил удалить.
     *
     * @since 0.2
     */
    fun onDeleteChatRequested(chatId: Long) {
        val isActiveChat = mutableUiState.value.activeChatId == chatId

        if (isActiveChat && mutableUiState.value.isGenerating) {
            deleteAfterGenerationStops = chatId
            onStopGeneration()
            return
        }

        if (isActiveChat) {
            clearDeletedActiveChat(chatId)
        }
        effectChannel.trySend(
            KsenaxBasicChatEffect.DeleteChat(
                chatId       = chatId,
                returnToMain = isActiveChat, // true/false по активному экрану
            ),
        )
    }

    /**
     * Очищает локальное состояние экрана, если удаляемый чат является активным.
     *
     * Метод защищён проверкой `activeChatId`: если запрошенный [chatId] не совпадает
     * с текущим активным чатом, состояние экрана не изменяется. При совпадении
     * `activeChatId` удаляется из [SavedStateHandle], а UI-состояние переводится
     * в режим без выбранного чата: очищаются transient-сообщения, streaming-буфер,
     * длительность генерации, флаг генерации и последняя ошибка.
     *
     * Используется перед отправкой эффекта удаления активного чата, чтобы экран
     * не держал ссылку на диалог, который будет удалён из repository.
     *
     * @param chatId идентификатор удаляемого чата.
     *
     * @since 0.2
     */
    private fun clearDeletedActiveChat(chatId: Long) {
        if (mutableUiState.value.activeChatId != chatId) return

        savedStateHandle.remove<Long>(ACTIVE_CHAT_ID_STATE_KEY)
        mutableUiState.update { state ->
            state.onActiveChatCleared()
        }
    }


    /*
     * ╔═════════════════════════════════════════════╗
     * ║          VERIFICATION CANCELLATION          ║
     * ╚═════════════════════════════════════════════╝
     */

    /**
     * Отменяет текущую проверку/подготовку модели и возвращает пользователя
     * на главный экран.
     *
     * Используется, когда пользователь не хочет ждать model gate: проверку наличия
     * файла, SHA-256 integrity-check или подготовку локального coordinator-а.
     * Активная verification job отменяется, временный текст стартового сообщения
     * очищается, состояние gate возвращается в [KsenaxBasicModelGateState.Idle],
     * после чего UI получает одноразовый эффект [KsenaxBasicChatEffect.ExitToMain].
     *
     * Метод не трогает generationJob: он относится именно к фазе verification /
     * preparation, то есть до запуска генерации ответа.
     *
     * @since 0.2
     */
    fun onCancelVerification() {
        verificationJob?.cancel()
        mutableUiState.update { state ->
            state.onVerificationCancelled()
        }
        effectChannel.trySend(KsenaxBasicChatEffect.ExitToMain)
    }



    /**
     * Переводит model gate в состояние отказа и публикует текст ошибки в UI.
     *
     * Используется, когда локальная модель не может быть допущена к генерации:
     * файл отсутствует, integrity-check завершился неуспешно или coordinator
     * не смог подготовить runtime модели. [stage] сохраняет точку отказа, чтобы UI
     * мог показать более точное состояние и действие восстановления.
     *
     * Метод не завершает экран и не отменяет генерацию сам по себе: он только
     * фиксирует failure-state. Навигационное решение остаётся за UI/пользователем.
     *
     * @param message пользовательский текст ошибки.
     * @param stage стадия model gate, на которой произошёл отказ.
     *
     * @since 0.2
     */
    private fun showGateFailure(
        message: String,
        stage:   KsenaxBasicModelFailureStage,
    ) {
        mutableUiState.update { state ->
            state.onModelGateFailed(message, stage)
        }
    }

    /**
     * Завершает активную генерацию при уничтожении ViewModel.
     *
     * Если streaming job ещё активна, помечает отмену как сохраняемую и отменяет
     * generation coroutine. Дальнейшее сохранение partial assistant response
     * выполняется внутри generation lifecycle: catch-блок для [CancellationException]
     * увидит [shouldPersistCancelledGeneration] и выполнит persist в
     * [NonCancellable]-контексте.
     *
     * Это защищает пользователя от потери уже сгенерированного текста при уходе
     * с экрана или уничтожении ViewModel scope.
     *
     * @since 0.2
     */
    override fun onCleared() {
        if (generationJob?.isActive == true) {
            shouldPersistCancelledGeneration = true
            generationJob?.cancel()
        }
        super.onCleared()
    }

    /**
     * Фабрика для создания [KsenaxBasicChatViewModel] с runtime-зависимостями
     * из [KsenaxAndroidApplication] и навигационным `initialChatId`.
     *
     * Нужна, потому что ViewModel имеет собственный конструктор с repository,
     * chat coordinator, integrity controller и [SavedStateHandle]. Стандартный
     * `ViewModelProvider` не может создать такую ViewModel без явной фабрики.
     *
     * [initialChatId] приходит из навигационного контекста и задаёт чат, который
     * должен быть открыт при первом создании экрана. [SavedStateHandle] создаётся
     * из `CreationExtras`, чтобы `activeChatId` мог переживать пересоздание экрана.
     *
     * @param application application-level composition root, из которого берутся
     * долгоживущие зависимости Basic Chat контура.
     * @param initialChatId идентификатор чата, который надо открыть изначально,
     * или null, если экран стартует без выбранного чата.
     *
     * @since 0.2
     */
    class Factory(
        private val application:   KsenaxAndroidApplication,
        private val initialChatId: Long?,
        private val responseModel: KsenaxSupportedTextModel =
            KsenaxSupportedTextModel.Gemma,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras:     androidx.lifecycle.viewmodel.CreationExtras,
        ): T {
            require(modelClass.isAssignableFrom(KsenaxBasicChatViewModel::class.java))
            val chatCoordinator = when (responseModel) {
                KsenaxSupportedTextModel.Gemma ->
                    application.basicChatCoordinator
                KsenaxSupportedTextModel.FunctionGemma ->
                    application.functionGemmaBasicChatCoordinator
            }
            val integrityController = when (responseModel) {
                KsenaxSupportedTextModel.Gemma ->
                    application.gemmaIntegrityController
                KsenaxSupportedTextModel.FunctionGemma ->
                    application.functionGemmaIntegrityController
            }
            return KsenaxBasicChatViewModel(
                initialChatId       = initialChatId,
                savedStateHandle    = extras.createSavedStateHandle(),
                chatRepository      = application.chatRepository,
                chatCoordinator     = chatCoordinator,
                integrityController = integrityController,
                modelTitle          = responseModel.title,
            ) as T
        }

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            error("KsenaxBasicChatViewModel requires CreationExtras.")
        }
    }
}

private fun String.toChatTitle(): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    return normalized.take(CHAT_TITLE_MAX_LENGTH)
}
