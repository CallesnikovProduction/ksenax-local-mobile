package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.temporaric

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxTemporaricChatCoordinator
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxTemporaricChatEvent
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxGemmaIntegrityController
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxGemmaVerificationResult
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxGemmaVerificationStage
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxMessage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MODEL_PREPARED_CONFIRMATION_MILLIS = 250L

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
    private val integrityController: KsenaxGemmaIntegrityController,
    private val modelTitle: String,
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

    fun onMessageFromMain(messageText: String) {
        if (submit(messageText)) {
            effectChannel.trySend(
                KsenaxTemporaricChatEffect.InitialMessageAccepted(
                    messageText.trim(),
                ),
            )
        }
    }

    fun onInputTextChanged(value: String) {
        mutableUiState.update { state -> state.copy(inputText = value) }
    }

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

    fun onSendClick() {
        submit(mutableUiState.value.inputText)
    }

    private fun submit(rawText: String): Boolean {
        val messageText = rawText.trim()
        val state = mutableUiState.value
        if (
            messageText.isEmpty() ||
            state.isGenerating ||
            state.isScreenBlocked
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
                generateReply(messageText)

            else -> Unit
        }
        return true
    }

    private fun startModelVerification(messageText: String) {
        verificationJob?.cancel()
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
                        delay(MODEL_PREPARED_CONFIRMATION_MILLIS)
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
        generationJob = viewModelScope.launch {
            val startedAtMillis = SystemClock.elapsedRealtime()
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
                        is KsenaxTemporaricChatEvent.TextDelta ->
                            mutableUiState.update { state ->
                                state.copy(
                                    streamingAssistantText =
                                        state.streamingAssistantText + event.text,
                                )
                            }

                        is KsenaxTemporaricChatEvent.Completed -> {
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
                            SystemClock.elapsedRealtime() - startedAtMillis,
                    )
                }
            } catch (error: Exception) {
                val partialText = mutableUiState.value.streamingAssistantText
                if (partialText.isNotBlank()) {
                    commitAssistantMessage(
                        text = partialText,
                        generationDurationMillis =
                            SystemClock.elapsedRealtime() - startedAtMillis,
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

    fun onStopGeneration() {
        if (generationJob?.isActive != true) return
        commitPartialOnCancellation = true
        generationJob?.cancel()
    }

    fun onExitRequested() {
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
     */
    fun onLeaveForNavigation() {
        if (mutableUiState.value.isGenerating) {
            onStopGeneration()
        }
    }

    fun onNewChatClick() {
        if (mutableUiState.value.isGenerating) {
            clearBeforeExit = true
            exitAfterGeneration = true
            onStopGeneration()
        } else {
            clearSession()
            effectChannel.trySend(KsenaxTemporaricChatEffect.ExitToMain)
        }
    }

    fun onCancelVerification() {
        verificationJob?.cancel()
        clearSession()
        effectChannel.trySend(KsenaxTemporaricChatEffect.ExitToMain)
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
