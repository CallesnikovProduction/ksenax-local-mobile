package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnRuntime
import com.kolesnikovprod.ksetaorch.storage.chat.domain.KsenaxChatRepository
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChat
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChatMode
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredMessage
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxAgentRuntimeController
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxGemmaVerificationResult
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxGemmaVerificationStage
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxModelIntegrityVerifier
import com.kolesnikovprod.ksetaorch.ui.main.model.toPresentationChat
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.KSENAX_MODEL_VERIFICATION_SUCCESS_HOLD_MILLIS
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState
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

private const val ChatTitleMaxLength = 48
private const val ActiveChatIdStateKey = "agentic_chat_active_chat_id"

/**
 * Presentation-контур реального агентного диалога.
 *
 * ViewModel хранит чат и шаги выполнения в Room, проверяет обязательные модели,
 * собирает agent runtime для сохранённой SAF-директории либо default
 * Documents-workspace и управляет отменой turn-а.
 *
 * Навигация передаёт только identity чата и стартовый workspace. Выбор и сборка
 * моделей агентного pipeline принадлежат application-level runtime controller,
 * поэтому ViewModel не принимает UI-настройку response-модели.
 *
 * @param initialChatId чат, который следует восстановить, или `null` для нового.
 * @param initialWorkspaceTreeUri сохранённый SAF tree URI нового чата.
 * @param initialWorkspaceDisplayPath отображаемый путь нового workspace.
 * @param savedStateHandle сохраняет identity активного чата при пересоздании UI.
 * @param chatRepository источник Room-backed истории.
 * @param workspaceController собирает runtime для workspace активного чата.
 * @param integrityController проверяет обязательные модели agentic pipeline.
 * @param modelTitle отображаемое имя набора моделей.
 *
 * @since 0.4
 * @author Stephan Kolesnikov
 */
class KsenaxAgenticChatViewModel(
    private val initialChatId: Long?,
    private val initialWorkspaceTreeUri: String?,
    private val initialWorkspaceDisplayPath: String,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: KsenaxChatRepository,
    private val workspaceController: KsenaxAgentRuntimeController,
    private val integrityController: KsenaxModelIntegrityVerifier,
    val modelTitle: String,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(
        KsenaxAgenticChatUiState(
            activeChatId = savedStateHandle[ActiveChatIdStateKey] ?: initialChatId,
            workspaceTreeUri = initialWorkspaceTreeUri,
            workspaceDisplayPath = initialWorkspaceDisplayPath,
        ),
    )
    val uiState = mutableUiState.asStateFlow()

    private val effectChannel = Channel<KsenaxAgenticChatEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var latestStoredChats: List<KsenaxStoredChat> = emptyList()
    private var coordinator: KsenaxAgentTurnRuntime? = null
    private var verificationJob: Job? = null
    private var turnJob: Job? = null
    private var hasEntered = false
    private var exitAfterTurnStops = false
    private var deleteAfterTurnStops: Long? = null

    init {
        viewModelScope.launch {
            chatRepository.chats.collect { chats ->
                latestStoredChats = chats
                val state = mutableUiState.value
                val activeStoredChat = chats.firstOrNull { it.id == state.activeChatId }
                val transientWasPersisted = state.transientUserText != null &&
                    activeStoredChat?.messages?.lastOrNull { it.role == KsenaxMessageRole.User }
                        ?.text == state.transientUserText

                mutableUiState.update { current ->
                    current.copy(
                        chats = chats
                            .filter { it.mode == KsenaxStoredChatMode.Agentic }
                            .map(KsenaxStoredChat::toPresentationChat),
                        workspaceTreeUri =
                            if (activeStoredChat != null) {
                                activeStoredChat.workspaceTreeUri
                            } else {
                                current.workspaceTreeUri
                            },
                        workspaceDisplayPath =
                            activeStoredChat?.workspaceDisplayPath
                                ?: current.workspaceDisplayPath,
                        transientUserText =
                            if (transientWasPersisted) null else current.transientUserText,
                    )
                }
            }
        }
    }

    /**
     * Однократно принимает initial message из navigation-state и запускает
     * model gate до создания нового чата.
     *
     * @since 0.4
     */
    fun onEnter(initialMessage: String?) {
        if (hasEntered) return
        hasEntered = true

        val normalizedMessage = initialMessage?.trim().orEmpty()
        if (normalizedMessage.isNotEmpty()) {
            mutableUiState.update { it.copy(transientUserText = normalizedMessage) }
            startModelVerification(normalizedMessage, isInitialMessage = true)
        }
    }

    /**
     * Обновляет draft пользовательского сообщения.
     *
     * @since 0.4
     */
    fun onInputTextChanged(value: String) {
        mutableUiState.update { it.copy(inputText = value) }
    }

    /**
     * Дописывает результат транскрипции в конец текущего draft.
     *
     * @since 0.4
     */
    fun onVoiceTranscribed(transcription: String) {
        val normalized = transcription.trim()
        if (normalized.isEmpty()) return

        mutableUiState.update { state ->
            val separator = when {
                state.inputText.isEmpty() -> ""
                state.inputText.last().isWhitespace() -> ""
                else -> " "
            }
            state.copy(inputText = state.inputText + separator + normalized)
        }
    }

    /**
     * Валидирует draft и запускает проверку моделей либо agentic turn.
     *
     * @since 0.4
     */
    fun onSendClick() {
        val state = mutableUiState.value
        val messageText = state.inputText.trim()
        if (messageText.isEmpty() || state.isRunning || state.isScreenBlocked) return

        mutableUiState.update { it.copy(transientUserText = messageText) }
        when (state.modelGateState) {
            KsenaxBasicModelGateState.Idle ->
                startModelVerification(messageText, isInitialMessage = false)
            KsenaxBasicModelGateState.Ready ->
                if (integrityController.isVerifiedInCurrentSession()) {
                    runTurn(messageText, isInitialMessage = false)
                } else {
                    startModelVerification(messageText, isInitialMessage = false)
                }
            else -> Unit
        }
    }

    /**
     * Отменяет выполняющийся agentic turn.
     *
     * @since 0.4
     */
    fun onStopTurn() {
        turnJob?.cancel()
    }

    /**
     * Отменяет model gate и удаляет несохранённое initial message.
     *
     * @since 0.4
     */
    fun onCancelVerification() {
        verificationJob?.cancel()
        mutableUiState.update {
            it.copy(
                transientUserText = null,
                modelGateState = KsenaxBasicModelGateState.Idle,
            )
        }
    }

    /**
     * Переключает ViewModel на сохранённый Agentic-чат с [chatId].
     *
     * @since 0.4
     */
    fun onChatSelected(chatId: Long) {
        if (mutableUiState.value.isRunning) return
        val chat = latestStoredChats.firstOrNull { it.id == chatId } ?: return
        coordinator = null
        savedStateHandle[ActiveChatIdStateKey] = chatId
        mutableUiState.update {
            it.copy(
                activeChatId = chatId,
                workspaceTreeUri = chat.workspaceTreeUri,
                workspaceDisplayPath = chat.workspaceDisplayPath.orEmpty(),
                inputText = "",
                transientUserText = null,
                modelGateState = KsenaxBasicModelGateState.Idle,
                errorMessage = null,
            )
        }
    }

    /**
     * Завершает текущий turn при необходимости и запрашивает возврат на main.
     *
     * @since 0.4
     */
    fun onNewChatClick() {
        if (mutableUiState.value.isRunning) {
            exitAfterTurnStops = true
            onStopTurn()
        } else {
            effectChannel.trySend(KsenaxAgenticChatEffect.ExitToMain)
        }
    }

    /**
     * Запрашивает удаление [chatId], сначала корректно останавливая активный turn.
     *
     * @since 0.4
     */
    fun onDeleteChatRequested(chatId: Long) {
        val isActive = mutableUiState.value.activeChatId == chatId
        if (isActive && mutableUiState.value.isRunning) {
            deleteAfterTurnStops = chatId
            onStopTurn()
            return
        }
        if (isActive) clearDeletedActiveChat(chatId)
        effectChannel.trySend(
            KsenaxAgenticChatEffect.DeleteChat(chatId, returnToMain = isActive),
        )
    }

    private fun startModelVerification(
        messageText: String,
        isInitialMessage: Boolean,
    ) {
        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            val verification = integrityController.verifyOnce { stage ->
                mutableUiState.update {
                    it.copy(
                        modelGateState = when (stage) {
                            KsenaxGemmaVerificationStage.CheckingPresence ->
                                KsenaxBasicModelGateState.CheckingPresence
                            KsenaxGemmaVerificationStage.CheckingIntegrity ->
                                KsenaxBasicModelGateState.CheckingIntegrity
                        },
                    )
                }
            }

            when (verification) {
                KsenaxGemmaVerificationResult.Missing -> showGateFailure(
                    "Файл $modelTitle не найден. Установи модель заново.",
                    KsenaxBasicModelFailureStage.Presence,
                )
                KsenaxGemmaVerificationResult.Invalid -> showGateFailure(
                    "$modelTitle не прошла проверку SHA-256.",
                    KsenaxBasicModelFailureStage.Integrity,
                )
                KsenaxGemmaVerificationResult.Valid -> prepareAgent(
                    messageText,
                    isInitialMessage,
                )
            }
        }
    }

    private suspend fun prepareAgent(
        messageText: String,
        isInitialMessage: Boolean,
    ) {
        mutableUiState.update {
            it.copy(modelGateState = KsenaxBasicModelGateState.PreparingModel)
        }
        val state = mutableUiState.value
        workspaceController.createCoordinator(
            workspaceTreeUri = state.workspaceTreeUri,
            workspaceDisplayPath = state.workspaceDisplayPath,
        ).onSuccess { preparedCoordinator ->
            coordinator = preparedCoordinator
            runCatching { preparedCoordinator.prepare() }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(modelGateState = KsenaxBasicModelGateState.ModelPrepared)
                    }
                    delay(KSENAX_MODEL_VERIFICATION_SUCCESS_HOLD_MILLIS)
                    mutableUiState.update {
                        it.copy(modelGateState = KsenaxBasicModelGateState.Ready)
                    }
                    runTurn(messageText, isInitialMessage)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showGateFailure(
                        error.toPreparationMessage("Не удалось запустить локальную модель"),
                        KsenaxBasicModelFailureStage.Preparation,
                    )
                }
        }.onFailure { error ->
            showGateFailure(
                error.toPreparationMessage("Не удалось подготовить agent runtime"),
                KsenaxBasicModelFailureStage.Preparation,
            )
        }
    }

    private fun runTurn(
        messageText: String,
        isInitialMessage: Boolean,
    ) {
        val activeCoordinator = coordinator ?: run {
            mutableUiState.update {
                it.copy(
                    modelGateState = KsenaxBasicModelGateState.Idle,
                    errorMessage = "Agent runtime ещё не подготовлен.",
                )
            }
            return
        }

        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            var chatId: Long? = null
            var finalWasPersisted = false
            try {
                val persistedChatId = persistUserMessage(messageText)
                chatId = persistedChatId
                savedStateHandle[ActiveChatIdStateKey] = persistedChatId
                mutableUiState.update {
                    it.copy(
                        activeChatId = persistedChatId,
                        inputText = "",
                        isRunning = true,
                        errorMessage = null,
                    )
                }
                if (isInitialMessage) {
                    effectChannel.send(
                        KsenaxAgenticChatEffect.InitialMessageCommitted(messageText),
                    )
                }

                val result = activeCoordinator.handleUserText(
                    text = messageText,
                    onStage = { stage ->
                        persistAgentMessage(
                            chatId = persistedChatId,
                            text = KsenaxAgentTurnPresenter.stageText(stage),
                            isFinal = false,
                        )
                    },
                )
                persistAgentMessage(
                    chatId = persistedChatId,
                    text = KsenaxAgentTurnPresenter.resultText(result),
                    isFinal = true,
                )
                finalWasPersisted = true
            } catch (cancellation: CancellationException) {
                chatId?.let { persistedChatId ->
                    if (!finalWasPersisted) {
                        withContext(NonCancellable) {
                            persistAgentMessage(
                                chatId = persistedChatId,
                                text = "Запрос отменён.",
                                isFinal = true,
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                chatId?.let { persistedChatId ->
                    withContext(NonCancellable) {
                        persistAgentMessage(
                            chatId = persistedChatId,
                            text = "Не удалось завершить действие: " +
                                (error.message ?: "неизвестная ошибка"),
                            isFinal = true,
                        )
                    }
                }
                mutableUiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка agentic-запроса.")
                }
            } finally {
                mutableUiState.update { it.copy(isRunning = false) }
                if (exitAfterTurnStops) {
                    exitAfterTurnStops = false
                    effectChannel.trySend(KsenaxAgenticChatEffect.ExitToMain)
                }
                deleteAfterTurnStops?.let { deletedChatId ->
                    deleteAfterTurnStops = null
                    clearDeletedActiveChat(deletedChatId)
                    effectChannel.trySend(
                        KsenaxAgenticChatEffect.DeleteChat(
                            chatId = deletedChatId,
                            returnToMain = true,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun persistUserMessage(messageText: String): Long {
        val now = System.currentTimeMillis()
        val state = mutableUiState.value
        val message = KsenaxStoredMessage(
            role = KsenaxMessageRole.User,
            text = messageText,
            createdAtEpochMillis = now,
        )
        return state.activeChatId?.let { chatId ->
            chatRepository.appendMessage(chatId, message)
            chatId
        } ?: chatRepository.saveChat(
            KsenaxStoredChat(
                mode = KsenaxStoredChatMode.Agentic,
                title = messageText.toChatTitle(),
                workspaceTreeUri = state.workspaceTreeUri,
                workspaceDisplayPath = state.workspaceDisplayPath,
                messages = listOf(message),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    private suspend fun persistAgentMessage(
        chatId: Long,
        text: String,
        isFinal: Boolean,
    ) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        chatRepository.appendMessage(
            chatId = chatId,
            message = KsenaxStoredMessage(
                role = KsenaxMessageRole.Assistant,
                text = normalized,
                createdAtEpochMillis = System.currentTimeMillis(),
                isFinalAgenticStep = isFinal,
            ),
        )
    }

    private fun clearDeletedActiveChat(chatId: Long) {
        if (mutableUiState.value.activeChatId != chatId) return
        savedStateHandle.remove<Long>(ActiveChatIdStateKey)
        coordinator = null
        mutableUiState.update {
            it.copy(
                activeChatId = null,
                transientUserText = null,
                isRunning = false,
                errorMessage = null,
            )
        }
    }

    private fun showGateFailure(
        message: String,
        stage: KsenaxBasicModelFailureStage,
    ) {
        mutableUiState.update {
            it.copy(
                modelGateState = KsenaxBasicModelGateState.Failure(message, stage),
                errorMessage = message,
            )
        }
    }

    /**
     * Создаёт Agentic ViewModel из process-level зависимостей и navigation-state.
     *
     * [SavedStateHandle] берётся из `CreationExtras` текущего chat destination.
     * Agentic model topology намеренно не является параметром фабрики: ею владеет
     * [KsenaxAndroidApplication.agenticWorkRuntimeController].
     *
     * @since 0.4
     * @author Stephan Kolesnikov
     */
    class Factory(
        private val application: KsenaxAndroidApplication,
        private val initialChatId: Long?,
        private val initialWorkspaceTreeUri: String?,
        private val initialWorkspaceDisplayPath: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: androidx.lifecycle.viewmodel.CreationExtras,
        ): T {
            require(modelClass.isAssignableFrom(KsenaxAgenticChatViewModel::class.java))
            return KsenaxAgenticChatViewModel(
                initialChatId = initialChatId,
                initialWorkspaceTreeUri = initialWorkspaceTreeUri,
                initialWorkspaceDisplayPath = initialWorkspaceDisplayPath,
                savedStateHandle = extras.createSavedStateHandle(),
                chatRepository = application.chatRepository,
                workspaceController = application.agenticWorkRuntimeController,
                integrityController = application.agenticModelsIntegrityController,
                modelTitle = "Gemma-4 + FunctionGemma",
            ) as T
        }

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            error("KsenaxAgenticChatViewModel requires CreationExtras.")
        }
    }
}

private fun String.toChatTitle(): String =
    trim().replace(Regex("\\s+"), " ").take(ChatTitleMaxLength)

private fun Throwable.toPreparationMessage(fallback: String): String {
    val detail = sequenceOf(message, cause?.message)
        .firstOrNull { candidate -> !candidate.isNullOrBlank() }

    return buildString {
        append(fallback)
        append(": ")
        append(this@toPreparationMessage::class.java.simpleName)
        if (detail != null) {
            append(" — ")
            append(detail)
        }
    }
}
