package com.kolesnikovprod.ksetaorch.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kolesnikovprod.ksetaorch.communication.voice.KsenaxRecordedVoiceInput
import com.kolesnikovprod.ksetaorch.communication.voice.KsenaxVoiceController
import com.kolesnikovprod.ksetaorch.communication.voice.vosk.KsenaxVoskModelReachabilityProbe
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.download.KsenaxModelInstallCoordinator
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxGemma4E2BInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxFunctionGemmaInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxVoskRuSmallInstallUseCase
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxModelRuntimeSettingsController
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxVoiceInputController
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxDownloadStallTracker
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxPostInstallValidationController
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxPostInstallValidationResult
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxPostInstallValidationStage
import com.kolesnikovprod.ksetaorch.ui.helpers.KsenaxInstallCoordinatorSelector
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.model.toPresentationChat
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxTranscribingModel
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModelSelector
import com.kolesnikovprod.ksetaorch.ui.main.settings.toVoiceRecordingProfile
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxTranscribingModelSelector
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxAppSettingsSnapshot
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxContextWindow
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsUiState
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeId
import com.kolesnikovprod.ksetaorch.ui.helpers.KsenaxInstallUiStateReducer
import com.kolesnikovprod.ksetaorch.ui.helpers.KsenaxPendingInstallAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.KsenaxWorkingFolderSelection

class KsenaxMainViewModel(application: Application) : AndroidViewModel(application) {

    /*
     * ╦            ╔═══════════════════╗
     * ╠════════════╬▢  DEPENDENCIES  ▢╣
     * ╩            ╚═══════════════════╝
     */

    private val appContext = application.applicationContext
    private val ksenaxApplication = application as KsenaxAndroidApplication
    private val modelPreferences = appContext.getSharedPreferences(
        MODEL_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val settingsPreferences = appContext.getSharedPreferences(
        SETTINGS_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val legacyUiPreferences = appContext.getSharedPreferences(
        LEGACY_UI_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val gemmaInstallUseCase = KsenaxGemma4E2BInstallUseCase(appContext)
    private val functionGemmaInstallUseCase =
        KsenaxFunctionGemmaInstallUseCase(appContext)
    private val voskInstallUseCase = KsenaxVoskRuSmallInstallUseCase(appContext)

    /*
     * ╦            ╔════════════════════════════╗
     * ╠════════════╬▢  CONTROLLERS / HELPERS  ▢╣
     * ╩            ╚════════════════════════════╝
     */

    private val installCoordinatorSelector = KsenaxInstallCoordinatorSelector(
        gemmaCoordinator = KsenaxModelInstallCoordinator(gemmaInstallUseCase),
        functionGemmaCoordinator =
            KsenaxModelInstallCoordinator(functionGemmaInstallUseCase),
        voskCoordinator  = KsenaxModelInstallCoordinator(voskInstallUseCase),
    )

    private val voiceController = KsenaxVoiceController()

    private val voiceInputController = KsenaxVoiceInputController(
        voskInstallUseCase  = voskInstallUseCase,
        gemmaInstallUseCase = gemmaInstallUseCase,
        gemmaModelSession   = ksenaxApplication.gemmaModelSession,
    )

    private val modelRuntimeSettingsController = KsenaxModelRuntimeSettingsController(
        responseModelSessions = listOf(
            ksenaxApplication.gemmaModelSession,
        ),
    )


    /*
     * ╦            ╔═════════════════════╗
     * ╠════════════╬▢  INTERNAL STATE  ▢╣
     * ╩            ╚═════════════════════╝
     */

    private var installObservationJob: Job? = null
    private var installCompletionJob: Job? = null
    private var postInstallVerificationJob: Job? = null
    private var voiceRecordingStartJob: Job? = null
    private var pendingInstallAction:  KsenaxPendingInstallAction? = null
    private var isRoutedChatVoiceInputActive = false
    private val downloadStallTracker = KsenaxDownloadStallTracker()

    private val postInstallValidationControllers by lazy {
        mapOf(
            KsenaxInstallOverlayTarget.Gemma4E2B to
                KsenaxPostInstallValidationController(
                    installUseCase = gemmaInstallUseCase,
                    sessionRegistry =
                        ksenaxApplication.modelVerificationSessionRegistry,
                    reachabilityProbe = {
                        ksenaxApplication.gemmaModelSession.initializeEngine()
                    },
                ),
            KsenaxInstallOverlayTarget.FunctionGemma270M to
                KsenaxPostInstallValidationController(
                    installUseCase = functionGemmaInstallUseCase,
                    sessionRegistry =
                        ksenaxApplication.modelVerificationSessionRegistry,
                    reachabilityProbe = {
                        ksenaxApplication.functionGemmaModelSession
                            .initializeEngine()
                    },
                ),
            KsenaxInstallOverlayTarget.VoskSmallRu to
                KsenaxPostInstallValidationController(
                    installUseCase = voskInstallUseCase,
                    sessionRegistry =
                        ksenaxApplication.modelVerificationSessionRegistry,
                    reachabilityProbe = {
                        KsenaxVoskModelReachabilityProbe(
                            modelDirectoryPath =
                                voskInstallUseCase.getInstalledPath(),
                        ).verify()
                    },
                ),
        )
    }

    private val voiceTranscriptionChannel = Channel<String>(Channel.BUFFERED)
    val voiceTranscriptions = voiceTranscriptionChannel.receiveAsFlow()

    private val initialSettingsSnapshot = readInitialSettingsSnapshot()
    private val unresolvedInitialSettingsSnapshot =
        initialSettingsSnapshot.copy(
            transcribingModel = null,
            responseModel = null,
        )


    /*
     * ╦            ╔═══════════════╗
     * ╠════════════╬▢  UI STATE  ▢╣
     * ╩            ╚═══════════════╝
     */

    var uiState by mutableStateOf(
        KsenaxMainUiState(
            gemmaInstallSnapshot = installCoordinatorSelector.gemmaInitialSnapshot(),
            functionGemmaInstallSnapshot =
                installCoordinatorSelector.functionGemmaInitialSnapshot(),
            voskInstallSnapshot  = installCoordinatorSelector.voskInitialSnapshot(),
            selectedTranscribingModel = null,
            selectedSupportedModel = null,
            settingsUiState = KsenaxSettingsUiState(
                savedSnapshot = unresolvedInitialSettingsSnapshot,
                draftSnapshot = unresolvedInitialSettingsSnapshot,
            ),
        ),
    )
        private set


    /*
     * ╦            ╔════════════════╗
     * ╠════════════╬▢  LIFECYCLE  ▢╣
     * ╩            ╚════════════════╝
     */

    init {
        applyModelContextWindow(initialSettingsSnapshot.contextWindow)
        observeStoredChats()
        observeVoiceState()
        refreshInstalledModelFlags()
        resumeSavedInstallIfNeeded()
    }


    /*
     * ╦            ╔═══════════════════════╗
     * ╠════════════╬▢  PUBLIC UI EVENTS  ▢╣
     * ╩            ╚═══════════════════════╝
     */

    fun onInputTextChanged(value: String) {
        uiState = uiState.onInputTextChangedDownstreamed(value)
    }

    fun onModeSelected(mode: ChatMode) {
        uiState = uiState.onModeSelectedDownstreamed(mode)
    }

    fun onWorkingFolderSelected(selection: KsenaxWorkingFolderSelection) {
        if (!selection.hasPersistedPermission) {
            uiState = uiState.onWorkingFolderFailure(
                "Android не дал постоянный доступ к выбранной директории"
            )
            return
        }

        uiState = uiState.onSuccessfulUpdateWorkingFolderData(selection)

        viewModelScope.launch {
            ksenaxApplication.agenticWorkRuntimeController.initializeWorkspace(
                workspaceTreeUri = selection.treeUri,
                workspaceDisplayPath = selection.displayPath,
            ).onFailure { error ->
                uiState = uiState.onWorkingFolderFailure(
                    error,
                    "Не удалось подготовить рабочую директорию"
                )
            }
        }
    }

    fun onRenameChat(chat: KsenaxChat, newTitle: String) {
        val normalizedTitle = newTitle
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(CHAT_TITLE_MAX_LENGTH)
        if (normalizedTitle.isEmpty() || normalizedTitle == chat.title) {
            return
        }

        viewModelScope.launch {
            ksenaxApplication.chatRepository.renameChat(
                chatId = chat.id,
                title = normalizedTitle,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    fun onDeleteChat(chat: KsenaxChat) {
        onDeleteChat(chat.id, chat.mode)
    }

    fun onDeleteChat(chatId: Long, mode: ChatMode) {
        val shouldClearActiveChat =
            uiState.activeChatId == chatId && uiState.activeChat?.mode == mode
        if (shouldClearActiveChat) {
            uiState = uiState.copy(activeChatId = null)
        }
        viewModelScope.launch {
            ksenaxApplication.chatRepository.deleteChat(chatId)
        }
    }

    fun onNewChatClick() {
        uiState = uiState.onNewChatClickedDownstreamed()
    }

    fun onBasicMessageCommitted(messageText: String) {
        clearInputIfItStillContains(messageText)
    }

    fun onAgenticMessageCommitted(messageText: String) {
        clearInputIfItStillContains(messageText)
    }

    fun onTemporaricMessageAccepted(messageText: String) {
        clearInputIfItStillContains(messageText)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onMicClick() {
        if (uiState.voiceSnapshot.isProcessingVoice) {
            return
        }

        if (uiState.voiceSnapshot.isRecording) {
            voiceController.stopRecording()
            return
        }

        if (voiceRecordingStartJob?.isActive == true) {
            return
        }

        voiceRecordingStartJob = viewModelScope.launch {
            val transcribingModel = KsenaxTranscribingModelSelector
                .resolveTranscribingModelForVoiceRecording(uiState)

            if (!ensureTranscribingModelInstallationIsCurrent(transcribingModel)) {
                onTranscribingModelClick(transcribingModel)
                return@launch
            }

            val recordingProfile = transcribingModel.toVoiceRecordingProfile()
            val outputFile = voiceController.createVoiceOutputFile(
                savedVoicesDirPath =
                    voiceInputController.voiceOutputDirectoryPathFor(transcribingModel),
                profile = recordingProfile,
            )

            voiceController.startRecording(
                coroutineScope   = viewModelScope,
                outputFile       = outputFile,
                recordingProfile = recordingProfile,
                onRecorded       = ::onVoiceRecorded,
                onFailure        = ::onVoiceRecordingFailure,
            )
        }
    }

    fun onTranscribingModelClick(model: KsenaxTranscribingModel) {
        val isInstalled = KsenaxTranscribingModelSelector
            .isInstalled(uiState, model)

        if (isInstalled) {
            uiState = uiState.copy(selectedTranscribingModel = model)
            saveTranscribingModel(model)
            syncCommittedModelsIntoSettings()
            return
        }

        pendingInstallAction = KsenaxPendingInstallAction.SelectTranscribingModel(model)

        uiState = KsenaxInstallUiStateReducer.showModelOffer(
            uiState = uiState,
            target = KsenaxTranscribingModelSelector.installOverlayTargetFor(model)
        )
    }

    fun onSupportedModelClick(model: KsenaxSupportedTextModel) {
        if (KsenaxSupportedTextModelSelector.isInstalled(uiState, model)) {
            uiState = uiState.copy(selectedSupportedModel = model)
            saveSupportedModel(model)
            syncCommittedModelsIntoSettings()
            return
        }

        pendingInstallAction =
            KsenaxPendingInstallAction.SelectSupportedTextModel(model)

        uiState = KsenaxInstallUiStateReducer.showModelOffer(
            uiState = uiState,
            target =
                KsenaxSupportedTextModelSelector.installOverlayTargetFor(model),
        )
    }

    fun onSettingsOpened() {
        val committedSnapshot = uiState.settingsUiState.savedSnapshot.copy(
            transcribingModel = uiState.selectedTranscribingModel,
            responseModel = uiState.selectedSupportedModel,
        )
        uiState = uiState.copy(
            settingsUiState = KsenaxSettingsUiState(
                savedSnapshot = committedSnapshot,
                draftSnapshot = committedSnapshot,
            ),
        )
    }

    fun onSettingsTranscribingModelClick(model: KsenaxTranscribingModel) {
        if (KsenaxTranscribingModelSelector.isInstalled(uiState, model)) {
            updateSettingsDraft { snapshot ->
                snapshot.copy(transcribingModel = model)
            }
            return
        }

        pendingInstallAction = KsenaxPendingInstallAction.SelectTranscribingModel(
            model = model,
            forSettingsDraft = true,
        )
        uiState = KsenaxInstallUiStateReducer.showModelOffer(
            uiState = uiState,
            target = KsenaxTranscribingModelSelector.installOverlayTargetFor(model),
        )
    }

    fun onSettingsResponseModelClick(model: KsenaxSupportedTextModel) {
        if (KsenaxSupportedTextModelSelector.isInstalled(uiState, model)) {
            updateSettingsDraft { snapshot ->
                snapshot.copy(responseModel = model)
            }
            return
        }

        pendingInstallAction = KsenaxPendingInstallAction.SelectSupportedTextModel(
            model = model,
            forSettingsDraft = true,
        )
        uiState = KsenaxInstallUiStateReducer.showModelOffer(
            uiState = uiState,
            target =
                KsenaxSupportedTextModelSelector.installOverlayTargetFor(model),
        )
    }

    fun onSettingsContextWindowSelected(contextWindow: KsenaxContextWindow) {
        updateSettingsDraft { snapshot ->
            snapshot.copy(contextWindow = contextWindow)
        }
    }

    fun onSettingsLaunchAnimationChanged(isEnabled: Boolean) {
        updateSettingsDraft { snapshot ->
            snapshot.copy(launchAnimationEnabled = isEnabled)
        }
    }

    fun onSettingsThemeApplied(
        themeId: KsenaxThemeId,
        themeBackgroundSaturation: Float,
    ) {
        val normalizedSaturation = themeBackgroundSaturation.coerceIn(0f, 1f)
        val currentSettings = uiState.settingsUiState
        val savedSnapshot = currentSettings.savedSnapshot.copy(
            themeId = themeId,
            themeBackgroundSaturation = normalizedSaturation,
        )
        val draftSnapshot = currentSettings.draftSnapshot.copy(
            themeId = themeId,
            themeBackgroundSaturation = normalizedSaturation,
        )

        settingsPreferences.edit()
            .putString(THEME_ID_KEY, themeId.name)
            .putFloat(
                THEME_BACKGROUND_SATURATION_KEY,
                normalizedSaturation,
            )
            .remove(THEME_BACKGROUND_COLOR_ENABLED_KEY)
            .apply()

        uiState = uiState.copy(
            settingsUiState = currentSettings.copy(
                savedSnapshot = savedSnapshot,
                draftSnapshot = draftSnapshot,
            ),
        )
    }

    fun onSaveSettings() {
        val snapshot = uiState.settingsUiState.draftSnapshot
        saveSettingsSnapshot(snapshot)
        applyModelContextWindow(snapshot.contextWindow)
        uiState = uiState.copy(
            selectedTranscribingModel = snapshot.transcribingModel,
            selectedSupportedModel = snapshot.responseModel,
            settingsUiState = uiState.settingsUiState.copy(
                savedSnapshot = snapshot,
                draftSnapshot = snapshot,
                isExitConfirmationVisible = false,
            ),
        )
    }

    fun onSettingsBackRequested(): Boolean {
        if (!uiState.settingsUiState.hasUnsavedChanges) {
            return true
        }
        uiState = uiState.copy(
            settingsUiState = uiState.settingsUiState.copy(
                isExitConfirmationVisible = true,
            ),
        )
        return false
    }

    fun onDismissSettingsExitConfirmation() {
        uiState = uiState.copy(
            settingsUiState = uiState.settingsUiState.copy(
                isExitConfirmationVisible = false,
            ),
        )
    }

    fun onDiscardSettingsChanges() {
        uiState = uiState.copy(
            settingsUiState = uiState.settingsUiState.copy(
                draftSnapshot = uiState.settingsUiState.savedSnapshot,
                isExitConfirmationVisible = false,
            ),
        )
    }

    fun onBasicVoiceInputActive(isActive: Boolean) {
        isRoutedChatVoiceInputActive = isActive
    }

    fun onAgenticVoiceInputActive(isActive: Boolean) {
        isRoutedChatVoiceInputActive = isActive
    }

    fun onTemporaricVoiceInputActive(isActive: Boolean) {
        isRoutedChatVoiceInputActive = isActive
    }

    fun onDismissModelOfferClick() {
        pendingInstallAction = null
        uiState = KsenaxInstallUiStateReducer.hideOverlay(uiState)
    }

    fun onAllowDownloadOverMeteredNetworkChange(isAllowed: Boolean) {
        uiState = uiState.copy(allowDownloadOverMeteredNetwork = isAllowed)
    }

    fun onAllowDownloadOverRoamingChange(isAllowed: Boolean) {
        uiState = uiState.copy(allowDownloadOverRoaming = isAllowed)
    }

    fun onInstallModelClick() {
        val target = uiState.activeInstallOverlayTarget ?: return
        installCompletionJob?.cancel()
        postInstallVerificationJob?.cancel()
        downloadStallTracker.reset()
        ksenaxApplication.modelVerificationSessionRegistry.invalidate(
            target.installTarget.id,
        )
        uiState = uiState.copy(
            isActiveDownloadStalled = false,
            postInstallVerificationTarget = null,
            postInstallVerificationState = null,
        )

        val currentSnapshot = KsenaxInstallUiStateReducer.snapshotFor(
            uiState,
            target
        )

        val nextSnapshot = installCoordinatorSelector.coordinatorFor(target).startDownload(
            currentSnapshot = currentSnapshot,
            policy = KsenaxDownloadPolicy(
                allowOverMeteredNetwork = uiState.allowDownloadOverMeteredNetwork,
                allowOverRoaming = uiState.allowDownloadOverRoaming,
            ),
        )

        uiState = KsenaxInstallUiStateReducer.withSnapshot(
            uiState = uiState,
            target = target,
            snapshot = nextSnapshot,
        )

        uiState = KsenaxInstallUiStateReducer.showProgress(uiState)

        observeInstallState(
            target   = target,
            snapshot = nextSnapshot,
        )
    }

    fun onCancelDownloadClick() {
        if (
            uiState.modelDownloadOverlayState !=
                KsenaxModelDownloadOverlayState.Progress ||
            uiState.activeInstallSnapshot?.isDownloading != true
        ) {
            return
        }
        uiState = KsenaxInstallUiStateReducer.showCancelConfirmation(uiState)
    }

    fun onMinimizeDownloadOverlayClick() {
        if (
            uiState.modelDownloadOverlayState !=
                KsenaxModelDownloadOverlayState.Progress ||
            uiState.activeInstallSnapshot?.isDownloading != true
        ) {
            return
        }
        uiState = uiState.copy(
            isModelDownloadOverlayMinimized = true,
            isCancelDownloadConfirmationVisible = false,
            keyboardDismissRequestId = uiState.keyboardDismissRequestId + 1,
        )
    }

    fun onExpandDownloadOverlayClick() {
        if (uiState.modelDownloadOverlayState ==
            KsenaxModelDownloadOverlayState.Hidden
        ) {
            return
        }
        uiState = uiState.copy(isModelDownloadOverlayMinimized = false)
    }

    fun onKeepDownloadClick() {
        uiState = KsenaxInstallUiStateReducer.hideCancelConfirmation(uiState)
    }

    fun onConfirmCancelDownloadClick() {
        val target = uiState.activeInstallOverlayTarget ?: return

        installObservationJob?.cancel()
        installCompletionJob?.cancel()
        downloadStallTracker.reset()

        val curSnapshot = KsenaxInstallUiStateReducer.snapshotFor(uiState, target)

        val nextSnapshot = installCoordinatorSelector.coordinatorFor(target)
            .cancelDownload(curSnapshot)

        uiState = KsenaxInstallUiStateReducer.withSnapshot(
            uiState  = uiState,
            target   = target,
            snapshot = nextSnapshot,
        )

        uiState = KsenaxInstallUiStateReducer.showModelOffer(uiState, target)
    }

    fun onCancelPostInstallVerification() {
        postInstallVerificationJob?.cancel()
        postInstallVerificationJob = null
        uiState = uiState.copy(
            postInstallVerificationTarget = null,
            postInstallVerificationState = null,
        )
    }


    /*
     * ╦            ╔═══════════════════════╗
     * ╠════════════╬▢  SEND / CHAT FLOW  ▢╣
     * ╩            ╚═══════════════════════╝
     */

    private fun observeStoredChats() {
        viewModelScope.launch {
            ksenaxApplication.chatRepository.chats.collect { storedChats ->
                val persistedChats = storedChats
                    .map { chat -> chat.toPresentationChat() }

                uiState = uiState.copy(
                    chats = persistedChats,
                )
            }
        }
    }

    private fun clearInputIfItStillContains(messageText: String) {
        if (uiState.inputText == messageText) {
            uiState = uiState.copy(inputText = "")
        }
    }


    /*
     * ╦            ╔═════════════════╗
     * ╠════════════╬▢  VOICE FLOW  ▢╣
     * ╩            ╚═════════════════╝
     */

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceController.snapshots.collect { nextSnapshot ->
                uiState = uiState.copy(voiceSnapshot = nextSnapshot)
            }
        }
    }

    private suspend fun onVoiceRecorded(recordedVoice: KsenaxRecordedVoiceInput) {
        val transcription = voiceInputController
            .resolveInputText(recordedVoice)
            .trim()

        if (isRoutedChatVoiceInputActive) {
            voiceTranscriptionChannel.send(transcription)
            uiState = uiState.copy(voiceFailureMessage = null)
            return
        }

        val currentText = uiState.inputText
        val separator = when {
            currentText.isEmpty()             -> ""
            currentText.last().isWhitespace() -> ""
            else                              -> " "
        }

        uiState = uiState.copy(
            inputText           = currentText + separator + transcription,
            voiceFailureMessage = null
        )
    }

    private fun onVoiceRecordingFailure(message: String) {
        uiState = uiState.copy(voiceFailureMessage = message)
    }

    private suspend fun ensureTranscribingModelInstallationIsCurrent(
        model: KsenaxTranscribingModel,
    ): Boolean {
        if (KsenaxTranscribingModelSelector.isInstalled(uiState, model)) {
            return true
        }

        val isInstalled = when (model) {
            KsenaxTranscribingModel.Gemma ->
                gemmaInstallUseCase.hasValidInstallation()
            KsenaxTranscribingModel.Vosk ->
                voskInstallUseCase.hasValidInstallation()
        }

        if (!isInstalled) {
            return false
        }

        ksenaxApplication.modelVerificationSessionRegistry.markVerified(
            KsenaxTranscribingModelSelector
                .installOverlayTargetFor(model)
                .installTarget
                .id,
        )

        uiState = when (model) {
            KsenaxTranscribingModel.Gemma ->
                uiState.copy(isGemmaInstalled = true)
            KsenaxTranscribingModel.Vosk ->
                uiState.copy(isVoskInstalled = true)
        }.copy(selectedTranscribingModel = model)
        syncCommittedModelsIntoSettings()

        return true
    }


    /*
     * ╦            ╔═══════════════════╗
     * ╠════════════╬▢  INSTALL FLOW  ▢╣
     * ╩            ╚═══════════════════╝
     */

    private fun resumeSavedInstallIfNeeded() {
        val gemmaSnapshot = uiState.gemmaInstallSnapshot
        val functionGemmaSnapshot = uiState.functionGemmaInstallSnapshot
        val voskSnapshot = uiState.voskInstallSnapshot

        when {
            gemmaSnapshot.currentDownloadId != NO_DOWNLOAD_ID -> {
                uiState = uiState.copy(
                    activeInstallOverlayTarget = KsenaxInstallOverlayTarget.Gemma4E2B,
                    modelDownloadOverlayState = KsenaxModelDownloadOverlayState.Progress,
                    isModelDownloadOverlayMinimized = false,
                )
                observeInstallState(
                    target = KsenaxInstallOverlayTarget.Gemma4E2B,
                    snapshot = gemmaSnapshot,
                )
            }
            functionGemmaSnapshot.currentDownloadId != NO_DOWNLOAD_ID -> {
                uiState = uiState.copy(
                    activeInstallOverlayTarget =
                        KsenaxInstallOverlayTarget.FunctionGemma270M,
                    modelDownloadOverlayState =
                        KsenaxModelDownloadOverlayState.Progress,
                    isModelDownloadOverlayMinimized = false,
                )
                observeInstallState(
                    target = KsenaxInstallOverlayTarget.FunctionGemma270M,
                    snapshot = functionGemmaSnapshot,
                )
            }
            voskSnapshot.currentDownloadId != NO_DOWNLOAD_ID -> {
                uiState = uiState.copy(
                    activeInstallOverlayTarget = KsenaxInstallOverlayTarget.VoskSmallRu,
                    modelDownloadOverlayState = KsenaxModelDownloadOverlayState.Progress,
                    isModelDownloadOverlayMinimized = false,
                )
                observeInstallState(
                    target = KsenaxInstallOverlayTarget.VoskSmallRu,
                    snapshot = voskSnapshot,
                )
            }
        }
    }

    private fun observeInstallState(
        target: KsenaxInstallOverlayTarget,
        snapshot: KsenaxInstallSnapshot,
    ) {
        installObservationJob?.cancel()
        installObservationJob = viewModelScope.launch {
            installCoordinatorSelector.coordinatorFor(target).observeInstallState(
                initialSnapshot = snapshot,
                onSnapshotChanged = { nextSnapshot ->
                    onInstallSnapshotChanged(
                        target = target,
                        snapshot = nextSnapshot,
                    )
                },
            )
        }
    }

    private fun onInstallSnapshotChanged(
        target: KsenaxInstallOverlayTarget,
        snapshot: KsenaxInstallSnapshot,
    ) {
        uiState = KsenaxInstallUiStateReducer.withSnapshot(uiState, target, snapshot)
        uiState = uiState.copy(
            isActiveDownloadStalled = downloadStallTracker.update(
                targetKey = target.installTarget.id,
                snapshot = snapshot,
            ),
        )

        when {
            snapshot.isInstalled -> onModelInstallValidated(target)
            snapshot.isInterrupted -> {
                uiState = KsenaxInstallUiStateReducer.showModelOffer(uiState, target)
            }
        }
    }

    private fun onModelInstallValidated(target: KsenaxInstallOverlayTarget) {
        if (installCompletionJob?.isActive == true) return

        downloadStallTracker.reset()
        uiState = KsenaxInstallUiStateReducer.showCompleted(uiState)
        installCompletionJob = viewModelScope.launch {
            delay(MODEL_DOWNLOAD_SUCCESS_HOLD_MILLIS)
            completeModelInstallation(target)
            startPostInstallVerification(target)
        }
    }

    private fun completeModelInstallation(target: KsenaxInstallOverlayTarget) {
        val pendingAction = pendingInstallAction
        val installsForSettingsDraft = when (pendingAction) {
            is KsenaxPendingInstallAction.SelectTranscribingModel ->
                pendingAction.forSettingsDraft
            is KsenaxPendingInstallAction.SelectSupportedTextModel ->
                pendingAction.forSettingsDraft
            else -> false
        }
        pendingInstallAction = null

        uiState = when (target) {
            KsenaxInstallOverlayTarget.Gemma4E2B -> uiState.copy(isGemmaInstalled = true)
            KsenaxInstallOverlayTarget.FunctionGemma270M ->
                uiState.copy(isFunctionGemmaInstalled = true)
            KsenaxInstallOverlayTarget.VoskSmallRu -> uiState.copy(isVoskInstalled = true)
        }.copy(
            modelDownloadOverlayState = KsenaxModelDownloadOverlayState.Hidden,
            activeInstallOverlayTarget = null,
            isModelDownloadOverlayMinimized = false,
            isActiveDownloadStalled = false,
            isCancelDownloadConfirmationVisible = false,
        )

        if (!installsForSettingsDraft) {
            uiState = uiState.copy(
                selectedTranscribingModel =
                    KsenaxTranscribingModelSelector.resolveSelectedInstalledModel(
                        currentSelection = uiState.selectedTranscribingModel,
                        isGemmaInstalled = uiState.isGemmaInstalled,
                        isVoskInstalled = uiState.isVoskInstalled,
                    ),
                selectedSupportedModel =
                    KsenaxSupportedTextModelSelector.resolveSelectedInstalledModel(
                        currentSelection = uiState.selectedSupportedModel,
                        isGemmaInstalled = uiState.isGemmaInstalled,
                        isFunctionGemmaInstalled =
                            uiState.isFunctionGemmaInstalled,
                    ),
            )
        }

        when (pendingAction) {
            is KsenaxPendingInstallAction.SendChatMessage -> {
                val messageText = pendingAction.messageText

                if (messageText.isNotBlank()) {
                    clearInputIfItStillContains(messageText)
                }
            }

            is KsenaxPendingInstallAction.SelectTranscribingModel -> {
                if (pendingAction.forSettingsDraft) {
                    updateSettingsDraft { snapshot ->
                        snapshot.copy(transcribingModel = pendingAction.model)
                    }
                } else {
                    uiState = uiState.copy(selectedTranscribingModel = pendingAction.model)
                    saveTranscribingModel(pendingAction.model)
                    syncCommittedModelsIntoSettings()
                }
            }

            is KsenaxPendingInstallAction.SelectSupportedTextModel -> {
                if (pendingAction.forSettingsDraft) {
                    updateSettingsDraft { snapshot ->
                        snapshot.copy(responseModel = pendingAction.model)
                    }
                } else {
                    uiState = uiState.copy(selectedSupportedModel = pendingAction.model)
                    saveSupportedModel(pendingAction.model)
                    syncCommittedModelsIntoSettings()
                }
            }

            null -> Unit
        }
    }

    private fun startPostInstallVerification(
        target: KsenaxInstallOverlayTarget,
    ) {
        postInstallVerificationJob?.cancel()
        uiState = uiState.copy(
            postInstallVerificationTarget = target,
            postInstallVerificationState =
                KsenaxPostInstallVerificationState.CheckingPresence,
        )

        postInstallVerificationJob = viewModelScope.launch {
            val controller = postInstallValidationControllers.getValue(target)
            val result = controller.verify { stage ->
                if (uiState.postInstallVerificationTarget == target) {
                    uiState = uiState.copy(
                        postInstallVerificationState =
                            stage.toPostInstallUiState(),
                    )
                }
            }

            if (uiState.postInstallVerificationTarget != target) {
                return@launch
            }

            when (result) {
                KsenaxPostInstallValidationResult.Success -> {
                    uiState = uiState.copy(
                        postInstallVerificationState =
                            KsenaxPostInstallVerificationState.Success,
                    )
                    delay(POST_INSTALL_VERIFICATION_SUCCESS_HOLD_MILLIS)
                    if (
                        uiState.postInstallVerificationTarget == target &&
                        uiState.postInstallVerificationState ==
                            KsenaxPostInstallVerificationState.Success
                    ) {
                        uiState = uiState.copy(
                            postInstallVerificationTarget = null,
                            postInstallVerificationState = null,
                        )
                    }
                }

                KsenaxPostInstallValidationResult.SessionExpired -> {
                    uiState = uiState.copy(
                        postInstallVerificationTarget = null,
                        postInstallVerificationState = null,
                    )
                }

                is KsenaxPostInstallValidationResult.Failure -> {
                    uiState = uiState.copy(
                        postInstallVerificationState =
                            KsenaxPostInstallVerificationState.Failure(
                                stage = result.stage.toFailureStage(),
                                message = result.toUserMessage(target),
                            ),
                    )
                }
            }
        }
    }

    private fun KsenaxPostInstallValidationStage.toPostInstallUiState():
        KsenaxPostInstallVerificationState = when (this) {
        KsenaxPostInstallValidationStage.Presence ->
            KsenaxPostInstallVerificationState.CheckingPresence
        KsenaxPostInstallValidationStage.Integrity ->
            KsenaxPostInstallVerificationState.CheckingIntegrity
        KsenaxPostInstallValidationStage.Reachability ->
            KsenaxPostInstallVerificationState.CheckingReachability
    }

    private fun KsenaxPostInstallValidationStage.toFailureStage():
        KsenaxPostInstallVerificationFailureStage = when (this) {
        KsenaxPostInstallValidationStage.Presence ->
            KsenaxPostInstallVerificationFailureStage.Presence
        KsenaxPostInstallValidationStage.Integrity ->
            KsenaxPostInstallVerificationFailureStage.Integrity
        KsenaxPostInstallValidationStage.Reachability ->
            KsenaxPostInstallVerificationFailureStage.Reachability
    }

    private fun KsenaxPostInstallValidationResult.Failure.toUserMessage(
        target: KsenaxInstallOverlayTarget,
    ): String = when (stage) {
        KsenaxPostInstallValidationStage.Presence ->
            "Файл ${target.overlayTitle} не найден после установки."
        KsenaxPostInstallValidationStage.Integrity ->
            "${target.overlayTitle} не прошла проверку целостности."
        KsenaxPostInstallValidationStage.Reachability ->
            cause?.message
                ?.takeIf(String::isNotBlank)
                ?: "Не удалось открыть локальный runtime."
    }


    /*
     * ╦            ╔═══════════════════╗
     * ╠════════════╬▢  MODEL STATUS  ▢╣
     * ╩            ╚═══════════════════╝
     */

    private fun refreshInstalledModelFlags() {
        viewModelScope.launch {
            val isGemmaInstalled =
                gemmaInstallUseCase.hasValidInstallation()
            val isFunctionGemmaInstalled =
                functionGemmaInstallUseCase.hasValidInstallation()
            val isVoskInstalled = voskInstallUseCase.hasValidInstallation()

            listOf(
                KsenaxInstallOverlayTarget.Gemma4E2B to isGemmaInstalled,
                KsenaxInstallOverlayTarget.FunctionGemma270M to
                    isFunctionGemmaInstalled,
                KsenaxInstallOverlayTarget.VoskSmallRu to isVoskInstalled,
            ).forEach { (target, isValid) ->
                if (isValid) {
                    ksenaxApplication.modelVerificationSessionRegistry
                        .markVerified(target.installTarget.id)
                }
            }

            val selectedTranscribingModel =
                KsenaxTranscribingModelSelector.resolveSelectedInstalledModel(
                    currentSelection =
                        initialSettingsSnapshot.transcribingModel,
                    isGemmaInstalled = isGemmaInstalled,
                    isVoskInstalled = isVoskInstalled,
                )

            uiState = uiState.copy(
                isGemmaInstalled = isGemmaInstalled,
                isFunctionGemmaInstalled = isFunctionGemmaInstalled,
                isVoskInstalled = isVoskInstalled,
                selectedTranscribingModel = selectedTranscribingModel,
                selectedSupportedModel =
                    KsenaxSupportedTextModelSelector.resolveSelectedInstalledModel(
                        currentSelection =
                            initialSettingsSnapshot.responseModel,
                        isGemmaInstalled = isGemmaInstalled,
                        isFunctionGemmaInstalled = isFunctionGemmaInstalled,
                    ),
            )
            syncCommittedModelsIntoSettings()
        }
    }

    private fun readSavedTranscribingModel(): KsenaxTranscribingModel? {
        val savedName = modelPreferences.getString(
            PREFERRED_TRANSCRIBING_MODEL_KEY,
            null,
        ) ?: return null
        return KsenaxTranscribingModel.entries.firstOrNull { model ->
            model.name == savedName
        }
    }

    private fun saveTranscribingModel(model: KsenaxTranscribingModel) {
        modelPreferences.edit()
            .putString(PREFERRED_TRANSCRIBING_MODEL_KEY, model.name)
            .apply()
    }

    private fun readSavedSupportedModel(): KsenaxSupportedTextModel? {
        val savedName = modelPreferences.getString(
            PREFERRED_SUPPORTED_MODEL_KEY,
            null,
        ) ?: return null
        return KsenaxSupportedTextModel.entries.firstOrNull { model ->
            model.name == savedName
        }
    }

    private fun saveSupportedModel(model: KsenaxSupportedTextModel) {
        modelPreferences.edit()
            .putString(PREFERRED_SUPPORTED_MODEL_KEY, model.name)
            .apply()
    }

    private fun readInitialSettingsSnapshot(): KsenaxAppSettingsSnapshot {
        val savedContextWindowName = settingsPreferences.getString(
            CONTEXT_WINDOW_KEY,
            null,
        )
        val contextWindow = KsenaxContextWindow.entries.firstOrNull { option ->
            option.name == savedContextWindowName
        } ?: KsenaxContextWindow.Tokens16K
        val launchAnimationEnabled = if (
            settingsPreferences.contains(LAUNCH_ANIMATION_ENABLED_KEY)
        ) {
            settingsPreferences.getBoolean(LAUNCH_ANIMATION_ENABLED_KEY, true)
        } else {
            legacyUiPreferences.getBoolean(LEGACY_LAUNCH_ANIMATION_ENABLED_KEY, true)
        }
        val themeId = settingsPreferences
            .getString(THEME_ID_KEY, null)
            ?.let { savedName ->
                KsenaxThemeId.entries.firstOrNull { theme ->
                    theme.name == savedName
                }
            }
            ?: KsenaxThemeId.MoonValley
        val themeBackgroundSaturation = when {
            settingsPreferences.contains(
                THEME_BACKGROUND_SATURATION_KEY,
            ) -> settingsPreferences.getFloat(
                THEME_BACKGROUND_SATURATION_KEY,
                1f,
            )
            settingsPreferences.contains(
                THEME_BACKGROUND_COLOR_ENABLED_KEY,
            ) -> if (
                settingsPreferences.getBoolean(
                    THEME_BACKGROUND_COLOR_ENABLED_KEY,
                    true,
                )
            ) {
                1f
            } else {
                0f
            }
            else -> 1f
        }.coerceIn(0f, 1f)

        return KsenaxAppSettingsSnapshot(
            transcribingModel = readSavedTranscribingModel(),
            responseModel = readSavedSupportedModel(),
            contextWindow = contextWindow,
            launchAnimationEnabled = launchAnimationEnabled,
            themeId = themeId,
            themeBackgroundSaturation = themeBackgroundSaturation,
        )
    }

    private fun saveSettingsSnapshot(snapshot: KsenaxAppSettingsSnapshot) {
        settingsPreferences.edit()
            .putString(CONTEXT_WINDOW_KEY, snapshot.contextWindow.name)
            .putBoolean(
                LAUNCH_ANIMATION_ENABLED_KEY,
                snapshot.launchAnimationEnabled,
            )
            .putString(THEME_ID_KEY, snapshot.themeId.name)
            .putFloat(
                THEME_BACKGROUND_SATURATION_KEY,
                snapshot.themeBackgroundSaturation.coerceIn(0f, 1f),
            )
            .remove(THEME_BACKGROUND_COLOR_ENABLED_KEY)
            .apply()

        modelPreferences.edit().apply {
            snapshot.transcribingModel?.let { model ->
                putString(PREFERRED_TRANSCRIBING_MODEL_KEY, model.name)
            } ?: remove(PREFERRED_TRANSCRIBING_MODEL_KEY)
            snapshot.responseModel?.let { model ->
                putString(PREFERRED_SUPPORTED_MODEL_KEY, model.name)
            } ?: remove(PREFERRED_SUPPORTED_MODEL_KEY)
        }.apply()

        legacyUiPreferences.edit()
            .putBoolean(
                LEGACY_LAUNCH_ANIMATION_ENABLED_KEY,
                snapshot.launchAnimationEnabled,
            )
            .apply()
    }

    /**
     * Передаёт сохранённый размер контекстного окна в model runtime.
     *
     * Session сериализует смену конфигурации с текущим inference и при
     * необходимости пересоздаёт native engine перед следующим запросом.
     */
    private fun applyModelContextWindow(contextWindow: KsenaxContextWindow) {
        viewModelScope.launch {
            modelRuntimeSettingsController.applyMaxContextTokens(
                maxContextTokens = contextWindow.tokenCount,
            )
        }
    }

    private fun updateSettingsDraft(
        transform: (KsenaxAppSettingsSnapshot) -> KsenaxAppSettingsSnapshot,
    ) {
        uiState = uiState.copy(
            settingsUiState = uiState.settingsUiState.copy(
                draftSnapshot = transform(uiState.settingsUiState.draftSnapshot),
            ),
        )
    }

    private fun syncCommittedModelsIntoSettings() {
        val currentSettings = uiState.settingsUiState
        val previousSnapshot = currentSettings.savedSnapshot
        val nextSnapshot = previousSnapshot.copy(
            transcribingModel = uiState.selectedTranscribingModel,
            responseModel = uiState.selectedSupportedModel,
        )
        val nextDraft = currentSettings.draftSnapshot.copy(
            transcribingModel =
                if (
                    currentSettings.draftSnapshot.transcribingModel ==
                    previousSnapshot.transcribingModel
                ) {
                    nextSnapshot.transcribingModel
                } else {
                    currentSettings.draftSnapshot.transcribingModel
                },
            responseModel =
                if (
                    currentSettings.draftSnapshot.responseModel ==
                    previousSnapshot.responseModel
                ) {
                    nextSnapshot.responseModel
                } else {
                    currentSettings.draftSnapshot.responseModel
                },
        )
        uiState = uiState.copy(
            settingsUiState = currentSettings.copy(
                savedSnapshot = nextSnapshot,
                draftSnapshot = nextDraft,
            ),
        )
    }


    /*
     * ╦            ╔══════════════╗
     * ╠════════════╬▢  CLEANUP  ▢╣
     * ╩            ╚══════════════╝
     */

    override fun onCleared() {
        installObservationJob?.cancel()
        installCompletionJob?.cancel()
        postInstallVerificationJob?.cancel()
        voiceController.close()
        super.onCleared()
    }

    private companion object {
        const val MODEL_DOWNLOAD_SUCCESS_HOLD_MILLIS = 450L
        const val POST_INSTALL_VERIFICATION_SUCCESS_HOLD_MILLIS = 400L
        const val MODEL_PREFERENCES_NAME = "ksenax_model_preferences"
        const val SETTINGS_PREFERENCES_NAME = "ksenax_app_settings"
        const val LEGACY_UI_PREFERENCES_NAME = "ksenax_preferences"
        const val PREFERRED_TRANSCRIBING_MODEL_KEY = "preferred_transcribing_model"
        const val PREFERRED_SUPPORTED_MODEL_KEY = "preferred_supported_model"
        const val CONTEXT_WINDOW_KEY = "context_window"
        const val LAUNCH_ANIMATION_ENABLED_KEY = "launch_animation_enabled"
        const val THEME_ID_KEY = "theme_id"
        const val THEME_BACKGROUND_SATURATION_KEY =
            "theme_background_saturation"
        const val THEME_BACKGROUND_COLOR_ENABLED_KEY =
            "theme_background_color_enabled"
        const val LEGACY_LAUNCH_ANIMATION_ENABLED_KEY = "launch_animation_enabled"
        const val CHAT_TITLE_MAX_LENGTH = 80
    }
}
