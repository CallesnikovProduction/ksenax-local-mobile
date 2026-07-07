package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.internal

import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicChatUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState

internal fun KsenaxBasicChatUiState.onGenerationStarted(chatId: Long):
        KsenaxBasicChatUiState {
    return copy(
        activeChatId             = chatId, // теперь точно есть активный чат
        inputText                = "",     // поле ввода очищается
        isGenerating             = true,   // Теперь запустилась генерация
        streamingAssistantText   = "",     // streaming-хвост сбрасывается
        generationDurationMillis = null,   // не актуально
        errorMessage             = null,   // не актуально
    )
}

internal fun KsenaxBasicChatUiState.appendAssistantDelta(delta: String):
        KsenaxBasicChatUiState {
    return copy(streamingAssistantText = streamingAssistantText + delta)
}

internal fun KsenaxBasicChatUiState.onGenerationErrored(err: Exception):
        KsenaxBasicChatUiState {
    return copy(errorMessage = err.message + "Response generation fatal error")
}

internal fun KsenaxBasicChatUiState.onUserMsgPersistErrored(err: Exception):
        KsenaxBasicChatUiState {
    return copy(
        transientUserText = null,
        isGenerating      = false,
        errorMessage      = err.message ?: "Не удалось сохранить сообщение.",
    )
}

internal fun KsenaxBasicChatUiState.onFinalizeGeneration():
        KsenaxBasicChatUiState {
    return copy(
        isGenerating           =
            false,
        streamingAssistantText =
            if (generationDurationMillis == null) "" else streamingAssistantText
    )
}

internal fun KsenaxBasicChatUiState.onActiveSelected(chatId: Long):
        KsenaxBasicChatUiState {
    return copy(
        activeChatId             = chatId,
        inputText                = "",
        transientUserText        = null,
        streamingAssistantText   = "",
        generationDurationMillis = null,
        errorMessage             = null,
    )
}

internal fun KsenaxBasicChatUiState.onActiveChatCleared():
        KsenaxBasicChatUiState {
    return copy(
        activeChatId             = null,
        transientUserText        = null,
        streamingAssistantText   = "",
        generationDurationMillis = null,
        isGenerating             = false,
        errorMessage             = null,
    )
}

internal fun KsenaxBasicChatUiState.onVerificationCancelled():
        KsenaxBasicChatUiState {
    return copy(
        transientUserText = null,
        modelGateState    = KsenaxBasicModelGateState.Idle,
    )
}

internal fun KsenaxBasicChatUiState.onModelGateFailed(
    message: String,
    stage:   KsenaxBasicModelFailureStage,
): KsenaxBasicChatUiState {
    return copy(
        modelGateState = KsenaxBasicModelGateState.Failure(
            message = message,
            stage   = stage,
        ),
        errorMessage   = message,
    )
}