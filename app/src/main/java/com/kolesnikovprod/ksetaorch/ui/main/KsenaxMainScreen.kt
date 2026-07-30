package com.kolesnikovprod.ksetaorch.ui.main

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedContent
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasRecordAudioPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.rememberMicrophonePermissionLauncher
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.rememberWorkingFolderLauncher
import com.kolesnikovprod.ksetaorch.ui.main.background.KsenaxMainBackground
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.GlowingBottomBar
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarDefaultMeasuredHeight
import com.kolesnikovprod.ksetaorch.ui.main.download.minimizedDownloadPresentation
import com.kolesnikovprod.ksetaorch.ui.main.center.KsenaxCenterContent
import com.kolesnikovprod.ksetaorch.ui.main.chat.KsenaxChatScreen
import com.kolesnikovprod.ksetaorch.ui.main.launch.KsenaxLaunchAnimation
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.toChatPanelTitle
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxDownloadOverlayHost
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxProductInfoOverlay
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import com.kolesnikovprod.ksetaorch.ui.main.sidepanel.KsenaxSidePanel
import com.kolesnikovprod.ksetaorch.ui.main.sidepanel.rememberKsenaxSidePanelRevealState
import com.kolesnikovprod.ksetaorch.ui.main.topbar.PixelTopBar
import com.kolesnikovprod.ksetaorch.ui.theme.visuals
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel


/**
 * Главный Compose-экран приложения и текущая точка сборки его presentation-слоя.
 *
 * Экран читает единый [KsenaxMainViewModel.uiState] и раскладывает его по
 * дочерним элементам: центральному экрану, чату, top bar, bottom bar, боковой
 * панели, настройкам транскрибации и overlay установки моделей. Пользовательские
 * действия, меняющие содержательное состояние приложения, передаются обратно
 * в [KsenaxMainViewModel].
 *
 * Из ViewModel экран получает:
 * - текст ввода, список диалогов и активный диалог;
 * - режим `Basic` или `Agentic` и выбранную рабочую папку;
 * - состояние записи, обработки голоса и текущую громкость;
 * - выбранную модель транскрибации и признаки установки Gemma/Vosk;
 * - состояние download overlay, прогресс, сетевую политику и подтверждение отмены.
 *
 * Локально в composable остаётся краткоживущее UI-состояние:
 * - открытие side panel и экрана настроек транскрибации;
 * - показ стартовой анимации;
 * - актуальное состояние Android-разрешения на микрофон;
 * - Activity Result launchers для разрешения и выбора рабочей папки;
 * - реакция на запрос ViewModel скрыть клавиатуру.
 *
 * Такое разделение означает, что экран управляет отображением и Android UI API,
 * но не запускает установку модели, транскрибацию или изменение диалога
 * самостоятельно.
 *
 * @param viewModel текущий владелец содержательного состояния главного экрана.
 * @param modifier внешний модификатор корневого контейнера.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KsenaxMainScreen(
    viewModel:  KsenaxMainViewModel,
    appVersion: Float,
    onBasicChatRequested: (String) -> Unit,
    onBasicChatSelected: (Long) -> Unit,
    onAgenticChatRequested: (String, String?, String) -> Unit,
    onAgenticChatSelected: (Long) -> Unit,
    onTemporaricChatRequested: (String) -> Unit,
    onSettingsRequested: (KsenaxSettingsPage) -> Unit,
    modifier:   Modifier = Modifier,
) {
    /*
     * ╦            ╔══════════════════╗
     * ╠════════════╬▢  STATE READS  ▢╣
     * ╩            ╚══════════════════╝
     */

    val uiState = viewModel.uiState
    val theme = uiState.settingsUiState.savedSnapshot.themeId.visuals
    val activeChat = uiState.activeChat


    /*
     * ╦            ╔══════════════════╗
     * ╠════════════╬▢  STATE READS  ▢╣
     * ╩            ╚══════════════════╝
     */

    /**
     * Возвращает текущий контекст, внутри которого работает Composable.
     *
     * Нужен для доступа ко всему Android API.
     */
    val context = LocalContext.current
    /**
     * Возвращает объект, управляющий фокусом ввода.
     *
     * То есть именно он знает:
     * - какое поле сейчас активно;
     * - какое поле получит фокус;
     * - как снять фокус.
     */
    val focusManager = LocalFocusManager.current

    /**
     * Контроллер экранной клавиатуры.
     */
    val keyboardController = LocalSoftwareKeyboardController.current

    /**
     * Объект, которому принадлежит жизненный цикл текущего экрана.
     */
    val lifecycleOwner = LocalLifecycleOwner.current

    /**
     * Android API жестов работает в пикселях.
     *
     * Показывает, «с какого момента смещения пальца можно открывать боковую панель».
     */
    val sidePanelWidthPx = with(LocalDensity.current) {
        236.dp.toPx()
    }

    /*
     * ╦            ╔═════════════════════╗
     * ╠════════════╬▢  LOCAL UI STATE  ▢╣
     * ╩            ╚═════════════════════╝
     */

    var hasMicPermission by remember(context) {
        mutableStateOf(context.hasRecordAudioPermission())
    }

    val sidePanelState = rememberKsenaxSidePanelRevealState()

    var bottomBarHeight by remember {
        mutableStateOf(BottomBarDefaultMeasuredHeight)
    }

    var isLaunchAnimationVisible by rememberSaveable {
        mutableStateOf(
            uiState.settingsUiState.savedSnapshot.launchAnimationEnabled,
        )
    }

    var isProductInfoOverlayVisible by rememberSaveable {
        mutableStateOf(false)
    }

    /*
     * ╦            ╔══════════════════════════╗
     * ╠════════════╬▢  ACTIVITY RESULT API  ▢╣
     * ╩            ╚══════════════════════════╝
     */

    val micPermissionLauncher = rememberMicrophonePermissionLauncher(
        context                  = context,
        onPermissionStateChanged = { hasMicPermission = it },
        onGranted                = viewModel::onMicClick
    )

    val workingFolderLauncher = rememberWorkingFolderLauncher(
        viewModel::onWorkingFolderSelected
    )


    /*
     * ╦            ╔══════════════════╗
     * ╠════════════╬▢  SIDE EFFECTS ▢╣
     * ╩            ╚══════════════════╝
     */

    LaunchedEffect(uiState.keyboardDismissRequestId) {
        if (uiState.keyboardDismissRequestId > 0) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    /*
     * Способ сказать Compose: «пока этот экран существует, сделай что-то;
     * а когда экран исчезнет — обязательно убери это».
     *
     * + проверка, мало ли пользователь отозвал разрешение на микрофон
     */
    DisposableEffect(
        context,
        lifecycleOwner,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            // нас интересует только момент возвращения пользователя на контекстный экран.
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = context.hasRecordAudioPermission()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // защита от memory leak...
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    /*
     * ╦            ╔══════════════════╗
     * ╠════════════╬▢  ROOT LAYOUT  ▢╣
     * ╩            ╚══════════════════╝
     */

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .dragSidePanelHorizontally(
                enabled = !sidePanelState.isOpen,
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
        /*
         * ╦            ╔═════════════════╗
         * ╠════════════╬▢  BACKGROUND  ▢╣
         * ╩            ╚═════════════════╝
         */
        KsenaxMainBackground(
            theme = theme,
            showScenicOverlay = activeChat == null,
            modifier = Modifier.fillMaxSize(),
        )

        /*
         * ╦            ╔════════════════════╗
         * ╠════════════╬▢  MAIN SCAFFOLD  ▢╣
         * ╩            ╚════════════════════╝
         */

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
                    theme             = theme,
                    value             = uiState.inputText,
                    onValueChange     = viewModel::onInputTextChanged,
                    hasMicPermission  = hasMicPermission,
                    isRecordingVoice  = uiState.voiceSnapshot.isRecording,
                    isProcessingVoice = uiState.voiceSnapshot.isProcessingVoice,
                    voiceLevel        = uiState.voiceSnapshot.voiceLevel,
                    onMicClick        = @SuppressLint("MissingPermission") {
                        val isGranted = context.hasRecordAudioPermission()
                        hasMicPermission = isGranted

                        if (!isGranted) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.onMicClick()
                        }
                    },
                    onSendClick = {
                        val message = uiState.inputText.trim()
                        if (message.isNotEmpty()) {
                            when (uiState.selectedMode) {
                                ChatMode.Basic -> onBasicChatRequested(message)
                                ChatMode.Agentic -> onAgenticChatRequested(
                                    message,
                                    uiState.workingFolderTreeUri,
                                    uiState.workingFolderPath,
                                )
                                ChatMode.Temporaric ->
                                    onTemporaricChatRequested(message)
                            }
                        }
                    },
                    downloadPresentation =
                        uiState.minimizedDownloadPresentation(),
                    onDownloadClick =
                        viewModel::onExpandDownloadOverlayClick,
                    onHeightChanged = { height -> bottomBarHeight = height },
                )
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = uiState.activeChatId,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                },
                label = "main_content_transition",
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            ) { targetChatId ->
                val targetChat = uiState.chats.firstOrNull { chat ->
                    chat.id == targetChatId
                }

                if (targetChat == null) {
                    KsenaxCenterContent(
                        theme                  = theme,
                        isTypingStarted       = !isLaunchAnimationVisible,
                        isAgenticModeSelected = uiState.isAgenticModeSelected,
                        workingFolderPath     = uiState.workingFolderPath,
                        workingFolderError    = uiState.workingFolderFailureMessage,
                        onWorkingFolderClick  = {
                            workingFolderLauncher.launch(null)
                        },
                        onLogoClick = {
                            isProductInfoOverlayVisible = true
                        },
                    )
                } else {
                    KsenaxChatScreen(
                        theme = theme,
                        chat = targetChat,
                        bottomBarHeight = bottomBarHeight,
                    )
                }
            }
        }

        /*
         * ╦            ╔═════════════════╗
         * ╠════════════╬▢  SIDE PANEL  ▢╣
         * ╩            ╚═════════════════╝
         */

        KsenaxSidePanel(
            theme          = theme,
            isOpen         = sidePanelState.isOpen ||
                sidePanelState.revealProgress > 0f,
            revealProgress = sidePanelState.revealProgress,
            onDismiss      = sidePanelState::close,
            chats          = uiState.chats,
            activeChatId   = uiState.activeChatId,
            activeChatMode = activeChat?.mode,
            onChatSelected = { chat ->
                if (chat.mode == ChatMode.Basic) {
                    onBasicChatSelected(chat.id)
                } else {
                    onAgenticChatSelected(chat.id)
                }
                sidePanelState.snapClosed()
            },
            onRenameChat = viewModel::onRenameChat,
            onDeleteChat = viewModel::onDeleteChat,
            onNewChatClick = {
                viewModel.onNewChatClick()
                sidePanelState.snapClosed()
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

        /*
         * ╦            ╔══════════════╗
         * ╠════════════╬▢  TOP BAR  ▢╣
         * ╩            ╚══════════════╝
         */

        PixelTopBar(
            theme           = theme,
            isSidePanelOpen = sidePanelState.isOpen ||
                sidePanelState.revealProgress > 0f,
            selectedMode    = uiState.selectedMode,
            activeChatMode  = activeChat?.mode,
            activeChatTitle = activeChat?.title?.toChatPanelTitle(),
            onModeSelected  = { mode ->
                viewModel.onModeSelected(mode)
            },
            onMenuClick     = {
                sidePanelState.toggle()
            },
        )

        /*
         * ╦            ╔═══════════════════════╗
         * ╠════════════╬▢  DOWNLOAD OVERLAY  ▢╣
         * ╩            ╚═══════════════════════╝
         */

        KsenaxDownloadOverlayHost(
            viewModel = viewModel,
            theme = theme,
            modifier = Modifier.fillMaxSize(),
        )

        KsenaxProductInfoOverlay(
            theme = theme,
            isVisible = isProductInfoOverlayVisible,
            onDismiss = {
                isProductInfoOverlayVisible = false
            },
            currentVersionOfApplication = appVersion,
            modifier = Modifier.fillMaxSize(),
        )

        /*
         * ╦            ╔═══════════════════════╗
         * ╠════════════╬▢  LAUNCH ANIMATION  ▢╣
         * ╩            ╚═══════════════════════╝
         */

        if (isLaunchAnimationVisible) {
            KsenaxLaunchAnimation(
                onFinished = {
                    isLaunchAnimationVisible = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
/**
 * Привязывает левую панель к горизонтальному движению пальца.
 *
 * Закрытая панель принимает только движение вправо. Открытая принимает оба
 * направления, поэтому её можно также плавно вернуть за левую границу.
 * Распознавание начинается после horizontal touch-slop и не конкурирует с
 * вертикальным списком сообщений.
 *
 * @since 0.3
 */
internal fun Modifier.dragSidePanelHorizontally(
    enabled: Boolean,
    isOpen: Boolean,
    onDragStarted: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, isOpen) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dragAccepted = false
            val dragStart = awaitHorizontalTouchSlopOrCancellation(
                pointerId = down.id,
            ) { change, overSlop ->
                if (isOpen || overSlop > 0f) {
                    dragAccepted = true
                    change.consume()
                    onDragStarted()
                    onDragDelta(overSlop)
                }
            }

            if (dragStart == null || !dragAccepted) {
                return@awaitEachGesture
            }

            val completed = horizontalDrag(dragStart.id) { change ->
                val dragAmount = change.positionChange().x
                if (dragAmount != 0f) {
                    change.consume()
                    onDragDelta(dragAmount)
                }
            }

            if (completed) {
                onDragFinished()
            } else {
                onDragCancelled()
            }
        }
    }
}
