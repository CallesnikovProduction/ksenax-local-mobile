package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.temporaric

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxTemporaricChatCoordinator
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxChatStreamEvent
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxModelIntegrityVerifier
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxGemmaVerificationResult
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxGemmaVerificationStage
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxMessage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.KSENAX_MODEL_VERIFICATION_SUCCESS_HOLD_MILLIS
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Process-only ViewModel сырого TEMPORARIC_PATTERN-чата.
 *
 * Класс намеренно не получает [com.kolesnikovprod.ksetaorch.storage.chat.domain.KsenaxChatRepository]
 * и не использует SavedStateHandle. Сообщения нужны только для текущего UI и
 * исчезают вместе с процессом приложения. Каждый turn отправляется отдельному
 * [KsenaxTemporaricChatCoordinator] без истории.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxTemporaricChatViewModel(
    private val chatCoordinator: KsenaxTemporaricChatCoordinator,
    private val integrityController: KsenaxModelIntegrityVerifier,
    val modelTitle: String,
    /** Часы длительности; production использует прежний SystemClock.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(KsenaxTemporaricChatUiState())
    val uiState = mutableUiState.asStateFlow()

    private val effectChannel =
        Channel<KsenaxTemporaricChatEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var verificationJob: Job? = null
    private var generationJob: Job? = null
    private var commitPartialOnCancellation = false
    private var exitAfterGeneration = false
    private var clearBeforeExit = false

    /**
     * Принимает initial message главного экрана и подтверждает его consumption
     * одноразовым effect только после успешного submit.
     *
     * @since 0.4
     */
    fun onMessageFromMain(messageText: String) {
        if (submit(messageText)) {
            effectChannel.trySend(
                KsenaxTemporaricChatEffect.InitialMessageAccepted(
                    messageText.trim(),
                ),
            )
        }
    }

    /**
     * Обновляет draft пользовательского сообщения.
     *
     * @since 0.4
     */
    fun onInputTextChanged(value: String) {
        mutableUiState.update { state -> state.copy(inputText = value) }
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
     * Отправляет текущий draft в stateless model pipeline.
     *
     * @since 0.4
     */
    fun onSendClick() {
        submit(mutableUiState.value.inputText)
    }

    private fun submit(rawText: String): Boolean {
        val messageText = rawText.trim()
        val state = mutableUiState.value
        if (
            messageText.isEmpty() ||
            state.isGenerating ||
            state.isScreenBlocked ||
            generationJob?.isActive == true || verificationJob?.isActive == true
        ) {
            return false
        }

        mutableUiState.update { current ->
            current.copy(
                inputText = "",
                messages = current.messages + KsenaxMessage(text = messageText),
                errorMessage = null,
            )
        }

        when (state.modelGateState) {
            KsenaxBasicModelGateState.Idle ->
                startModelVerification(messageText)

            KsenaxBasicModelGateState.Ready ->
                if (integrityController.isVerifiedInCurrentSession()) {
                    generateReply(messageText)
                } else {
                    startModelVerification(messageText)
                }

            else -> Unit
        }
        return true
    }

    private fun startModelVerification(messageText: String) {
        verificationJob?.cancel()
        mutableUiState.update { it.copy(modelGateState = KsenaxBasicModelGateState.CheckingPresence) }
        verificationJob = viewModelScope.launch {
            when (
                val result = integrityController.verifyOnce { stage ->
                    mutableUiState.update { state ->
                        state.copy(modelGateState = stage.toModelGateState())
                    }
                }
            ) {
                KsenaxGemmaVerificationResult.Missing ->
                    showGateFailure(
                        message = "Файл $modelTitle не найден. Установи модель заново.",
                        stage = KsenaxBasicModelFailureStage.Presence,
                    )

                KsenaxGemmaVerificationResult.Invalid ->
                    showGateFailure(
                        message = "$modelTitle не прошла проверку SHA-256.",
                        stage = KsenaxBasicModelFailureStage.Integrity,
                    )

                KsenaxGemmaVerificationResult.Valid -> {
                    mutableUiState.update { state ->
                        state.copy(
                            modelGateState =
                                KsenaxBasicModelGateState.PreparingModel,
                        )
                    }

                    runCatching {
                        chatCoordinator.prepare()
                    }.onSuccess {
                        mutableUiState.update { state ->
                            state.copy(
                                modelGateState =
                                    KsenaxBasicModelGateState.ModelPrepared,
                            )
                        }
                        delay(
                            KSENAX_MODEL_VERIFICATION_SUCCESS_HOLD_MILLIS,
                        )
                        mutableUiState.update { state ->
                            state.copy(
                                modelGateState = KsenaxBasicModelGateState.Ready,
                            )
                        }
                        generateReply(messageText)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        showGateFailure(
                            message = error.message
                                ?: "Не удалось запустить локальную модель.",
                            stage = KsenaxBasicModelFailureStage.Preparation,
                        )
                    }
                }
            }
        }
    }

    private fun generateReply(messageText: String) {
        generationJob?.cancel()
        commitPartialOnCancellation = false
        mutableUiState.update { it.copy(isGenerating = true, errorMessage = null) }
        // Cleanup должен существовать и для Stop сразу после принятия запроса.
        generationJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val startedAtMillis = elapsedRealtime()
            mutableUiState.update { state ->
                state.copy(
                    streamingAssistantText = "",
                    isGenerating = true,
                    errorMessage = null,
                )
            }

            try {
                chatCoordinator.streamReply(messageText).collect { event ->
                    when (event) {
                        is KsenaxChatStreamEvent.TextDelta ->
                            mutableUiState.update { state ->
                                state.copy(
                                    streamingAssistantText =
                                        state.streamingAssistantText + event.text,
                                )
                            }

                        is KsenaxChatStreamEvent.Completed -> {
                            val finalText = event.text.ifBlank {
                                mutableUiState.value.streamingAssistantText
                            }
                            commitAssistantMessage(
                                text = finalText,
                                generationDurationMillis = event.latencyMs,
                            )
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                if (commitPartialOnCancellation) {
                    commitAssistantMessage(
                        text = mutableUiState.value.streamingAssistantText,
                        generationDurationMillis =
                            elapsedRealtime() - startedAtMillis,
                    )
                }
                throw cancellation
            } catch (error: Exception) {
                val partialText = mutableUiState.value.streamingAssistantText
                if (partialText.isNotBlank()) {
                    commitAssistantMessage(
                        text = partialText,
                        generationDurationMillis =
                            elapsedRealtime() - startedAtMillis,
                    )
                }
                mutableUiState.update { state ->
                    state.copy(
                        errorMessage = error.message
                            ?: "Не удалось получить ответ модели.",
                    )
                }
            } finally {
                commitPartialOnCancellation = false
                mutableUiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        streamingAssistantText = "",
                    )
                }
                if (exitAfterGeneration) {
                    exitAfterGeneration = false
                    if (clearBeforeExit) {
                        clearBeforeExit = false
                        clearSession()
                    }
                    effectChannel.trySend(KsenaxTemporaricChatEffect.ExitToMain)
                }
            }
        }
    }

    private fun commitAssistantMessage(
        text: String,
        generationDurationMillis: Long,
    ) {
        val normalizedText = text.trim()
        mutableUiState.update { state ->
            state.copy(
                messages = if (normalizedText.isEmpty()) {
                    state.messages
                } else {
                    state.messages + KsenaxMessage(
                        text = normalizedText,
                        isUser = false,
                        generationDurationMillis = generationDurationMillis,
                    )
                },
                streamingAssistantText = "",
            )
        }
    }

    /**
     * Останавливает генерацию, сохраняя уже полученный partial response.
     *
     * @since 0.4
     */
    fun onStopGeneration() {
        if (generationJob?.isActive != true) return
        commitPartialOnCancellation = true
        generationJob?.cancel()
    }

    /**
     * Останавливает активную генерацию и запрашивает возврат на main.
     *
     * @since 0.4
     */
    fun onExitRequested() {
        cancelPendingVerification()
        if (mutableUiState.value.isGenerating) {
            exitAfterGeneration = true
            onStopGeneration()
        } else {
            effectChannel.trySend(KsenaxTemporaricChatEffect.ExitToMain)
        }
    }

    /**
     * Останавливает inference перед переходом на другой destination, не
     * уничтожая оперативную переписку и не отправляя навигационный эффект.
     *
     * @since 0.4
     */
    fun onLeaveForNavigation() {
        cancelPendingVerification()
        if (mutableUiState.value.isGenerating) {
            onStopGeneration()
        }
    }

    /**
     * Очищает RAM-only сессию и запрашивает возврат на main.
     *
     * @since 0.4
     */
    fun onNewChatClick() {
        cancelPendingVerification()
        if (mutableUiState.value.isGenerating) {
            clearBeforeExit = true
            exitAfterGeneration = true
            onStopGeneration()
        } else {
            clearSession()
            effectChannel.trySend(KsenaxTemporaricChatEffect.ExitToMain)
        }
    }

    /**
     * Отменяет model gate и очищает несохранённую RAM-only сессию.
     *
     * @since 0.4
     */
    fun onCancelVerification() {
        cancelPendingVerification()
        clearSession()
    }

    private fun cancelPendingVerification() {
        if (verificationJob?.isActive != true) return
        verificationJob?.cancel()
        verificationJob = null
        mutableUiState.update { it.copy(modelGateState = KsenaxBasicModelGateState.Idle) }
    }

    private fun clearSession() {
        mutableUiState.update { state ->
            KsenaxTemporaricChatUiState(
                modelGateState = if (
                    state.modelGateState == KsenaxBasicModelGateState.Ready
                ) {
                    KsenaxBasicModelGateState.Ready
                } else {
                    KsenaxBasicModelGateState.Idle
                },
            )
        }
    }

    private fun showGateFailure(
        message: String,
        stage: KsenaxBasicModelFailureStage,
    ) {
        mutableUiState.update { state ->
            state.copy(
                modelGateState = KsenaxBasicModelGateState.Failure(
                    message = message,
                    stage = stage,
                ),
                errorMessage = message,
            )
        }
    }

    private fun KsenaxGemmaVerificationStage.toModelGateState():
        KsenaxBasicModelGateState {
        return when (this) {
            KsenaxGemmaVerificationStage.CheckingPresence ->
                KsenaxBasicModelGateState.CheckingPresence
            KsenaxGemmaVerificationStage.CheckingIntegrity ->
                KsenaxBasicModelGateState.CheckingIntegrity
        }
    }

    /**
     * Выбирает stateless coordinator и integrity controller для response-модели.
     *
     * @since 0.4
     * @author Stephan Kolesnikov
     */
    class Factory(
        private val application: KsenaxAndroidApplication,
        private val responseModel: KsenaxSupportedTextModel =
            KsenaxSupportedTextModel.Gemma,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(
                modelClass.isAssignableFrom(
                    KsenaxTemporaricChatViewModel::class.java,
                ),
            )
            val chatCoordinator = when (responseModel) {
                KsenaxSupportedTextModel.Gemma ->
                    application.temporaricChatCoordinator
                KsenaxSupportedTextModel.FunctionGemma ->
                    application.functionGemmaTemporaricChatCoordinator
            }
            val integrityController = when (responseModel) {
                KsenaxSupportedTextModel.Gemma ->
                    application.gemmaIntegrityController
                KsenaxSupportedTextModel.FunctionGemma ->
                    application.functionGemmaIntegrityController
            }
            return KsenaxTemporaricChatViewModel(
                chatCoordinator = chatCoordinator,
                integrityController = integrityController,
                modelTitle = responseModel.title,
            ) as T
        }
    }
}
