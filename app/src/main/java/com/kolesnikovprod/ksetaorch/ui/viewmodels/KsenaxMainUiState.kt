package com.kolesnikovprod.ksetaorch.ui.viewmodels

import com.kolesnikovprod.ksetaorch.communication.voice.KsenaxVoiceSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.KsenaxWorkingFolderSelection
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxTranscribingModel
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxModelDownloadOverlayState.Hidden

/**
 * Визуальная строка, показывающая по умолчанию направление в «Documents».
 * Ирония в том, что storage-контур не знает о визуале, но он тоже по умолчанию
 * выбирает «Documents», ища папку в хранилище устройства.
 *
 * @since 0.2
 */
const val DefaultWorkingFolderPath = "/storage/emulated/0/Documents/ksenax-workspace"

/**
 * Единый снимок состояния главного экрана.
 *
 * @param inputText текст в поле ввода (внутри TextField).
 * @param selectedModeName выбранный режим чата, но сохранённый как [String].
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxMainUiState(
    val inputText:                           String                          = "",
    val selectedModeName:                    String                          = ChatMode.Basic.name,
    val workingFolderTreeUri:                String?                         = null,
    val workingFolderPath:                   String                          = DefaultWorkingFolderPath,
    val workingFolderFailureMessage:         String?                         = null,
    val chats:                               List<KsenaxChat>                = emptyList(),
    val activeChatId:                        Long?                           = null,
    val gemmaInstallSnapshot:                KsenaxInstallSnapshot           = KsenaxInstallSnapshot(),
    val functionGemmaInstallSnapshot:        KsenaxInstallSnapshot           = KsenaxInstallSnapshot(),
    val voskInstallSnapshot:                 KsenaxInstallSnapshot           = KsenaxInstallSnapshot(),
    val activeInstallOverlayTarget:          KsenaxInstallOverlayTarget?     = null,
    val modelDownloadOverlayState:           KsenaxModelDownloadOverlayState = Hidden,
    val allowDownloadOverMeteredNetwork:     Boolean                         = false,
    val allowDownloadOverRoaming:            Boolean                         = false,
    val isCancelDownloadConfirmationVisible: Boolean                         = false,
    val keyboardDismissRequestId:            Int                             = 0,
    val isGemmaInstalled:                    Boolean                         = false,
    val isFunctionGemmaInstalled:            Boolean                         = false,
    val isVoskInstalled:                     Boolean                         = false,
    val selectedTranscribingModel:           KsenaxTranscribingModel?        = null,
    val selectedSupportedModel:              KsenaxSupportedTextModel?       = null,
    val settingsUiState:                     KsenaxSettingsUiState            = KsenaxSettingsUiState(),
    val voiceSnapshot:                       KsenaxVoiceSnapshot             = KsenaxVoiceSnapshot(),
    val voiceFailureMessage:                 String?                         = null,
) {
    /**
     * Вычисляемое свойство выбранного режима, которое работает по логике:
     *
     * ```
     * Берём все entries из enum ChatMode, а затем находим тот, у которого имя
     * равняется selectedModeName.
     *
     * + Возвращаем его, если успех
     * - Возвращаем Basic, в случае ненахода
     * ```
     *
     * @since 0.2
     */
    val selectedMode: ChatMode
        get() = ChatMode.entries.firstOrNull { mode ->
            mode.name == selectedModeName
        } ?: ChatMode.Basic

    /**
     * Ищется чат среди всех имеющихся [KsenaxChat].
     *
     * @since 0.2
     */
    val activeChat: KsenaxChat?
        get() = chats.firstOrNull { chat ->
            chat.id == activeChatId
        }

    val isAgenticModeSelected: Boolean
        get() = selectedMode == ChatMode.Agentic

    val activeInstallProgress: Float
        get() = when (activeInstallOverlayTarget) {
            KsenaxInstallOverlayTarget.Gemma4E2B -> gemmaInstallSnapshot.downloadProgress
            KsenaxInstallOverlayTarget.FunctionGemma270M ->
                functionGemmaInstallSnapshot.downloadProgress
            KsenaxInstallOverlayTarget.VoskSmallRu -> voskInstallSnapshot.downloadProgress
            null -> 0f
        }


    /*
    * МЕТОДЫ, которые используются в MainViewModel.
    * */

    internal fun onInputTextChangedDownstreamed(text: String): KsenaxMainUiState {
        return copy(inputText = text)
    }

    internal fun onModeSelectedDownstreamed(mode: ChatMode): KsenaxMainUiState {
        return copy(
            selectedModeName = mode.name,
            activeChatId = if (mode == ChatMode.Agentic) this.activeChatId else null
        )
    }

    internal fun onWorkingFolderFailure(msg: String): KsenaxMainUiState {
        return copy(workingFolderFailureMessage = msg)
    }

    internal fun onWorkingFolderFailure(err: Throwable, defaultValue: String):
            KsenaxMainUiState {
        return copy(workingFolderFailureMessage = err.message ?: defaultValue)
    }

    internal fun onSuccessfulUpdateWorkingFolderData(selection: KsenaxWorkingFolderSelection):
            KsenaxMainUiState {
        return copy(
            workingFolderTreeUri = selection.treeUri,
            workingFolderPath = selection.displayPath,
            workingFolderFailureMessage = null
        )
    }

    internal fun onNewChatClickedDownstreamed(): KsenaxMainUiState {
        return copy(activeChatId = null, inputText = "")
    }
}

enum class KsenaxModelDownloadOverlayState {
    Hidden,
    ModelOffer,
    Downloading,
    Unpacking,
}

enum class KsenaxInstallOverlayTarget(
    val overlayTitle: String,
    val overlayDescription: String,
) {
    Gemma4E2B(
        overlayTitle = "Gemma-4-E2B",
        overlayDescription = "Стандартная мультимодальная нейросеть",
    ),
    FunctionGemma270M(
        overlayTitle = "FunctionGemma-270M",
        overlayDescription = "Компактная модель Mobile Actions",
    ),
    VoskSmallRu(
        overlayTitle = "VOSK-SMALL-RU",
        overlayDescription = "Лёгкая модель распознавания русской речи",
    ),
}
