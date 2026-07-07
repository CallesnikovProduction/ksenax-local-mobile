package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxDownloadOverlay
import com.kolesnikovprod.ksetaorch.ui.main.overlays.KsenaxPermissionsOverlay
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel
import kotlinx.coroutines.delay

private const val SETTINGS_ROUTE_ENTER_MILLIS = 580
private const val SETTINGS_ROUTE_EXIT_MILLIS = 1060
private val SettingsRouteExitEasing = CubicBezierEasing(
    a = 0.16f,
    b = 1f,
    c = 0.3f,
    d = 1f,
)

@Composable
fun KsenaxAppSettingsRoute(
    viewModel: KsenaxMainViewModel,
    initialPage: KsenaxSettingsPage,
    onExitToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val exitSwipeThresholdPx = with(LocalDensity.current) {
        84.dp.toPx()
    }
    var activePage by rememberSaveable(initialPage) {
        mutableStateOf(initialPage)
    }
    var isRouteVisible by rememberSaveable {
        mutableStateOf(false)
    }
    var exitAfterAnimation by rememberSaveable {
        mutableStateOf(false)
    }
    var isPermissionsOverlayVisible by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(viewModel) {
        viewModel.onSettingsOpened()
        isRouteVisible = true
    }

    LaunchedEffect(isRouteVisible, exitAfterAnimation) {
        if (!isRouteVisible && exitAfterAnimation) {
            delay(SETTINGS_ROUTE_EXIT_MILLIS.toLong())
            onExitToMain()
        }
    }

    fun animateExit() {
        if (exitAfterAnimation) return
        activePage = KsenaxSettingsPage.Main
        isPermissionsOverlayVisible = false
        exitAfterAnimation = true
        isRouteVisible = false
    }

    fun requestExit() {
        if (viewModel.onSettingsBackRequested()) {
            animateExit()
        }
    }

    BackHandler {
        if (isPermissionsOverlayVisible) {
            isPermissionsOverlayVisible = false
        } else if (activePage != KsenaxSettingsPage.Main) {
            activePage = KsenaxSettingsPage.Main
        } else {
            requestExit()
        }
    }

    AnimatedVisibility(
        visible = isRouteVisible,
        enter = slideInHorizontally(
            initialOffsetX = { width -> width },
            animationSpec = tween(SETTINGS_ROUTE_ENTER_MILLIS),
        ) + fadeIn(animationSpec = tween(150)),
        exit = slideOutHorizontally(
            targetOffsetX = { width -> width },
            animationSpec = tween(
                durationMillis = SETTINGS_ROUTE_EXIT_MILLIS,
                easing = SettingsRouteExitEasing,
            ),
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 240,
                easing = SettingsRouteExitEasing,
            ),
        ),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(
                exitSwipeThresholdPx,
                exitAfterAnimation,
            ) {
                var accumulatedDragX = 0f

                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedDragX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        accumulatedDragX += dragAmount
                        if (accumulatedDragX > 0f) {
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (
                            accumulatedDragX >= exitSwipeThresholdPx &&
                            !exitAfterAnimation
                        ) {
                            requestExit()
                        }
                        accumulatedDragX = 0f
                    },
                    onDragCancel = {
                        accumulatedDragX = 0f
                    },
                )
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppSettingsScreen(
                state = uiState.settingsUiState,
                onBackRequested = ::requestExit,
                onSaveClick = viewModel::onSaveSettings,
                onVoiceModelPickerClick = {
                    activePage = KsenaxSettingsPage.VoiceModel
                },
                onResponseModelPickerClick = {
                    activePage = KsenaxSettingsPage.ResponseModel
                },
                onContextWindowSelected =
                    viewModel::onSettingsContextWindowSelected,
                onPermissionsClick = {
                    isPermissionsOverlayVisible = true
                },
                onLaunchAnimationChanged =
                    viewModel::onSettingsLaunchAnimationChanged,
                onDismissExitConfirmation =
                    viewModel::onDismissSettingsExitConfirmation,
                onDiscardAndExit = {
                    viewModel.onDiscardSettingsChanges()
                    animateExit()
                },
                onSaveAndExit = {
                    viewModel.onSaveSettings()
                    animateExit()
                },
                modifier = Modifier.fillMaxSize(),
            )

            SettingsPickerReveal(
                visible = activePage == KsenaxSettingsPage.VoiceModel,
            ) {
                TranscribingSettingsScreen(
                    onBackClick = {
                        activePage = KsenaxSettingsPage.Main
                    },
                    selectedModel =
                        uiState.settingsUiState.draftSnapshot.transcribingModel,
                    isGemmaInstalled = uiState.isGemmaInstalled,
                    isVoskInstalled = uiState.isVoskInstalled,
                    onModelClick = viewModel::onSettingsTranscribingModelClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            SettingsPickerReveal(
                visible = activePage == KsenaxSettingsPage.ResponseModel,
            ) {
                SupportedModelsScreen(
                    onBackClick = {
                        activePage = KsenaxSettingsPage.Main
                    },
                    selectedModel =
                        uiState.settingsUiState.draftSnapshot.responseModel,
                    isGemmaInstalled = uiState.isGemmaInstalled,
                    isFunctionGemmaInstalled =
                        uiState.isFunctionGemmaInstalled,
                    onModelClick = viewModel::onSettingsResponseModelClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            KsenaxDownloadOverlay(
                state = uiState.modelDownloadOverlayState,
                target = uiState.activeInstallOverlayTarget,
                progress = uiState.activeInstallProgress,
                allowOverMeteredNetwork =
                    uiState.allowDownloadOverMeteredNetwork,
                allowOverRoaming = uiState.allowDownloadOverRoaming,
                isCancelConfirmationVisible =
                    uiState.isCancelDownloadConfirmationVisible,
                onAllowOverMeteredNetworkChange =
                    viewModel::onAllowDownloadOverMeteredNetworkChange,
                onAllowOverRoamingChange =
                    viewModel::onAllowDownloadOverRoamingChange,
                onInstallClick = viewModel::onInstallModelClick,
                onBackClick = viewModel::onDismissModelOfferClick,
                onCancelClick = viewModel::onCancelDownloadClick,
                onConfirmCancelClick =
                    viewModel::onConfirmCancelDownloadClick,
                onKeepDownloadClick = viewModel::onKeepDownloadClick,
                modifier = Modifier.fillMaxSize(),
            )

            KsenaxPermissionsOverlay(
                isVisible = isPermissionsOverlayVisible,
                onDismiss = {
                    isPermissionsOverlayVisible = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsPickerReveal(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { height -> height },
            animationSpec = tween(durationMillis = 280),
        ) + fadeIn(animationSpec = tween(durationMillis = 150)),
        exit = slideOutVertically(
            targetOffsetY = { height -> height },
            animationSpec = tween(durationMillis = 230),
        ) + fadeOut(animationSpec = tween(durationMillis = 130)),
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}
