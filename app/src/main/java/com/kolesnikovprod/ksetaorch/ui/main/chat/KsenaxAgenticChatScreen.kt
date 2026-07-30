package com.kolesnikovprod.ksetaorch.ui.main.chat

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasRecordAudioPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.rememberMicrophonePermissionLauncher
import com.kolesnikovprod.ksetaorch.ui.main.background.KsenaxMainBackground
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.GlowingBottomBar
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarDefaultMeasuredHeight
import com.kolesnikovprod.ksetaorch.ui.main.download.minimizedDownloadPresentation
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.dragSidePanelHorizontally
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxDownloadOverlayHost
import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationOverlayHost
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import com.kolesnikovprod.ksetaorch.ui.main.sidepanel.KsenaxSidePanel
import com.kolesnikovprod.ksetaorch.ui.main.sidepanel.rememberKsenaxSidePanelRevealState
import com.kolesnikovprod.ksetaorch.ui.main.topbar.PixelTopBar
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel
import com.kolesnikovprod.ksetaorch.ui.theme.visuals
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic.KsenaxAgenticChatEffect
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic.KsenaxAgenticChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState

@Composable
fun KsenaxAgenticChatScreen(
    viewModel: KsenaxAgenticChatViewModel,
    mainViewModel: KsenaxMainViewModel,
    initialMessage: String?,
    onInitialMessageCommitted: (String) -> Unit,
    onBasicModeRequested: () -> Unit,
    onTemporaricModeRequested: () -> Unit,
    onBasicChatSelected: (Long) -> Unit,
    onSettingsRequested: (KsenaxSettingsPage) -> Unit,
    onExitToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mainUiState = mainViewModel.uiState
    val theme = mainUiState.settingsUiState.savedSnapshot.themeId.visuals
    val context = LocalContext.current
    val sidePanelWidthPx = with(LocalDensity.current) { 236.dp.toPx() }
    var hasMicPermission by remember(context) {
        mutableStateOf(context.hasRecordAudioPermission())
    }
    val sidePanelState = rememberKsenaxSidePanelRevealState()
    var bottomBarHeight by remember {
        mutableStateOf(BottomBarDefaultMeasuredHeight)
    }

    val micPermissionLauncher = rememberMicrophonePermissionLauncher(
        context = context,
        onPermissionStateChanged = { hasMicPermission = it },
        onGranted = mainViewModel::onMicClick,
    )

    LaunchedEffect(viewModel, initialMessage) {
        viewModel.onEnter(initialMessage)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is KsenaxAgenticChatEffect.InitialMessageCommitted ->
                    onInitialMessageCommitted(effect.text)
                is KsenaxAgenticChatEffect.DeleteChat -> {
                    mainViewModel.onDeleteChat(effect.chatId, ChatMode.Agentic)
                    if (effect.returnToMain) onExitToMain()
                }
                KsenaxAgenticChatEffect.ExitToMain -> onExitToMain()
            }
        }
    }
    LaunchedEffect(mainViewModel, viewModel) {
        mainViewModel.voiceTranscriptions.collect(viewModel::onVoiceTranscribed)
    }
    DisposableEffect(mainViewModel) {
        mainViewModel.onAgenticVoiceInputActive(true)
        onDispose { mainViewModel.onAgenticVoiceInputActive(false) }
    }

    val cancelVerificationAndExit = {
        viewModel.onCancelVerification()
        onExitToMain()
    }

    BackHandler {
        if (uiState.isScreenBlocked) {
            cancelVerificationAndExit()
        } else {
            viewModel.onNewChatClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .dragSidePanelHorizontally(
                enabled = !sidePanelState.isOpen &&
                    !uiState.isScreenBlocked,
                isOpen = false,
                onDragStarted = sidePanelState::onDragStarted,
                onDragDelta = { dragDeltaX ->
                    sidePanelState.onDragDelta(
                        dragDeltaX = dragDeltaX,
                        panelWidthPx = sidePanelWidthPx,
                    )
                },
                onDragFinished = sidePanelState::onDragFinished,
                onDragCancelled = sidePanelState::onDragCancelled,
            ),
    ) {
        KsenaxMainBackground(
            theme = theme,
            showScenicOverlay = uiState.activeChat == null,
            modifier = Modifier.fillMaxSize(),
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(66.dp),
                )
            },
            bottomBar = {
                GlowingBottomBar(
                    theme = theme,
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    hasMicPermission = hasMicPermission,
                    isRecordingVoice = mainUiState.voiceSnapshot.isRecording,
                    isProcessingVoice = mainUiState.voiceSnapshot.isProcessingVoice,
                    voiceLevel = mainUiState.voiceSnapshot.voiceLevel,
                    onMicClick = @SuppressLint("MissingPermission") {
                        val granted = context.hasRecordAudioPermission()
                        hasMicPermission = granted
                        if (granted) {
                            mainViewModel.onMicClick()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSendClick = viewModel::onSendClick,
                    isGenerating = uiState.isRunning,
                    isInputEnabled = !uiState.isScreenBlocked,
                    showMicButton = true,
                    onStopClick = viewModel::onStopTurn,
                    downloadPresentation =
                        mainUiState.minimizedDownloadPresentation(),
                    onDownloadClick =
                        mainViewModel::onExpandDownloadOverlayClick,
                    onHeightChanged = { height -> bottomBarHeight = height },
                )
            },
        ) { innerPadding ->
            uiState.activeChat?.let { chat ->
                KsenaxChatScreen(
                    theme = theme,
                    chat = chat,
                    bottomBarHeight = bottomBarHeight,
                    modifier = Modifier.padding(
                        top = innerPadding.calculateTopPadding(),
                    ),
                )
            }
        }

        KsenaxSidePanel(
            theme = theme,
            isOpen = sidePanelState.isOpen ||
                sidePanelState.revealProgress > 0f,
            revealProgress = sidePanelState.revealProgress,
            onDismiss = sidePanelState::close,
            chats = mainUiState.chats,
            activeChatId = uiState.activeChatId,
            activeChatMode = ChatMode.Agentic,
            onChatSelected = { chat ->
                if (chat.mode == ChatMode.Agentic) {
                    viewModel.onChatSelected(chat.id)
                    sidePanelState.snapClosed()
                } else {
                    onBasicChatSelected(chat.id)
                }
            },
            onRenameChat = mainViewModel::onRenameChat,
            onDeleteChat = { chat ->
                if (chat.mode == ChatMode.Agentic) {
                    viewModel.onDeleteChatRequested(chat.id)
                } else {
                    mainViewModel.onDeleteChat(chat)
                }
            },
            onNewChatClick = {
                sidePanelState.snapClosed()
                viewModel.onNewChatClick()
            },
            onSettingsClick = {
                sidePanelState.snapClosed()
                onSettingsRequested(KsenaxSettingsPage.Main)
            },
            modifier = Modifier
                .fillMaxSize()
                .dragSidePanelHorizontally(
                    enabled = sidePanelState.isOpen,
                    isOpen = true,
                    onDragStarted = sidePanelState::onDragStarted,
                    onDragDelta = { dragDeltaX ->
                        sidePanelState.onDragDelta(
                            dragDeltaX = dragDeltaX,
                            panelWidthPx = sidePanelWidthPx,
                        )
                    },
                    onDragFinished = sidePanelState::onDragFinished,
                    onDragCancelled = sidePanelState::onDragCancelled,
                ),
        )

        PixelTopBar(
            theme = theme,
            isSidePanelOpen = sidePanelState.isOpen ||
                sidePanelState.revealProgress > 0f,
            selectedMode = ChatMode.Agentic,
            activeChatMode = ChatMode.Agentic,
            activeChatTitle = uiState.workspaceDisplayPath,
            onModeSelected = { mode ->
                when (mode) {
                    ChatMode.Basic -> {
                        onBasicModeRequested()
                        viewModel.onNewChatClick()
                    }
                    ChatMode.Agentic -> Unit
                    ChatMode.Temporaric -> {
                        onTemporaricModeRequested()
                        viewModel.onNewChatClick()
                    }
                }
            },
            onMenuClick = sidePanelState::toggle,
        )

        if (
            uiState.errorMessage != null &&
            uiState.modelGateState == KsenaxBasicModelGateState.Ready
        ) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = Color(0xFF9B8490),
                fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 126.dp, start = 20.dp, end = 20.dp),
            )
        }

        KsenaxModelVerificationOverlayHost(
            theme = theme,
            state = uiState.modelGateState.toModelVerificationUiState(),
            modelName = viewModel.modelTitle,
            onCancel = cancelVerificationAndExit,
            modifier = Modifier.fillMaxSize(),
        )

        KsenaxDownloadOverlayHost(
            viewModel = mainViewModel,
            theme = theme,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
