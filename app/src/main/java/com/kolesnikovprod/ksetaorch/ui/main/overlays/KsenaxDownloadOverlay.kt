package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelSegmentedProgressBar
import com.kolesnikovprod.ksetaorch.ui.main.download.KsenaxDownloadPresentation
import com.kolesnikovprod.ksetaorch.ui.main.download.activeDownloadPresentation
import com.kolesnikovprod.ksetaorch.ui.main.download.toVerificationUiState
import com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation.KsenaxModelVerificationOverlayHost
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.LocalKsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CURRENT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_COVERED_TEXT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_DIM_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ERROR_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_INACTIVE_PIXEL_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_MUTED_GOLD_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_NEGATIVE_BRUSH
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_PRIMARY_TEXT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxModelDownloadOverlayState

/**
 * Связывает process-level install state с download overlay и передаёт
 * завершённую установку в самостоятельный validation overlay.
 *
 * @since 0.3
 */
@Composable
fun KsenaxDownloadOverlayHost(
    viewModel: KsenaxMainViewModel,
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState

    CompositionLocalProvider(LocalKsenaxThemeVisuals provides theme) {
        KsenaxDownloadOverlay(
            state = uiState.modelDownloadOverlayState,
            target = uiState.activeInstallOverlayTarget,
            presentation = uiState.activeDownloadPresentation(),
            isMinimized = uiState.isModelDownloadOverlayMinimized,
            isInterrupted =
                uiState.activeInstallSnapshot?.isInterrupted == true,
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
            onMinimizeClick = viewModel::onMinimizeDownloadOverlayClick,
            onConfirmCancelClick = viewModel::onConfirmCancelDownloadClick,
            onKeepDownloadClick = viewModel::onKeepDownloadClick,
            modifier = modifier,
        )

        uiState.postInstallVerificationTarget?.let { verificationTarget ->
            KsenaxModelVerificationOverlayHost(
                theme = theme,
                state = uiState.postInstallVerificationState
                    .toVerificationUiState(),
                modelName = verificationTarget.overlayTitle,
                onCancel = viewModel::onCancelPostInstallVerification,
                modifier = modifier,
            )
        }
    }
}

/**
 * Install overlay с плавно появляющимся полноэкранным затемнением.
 *
 * @since 0.3
 */
@Composable
fun KsenaxDownloadOverlay(
    state: KsenaxModelDownloadOverlayState,
    target: KsenaxInstallOverlayTarget?,
    presentation: KsenaxDownloadPresentation?,
    isMinimized: Boolean,
    isInterrupted: Boolean,
    allowOverMeteredNetwork: Boolean,
    allowOverRoaming: Boolean,
    isCancelConfirmationVisible: Boolean,
    onAllowOverMeteredNetworkChange: (Boolean) -> Unit,
    onAllowOverRoamingChange: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    onMinimizeClick: () -> Unit,
    onConfirmCancelClick: () -> Unit,
    onKeepDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible =
            state != KsenaxModelDownloadOverlayState.Hidden &&
                target != null &&
                !isMinimized,
        enter = fadeIn(animationSpec = tween(durationMillis = 170)),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OVERLAY_DIM_BACKGROUND_COLOUR)
                .clickable(
                    interactionSource =
                        remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        when (state) {
                            KsenaxModelDownloadOverlayState.Hidden -> Unit
                            KsenaxModelDownloadOverlayState.ModelOffer ->
                                onBackClick()
                            KsenaxModelDownloadOverlayState.Progress -> {
                                if (presentation?.canCancel == true) {
                                    onMinimizeClick()
                                }
                            }
                            KsenaxModelDownloadOverlayState.Completed -> Unit
                        }
                    },
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                KsenaxModelDownloadOverlayState.Hidden -> Unit
                KsenaxModelDownloadOverlayState.ModelOffer -> {
                    ModelOfferCard(
                        target = requireNotNull(target),
                        isInterrupted = isInterrupted,
                        allowOverMeteredNetwork =
                            allowOverMeteredNetwork,
                        allowOverRoaming = allowOverRoaming,
                        onAllowOverMeteredNetworkChange =
                            onAllowOverMeteredNetworkChange,
                        onAllowOverRoamingChange =
                            onAllowOverRoamingChange,
                        onInstallClick = onInstallClick,
                        onBackClick = onBackClick,
                    )
                }
                KsenaxModelDownloadOverlayState.Progress,
                KsenaxModelDownloadOverlayState.Completed -> {
                    presentation?.let { installPresentation ->
                        DownloadProgressCard(
                            presentation = installPresentation,
                            onCancelClick = onCancelClick,
                            onMinimizeClick = onMinimizeClick,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isCancelConfirmationVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 130)),
                exit = fadeOut(animationSpec = tween(durationMillis = 110)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource =
                                remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onKeepDownloadClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CancelConfirmationCard(
                        onConfirmCancelClick = onConfirmCancelClick,
                        onKeepDownloadClick = onKeepDownloadClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelOfferCard(
    target: KsenaxInstallOverlayTarget,
    isInterrupted: Boolean,
    allowOverMeteredNetwork: Boolean,
    allowOverRoaming: Boolean,
    onAllowOverMeteredNetworkChange: (Boolean) -> Unit,
    onAllowOverRoamingChange: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    PixelOverlayCard(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 380.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 24.dp,
                top = 24.dp,
                end = 24.dp,
                bottom = 22.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OverlayHeader()

            Spacer(modifier = Modifier.height(24.dp))

            GradientIcon(
                drawableId = R.drawable.olay_download_brand,
                contentDescription = "Локальная модель",
                brush = theme.overlayMainBrush,
                modifier = Modifier.size(70.dp),
            )

            Spacer(modifier = Modifier.height(17.dp))

            Text(
                text = target.overlayTitle,
                color = OVERLAY_PRIMARY_TEXT_COLOUR,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = target.overlayDescription,
                color = theme.mutedColor,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )

            if (isInterrupted) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Установка не завершена. Можно повторить загрузку.",
                    color = OVERLAY_ERROR_COLOUR,
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            NetworkPolicyToggleRow(
                text = "Разрешить лимитную сеть",
                isEnabled = allowOverMeteredNetwork,
                onClick = {
                    onAllowOverMeteredNetworkChange(
                        !allowOverMeteredNetwork,
                    )
                },
            )

            Spacer(modifier = Modifier.height(9.dp))

            NetworkPolicyToggleRow(
                text = "Разрешить роуминг",
                isEnabled = allowOverRoaming,
                onClick = {
                    onAllowOverRoamingChange(!allowOverRoaming)
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayTextButton(
                    text = "НАЗАД",
                    color = theme.mutedColor,
                    onClick = onBackClick,
                    showFrame = false,
                )

                Spacer(modifier = Modifier.width(18.dp))

                OverlayTextButton(
                    text = if (isInterrupted) {
                        "ПОВТОРИТЬ"
                    } else {
                        "УСТАНОВИТЬ"
                    },
                    color = OVERLAY_PRIMARY_TEXT_COLOUR,
                    onClick = onInstallClick,
                    showFrame = true,
                    textBrush = if (isInterrupted) {
                        null
                    } else {
                        theme.overlayMainBrush
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(
    presentation: KsenaxDownloadPresentation,
    onCancelClick: () -> Unit,
    onMinimizeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    PixelOverlayCard(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 390.dp)
            .heightIn(max = 620.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 22.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OverlayHeader()

            Spacer(modifier = Modifier.height(18.dp))

            PixelDownloadRing(
                progress = presentation.progress,
                modifier = Modifier.size(142.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = presentation.modelName,
                color = OVERLAY_PRIMARY_TEXT_COLOUR,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = presentation.stageDescription,
                color = theme.mutedColor,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(17.dp))

            PixelSegmentedProgressBar(
                progress = presentation.progress,
                activeBrush = theme.overlayMainBrush,
                borderBrush = theme.overlayMainBrush,
                activeTextColor = OVERLAY_CURRENT_MINT_COLOUR,
                coveredTextColor = OVERLAY_COVERED_TEXT_COLOUR,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
            )

            Spacer(modifier = Modifier.height(13.dp))

            TransferMetricsRow(presentation = presentation)

            Spacer(modifier = Modifier.height(19.dp))

            InstallStageTimeline(currentStage = presentation.stage)

            if (presentation.showSlowConnectionHint) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Похоже, слабая скорость интернета...",
                    color = OVERLAY_MUTED_GOLD_COLOUR,
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val buttonsEnabled = presentation.canCancel
            val disabledButtonColour =
                OVERLAY_INACTIVE_PIXEL_COLOUR.copy(alpha = 0.88f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayTextButton(
                    text = "СВЕРНУТЬ",
                    color = if (buttonsEnabled) {
                        theme.mutedColor
                    } else {
                        disabledButtonColour
                    },
                    onClick = onMinimizeClick,
                    showFrame = false,
                    enabled = buttonsEnabled,
                )

                Spacer(modifier = Modifier.width(18.dp))

                OverlayTextButton(
                    text = "ОТМЕНИТЬ",
                    color = if (buttonsEnabled) {
                        OVERLAY_ERROR_COLOUR
                    } else {
                        disabledButtonColour
                    },
                    onClick = onCancelClick,
                    showFrame = true,
                    brush = if (buttonsEnabled) {
                        OVERLAY_NEGATIVE_BRUSH
                    } else {
                        SolidColor(disabledButtonColour)
                    },
                    enabled = buttonsEnabled,
                )
            }
        }
    }
}
