package com.kolesnikovprod.ksetaorch.ui.main.chat

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import com.kolesnikovprod.ksetaorch.ui.main.background.KsenaxMainBackground
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.GlowingBottomBar
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.toChatPanelTitle
import com.kolesnikovprod.ksetaorch.ui.main.openSidePanelOnRightSwipe
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxDownloadOverlay
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxModelVerificationOverlay
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import com.kolesnikovprod.ksetaorch.ui.main.sidepanel.KsenaxSidePanel
import com.kolesnikovprod.ksetaorch.ui.main.topbar.PixelTopBar
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicChatEffect
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasRecordAudioPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.rememberMicrophonePermissionLauncher

@Composable
fun KsenaxBasicChatScreen(
    viewModel: KsenaxBasicChatViewModel,
    mainViewModel: KsenaxMainViewModel,
    initialMessage: String?,
    onInitialMessageCommitted: (String) -> Unit,
    onAgenticModeRequested: () -> Unit,
    onTemporaricModeRequested: () -> Unit,
    onAgenticChatSelected: (Long) -> Unit,
    onSettingsRequested: (KsenaxSettingsPage) -> Unit,
    onExitToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mainUiState = mainViewModel.uiState
    val context = LocalContext.current
    val sidePanelSwipeThresholdPx = with(LocalDensity.current) {
        84.dp.toPx()
    }
    var hasMicPermission by remember(context) {
        mutableStateOf(context.hasRecordAudioPermission())
    }
    var isSidePanelOpen by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(152.dp) }

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
                is KsenaxBasicChatEffect.InitialMessageCommitted ->
                    onInitialMessageCommitted(effect.text)

                is KsenaxBasicChatEffect.DeleteChat -> {
                    mainViewModel.onDeleteChat(effect.chatId, ChatMode.Basic)
                    if (effect.returnToMain) {
                        onExitToMain()
                    }
                }

                KsenaxBasicChatEffect.ExitToMain -> onExitToMain()
            }
        }
    }

    LaunchedEffect(mainViewModel, viewModel) {
        mainViewModel.voiceTranscriptions.collect(viewModel::onVoiceTranscribed)
    }

    DisposableEffect(mainViewModel) {
        mainViewModel.onBasicVoiceInputActive(true)
        onDispose {
            mainViewModel.onBasicVoiceInputActive(false)
        }
    }

    BackHandler {
        if (uiState.isScreenBlocked) {
            viewModel.onCancelVerification()
        } else {
            viewModel.onNewChatClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        KsenaxMainBackground(
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
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    hasMicPermission = hasMicPermission,
                    isRecordingVoice = mainUiState.voiceSnapshot.isRecording,
                    isProcessingVoice = mainUiState.voiceSnapshot.isProcessingVoice,
                    voiceLevel = mainUiState.voiceSnapshot.voiceLevel,
                    onMicClick = @SuppressLint("MissingPermission") {
                        val isGranted = context.hasRecordAudioPermission()
                        hasMicPermission = isGranted
                        if (isGranted) {
                            mainViewModel.onMicClick()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSendClick = viewModel::onSendClick,
                    isGenerating = uiState.isGenerating,
                    isInputEnabled = !uiState.isScreenBlocked,
                    showMicButton = true,
                    onStopClick = viewModel::onStopGeneration,
                    onHeightChanged = { height -> bottomBarHeight = height },
                )
            },
        ) { innerPadding ->
            val activeChat = uiState.activeChat
            if (activeChat != null) {
                KsenaxChatScreen(
                    chat = activeChat,
                    showThinkingIndicator = uiState.isAwaitingAssistantText,
                    bottomBarHeight = bottomBarHeight,
                    modifier = Modifier.padding(
                        top = innerPadding.calculateTopPadding(),
                    ),
                )
            } else {
                Text(
                    text = "Basic chat готов",
                    color = Color(0xFF7D8797),
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        KsenaxSidePanel(
            isOpen = isSidePanelOpen,
            onDismiss = { isSidePanelOpen = false },
            chats = mainUiState.chats,
            activeChatId = uiState.activeChatId,
            activeChatMode = ChatMode.Basic,
            onChatSelected = { chat ->
                if (chat.mode == ChatMode.Basic) {
                    viewModel.onChatSelected(chat.id)
                    isSidePanelOpen = false
                } else {
                    onAgenticChatSelected(chat.id)
                }
            },
            onRenameChat = mainViewModel::onRenameChat,
            onDeleteChat = { chat ->
                if (chat.mode == ChatMode.Basic) {
                    viewModel.onDeleteChatRequested(chat.id)
                } else {
                    mainViewModel.onDeleteChat(chat)
                }
            },
            onNewChatClick = {
                isSidePanelOpen = false
                viewModel.onNewChatClick()
            },
            onSettingsClick = {
                isSidePanelOpen = false
                onSettingsRequested(KsenaxSettingsPage.Main)
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(34.dp)
                .fillMaxHeight()
                .openSidePanelOnRightSwipe(
                    enabled = !isSidePanelOpen &&
                        !uiState.isScreenBlocked,
                    thresholdPx = sidePanelSwipeThresholdPx,
                    onOpen = { isSidePanelOpen = true },
                ),
        )

        PixelTopBar(
            isSidePanelOpen = isSidePanelOpen,
            selectedMode = ChatMode.Basic,
            activeChatMode = uiState.activeChat?.mode,
            activeChatTitle = uiState.activeChat?.title?.toChatPanelTitle(),
            onModeSelected = { mode ->
                when (mode) {
                    ChatMode.Basic -> Unit
                    ChatMode.Agentic -> {
                        onAgenticModeRequested()
                        viewModel.onNewChatClick()
                    }
                    ChatMode.Temporaric -> {
                        onTemporaricModeRequested()
                        viewModel.onNewChatClick()
                    }
                }
            },
            onMenuClick = {
                isSidePanelOpen = !isSidePanelOpen
            },
        )

        if (
            uiState.errorMessage != null &&
            uiState.modelGateState == KsenaxBasicModelGateState.Ready
        ) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = Color(0xFF9B8490),
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 126.dp, start = 20.dp, end = 20.dp),
            )
        }

        KsenaxModelVerificationOverlay(
            state = uiState.modelGateState,
            onCancel = viewModel::onCancelVerification,
            onOpenSupportedModels = {
                isSidePanelOpen = false
                onSettingsRequested(KsenaxSettingsPage.ResponseModel)
            },
            modifier = Modifier.fillMaxSize(),
        )

        KsenaxDownloadOverlay(
            state                       = mainUiState.modelDownloadOverlayState,
            target                      = mainUiState.activeInstallOverlayTarget,
            progress                    = mainUiState.activeInstallProgress,
            allowOverMeteredNetwork     = mainUiState.allowDownloadOverMeteredNetwork,
            allowOverRoaming            = mainUiState.allowDownloadOverRoaming,
            isCancelConfirmationVisible =
                mainUiState.isCancelDownloadConfirmationVisible,
            onAllowOverMeteredNetworkChange =
                mainViewModel::onAllowDownloadOverMeteredNetworkChange,
            onAllowOverRoamingChange        =
                mainViewModel::onAllowDownloadOverRoamingChange,
            onInstallClick                  =
                mainViewModel::onInstallModelClick,
            onBackClick                     =
                mainViewModel::onDismissModelOfferClick,
            onCancelClick                   =
                mainViewModel::onCancelDownloadClick,
            onConfirmCancelClick            =
                mainViewModel::onConfirmCancelDownloadClick,
            onKeepDownloadClick             =
                mainViewModel::onKeepDownloadClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
