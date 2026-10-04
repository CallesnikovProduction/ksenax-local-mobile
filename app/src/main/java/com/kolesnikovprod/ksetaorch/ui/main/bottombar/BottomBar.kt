package com.kolesnikovprod.ksetaorch.ui.main.bottombar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.components.PixelGradientSpinner
import com.kolesnikovprod.ksetaorch.ui.components.PixelSegmentedProgressBar
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarActionButtonSize
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarActionSpacing
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarBackdropFadeHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarBackdropOpaqueInset
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarBaseContainerHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarCollapsedFrameHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarDefaultBottomPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarExpandedFrameHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarFrame
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarInputHorizontalPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarInputVerticalPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarKeyboardBottomPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarMicrophoneIconSize
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarSendIconSize
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarTopPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.ButtonAsPixeledFrame
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.PixelPermissionDeniedCross
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.VoiceActivityPanel
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.VoicePanelBottomOffset
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.VoicePanelHeight
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.main.download.KsenaxDownloadPresentation
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CURRENT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_COVERED_TEXT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.mintLoaderGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun calculateImeVisibilityFraction(
    currentBottomPx: Int,
    animationSourceBottomPx: Int,
    animationTargetBottomPx: Int,
): Float {
    val animationRangePx = maxOf(
        currentBottomPx,
        animationSourceBottomPx,
        animationTargetBottomPx,
    )
    return if (animationRangePx > 0) {
        (currentBottomPx.toFloat() / animationRangePx.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlowingBottomBar(
    theme:             KsenaxThemeVisuals,
    value:             String,
    onValueChange:     (String) -> Unit,
    hasMicPermission:  Boolean,
    isRecordingVoice:  Boolean,
    isProcessingVoice: Boolean,
    voiceLevel:        Float,
    onMicClick:        () -> Unit,
    onSendClick:       () -> Unit,
    isGenerating:      Boolean = false,
    isInputEnabled:    Boolean = true,
    showMicButton:     Boolean = true,
    onStopClick:       () -> Unit = {},
    downloadPresentation: KsenaxDownloadPresentation? = null,
    onDownloadClick:   () -> Unit = {},
    onHeightChanged:   (Dp) -> Unit = {},
    modifier:          Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var pixelizedBottomBarHeight by remember {
        mutableStateOf(0.dp)
    }
    var inputFrameHeight by remember {
        mutableStateOf(BottomBarCollapsedFrameHeight)
    }
    val containerHeight =
        BottomBarBaseContainerHeight +
            (inputFrameHeight - BottomBarCollapsedFrameHeight)
                .coerceAtLeast(0.dp)

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeAnimationSourceBottomPx =
        WindowInsets.imeAnimationSource.getBottom(density)
    val imeAnimationTargetBottomPx =
        WindowInsets.imeAnimationTarget.getBottom(density)
    val imeVisibilityFraction = calculateImeVisibilityFraction(
        currentBottomPx = imeBottomPx,
        animationSourceBottomPx = imeAnimationSourceBottomPx,
        animationTargetBottomPx = imeAnimationTargetBottomPx,
    )
    val imeBottomPadding = with(density) {
        imeBottomPx.toDp()
    }
    val bottomBarBottomPadding = lerp(
        start = BottomBarDefaultBottomPadding,
        stop = BottomBarKeyboardBottomPadding,
        fraction = imeVisibilityFraction,
    )
    LaunchedEffect(downloadPresentation != null) {
        if (downloadPresentation != null) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = -imeBottomPadding)
            .height(containerHeight),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val frameTopY = (
                size.height -
                    bottomBarBottomPadding.toPx() -
                    inputFrameHeight.toPx()
                ).coerceIn(0f, size.height)
            val opaqueTopY = (
                frameTopY + BottomBarBackdropOpaqueInset.toPx()
                ).coerceIn(0f, size.height)
            val fadeTopY = (
                frameTopY - BottomBarBackdropFadeHeight.toPx()
                ).coerceAtLeast(0f)

            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.18f to Color.Black.copy(alpha = 0.05f),
                        0.46f to Color.Black.copy(alpha = 0.20f),
                        0.72f to Color.Black.copy(alpha = 0.58f),
                        1.00f to Color.Black.copy(alpha = 0.96f),
                    ),
                    startY = fadeTopY,
                    endY = opaqueTopY,
                ),
                topLeft = Offset(0f, fadeTopY),
                size = Size(size.width, opaqueTopY - fadeTopY),
            )

            drawRect(
                color = Color.Black.copy(alpha = 0.98f),
                topLeft = Offset(0f, opaqueTopY),
                size = Size(size.width, size.height - opaqueTopY),
            )
        }

        AnimatedVisibility(
            visible =
                downloadPresentation == null &&
                    (isRecordingVoice || isProcessingVoice),
            enter = slideInVertically(
                initialOffsetY = { panelHeight -> panelHeight },
                animationSpec = tween(durationMillis = 220),
            ) + fadeIn(animationSpec = tween(durationMillis = 140)),
            exit = slideOutVertically(
                targetOffsetY = { panelHeight -> panelHeight },
                animationSpec = tween(durationMillis = 220),
            ) + fadeOut(animationSpec = tween(durationMillis = 160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset {
                    val panelOffset =
                        if (pixelizedBottomBarHeight > 0.dp) {
                            pixelizedBottomBarHeight - BottomBarTopPadding + 8.dp
                        } else {
                            bottomBarBottomPadding + VoicePanelBottomOffset
                        }
                    IntOffset(x = 0, y = -panelOffset.roundToPx())
                }
                .padding(horizontal = 27.dp),
        ) {
            VoiceActivityPanel(
                frameBrush = theme.voiceBrush,
                isRecordingVoice = isRecordingVoice,
                isProcessingVoice = isProcessingVoice,
                voiceLevel = voiceLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VoicePanelHeight),
            )
        }

        if (downloadPresentation == null) {
            PixelizedBottomBar(
                theme                = theme,
                value                = value,
                onValueChange        = onValueChange,
                hasMicPermission     = hasMicPermission,
                isRecordingVoice     = isRecordingVoice,
                onMicClick           = onMicClick,
                onSendClick          = onSendClick,
                isGenerating         = isGenerating,
                isInputEnabled       = isInputEnabled,
                showMicButton        = showMicButton,
                onStopClick          = onStopClick,
                bottomContentPadding = bottomBarBottomPadding,
                onHeightChanged      = { height ->
                    pixelizedBottomBarHeight = height
                    onHeightChanged(height)
                },
                onFrameHeightChanged = { height ->
                    inputFrameHeight = height
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            KsenaxCompactDownloadBottomBar(
                theme = theme,
                presentation = downloadPresentation,
                onClick = onDownloadClick,
                bottomContentPadding = bottomBarBottomPadding,
                onHeightChanged = { height ->
                    pixelizedBottomBarHeight = height
                    inputFrameHeight = 70.dp
                    onHeightChanged(height)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
internal fun KsenaxCompactDownloadBottomBar(
    theme: KsenaxThemeVisuals,
    presentation: KsenaxDownloadPresentation,
    onClick: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnHeightChanged by rememberUpdatedState(onHeightChanged)
    var measuredHeight by remember { mutableStateOf(70.dp) }

    LaunchedEffect(measuredHeight, bottomContentPadding) {
        currentOnHeightChanged(
            measuredHeight + BottomBarTopPadding + bottomContentPadding,
        )
    }

    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp)
            .padding(
                top = BottomBarTopPadding,
                bottom = bottomContentPadding,
            )
            .height(70.dp)
            .onSizeChanged { size ->
                measuredHeight = with(density) { size.height.toDp() }
            },
    ) { pressed ->
        val pressedBrush = theme.overlayMainBrush.whileKsenaxPressed(pressed)

        BottomBarFrame(
            modifier = Modifier.matchParentSize(),
            frameBrush = pressedBrush,
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelGradientSpinner(
                gradientColors = theme.markerColors,
                modifier = Modifier.size(34.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            PixelSegmentedProgressBar(
                progress = presentation.progress,
                activeBrush = pressedBrush,
                borderBrush = pressedBrush,
                activeTextColor = OVERLAY_CURRENT_MINT_COLOUR,
                coveredTextColor = OVERLAY_COVERED_TEXT_COLOUR,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
            )
        }
    }
}

@Composable
private fun PixelizedBottomBar(
    theme:                KsenaxThemeVisuals,
    value:                String,
    onValueChange:        (String) -> Unit,
    hasMicPermission:     Boolean,
    isRecordingVoice:     Boolean,
    onMicClick:           () -> Unit,
    onSendClick:          () -> Unit,
    isGenerating:         Boolean,
    isInputEnabled:       Boolean,
    showMicButton:        Boolean,
    onStopClick:          () -> Unit,
    bottomContentPadding: Dp,
    onHeightChanged:      (Dp) -> Unit,
    onFrameHeightChanged: (Dp) -> Unit,
    modifier:             Modifier = Modifier,
) {
    val density = LocalDensity.current
    val microphoneBrush =
        if (isRecordingVoice) mintLoaderGradientBrush else theme.controlsBrush
    val inputState = rememberTextFieldState(initialText = value)
    val inputScrollState = rememberScrollState()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnHeightChanged by rememberUpdatedState(onHeightChanged)
    var measuredFrameHeight by remember {
        mutableStateOf(BottomBarCollapsedFrameHeight)
    }

    LaunchedEffect(inputState) {
        snapshotFlow { inputState.text.toString() }
            .distinctUntilChanged()
            .collect { nextValue ->
                currentOnValueChange(nextValue)
            }
    }

    LaunchedEffect(value) {
        if (inputState.text.toString() != value) {
            inputState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(measuredFrameHeight, bottomContentPadding) {
        currentOnHeightChanged(
            measuredFrameHeight + BottomBarTopPadding + bottomContentPadding,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp)
            .padding(top = BottomBarTopPadding, bottom = bottomContentPadding)
            .heightIn(
                min = BottomBarCollapsedFrameHeight,
                max = BottomBarExpandedFrameHeight,
            )
            .onSizeChanged { size ->
                val nextFrameHeight = with(density) { size.height.toDp() }
                measuredFrameHeight = nextFrameHeight
                onFrameHeightChanged(nextFrameHeight)
            },
        contentAlignment = Alignment.Center,
    ) {
        BottomBarFrame(
            modifier = Modifier.matchParentSize(),
            frameBrush = theme.controlsBrush,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                state = inputState,
                enabled = isInputEnabled,
                lineLimits = TextFieldLineLimits.MultiLine(
                    minHeightInLines = 1,
                    maxHeightInLines = 3,
                ),
                scrollState = inputScrollState,
                cursorBrush = theme.controlsBrush,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = BottomBarInputHorizontalPadding,
                        vertical = BottomBarInputVerticalPadding,
                    ),
                decorator = { innerTextField ->
                    if (inputState.text.isEmpty()) {
                        Text(
                            text = "Введите команду...",
                            color = Color(0xFF6F7C8A),
                            fontSize = 14.sp,
                            fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                        )
                    }
                    innerTextField()
                },
            )

            Row(
                modifier = Modifier.padding(start = 12.dp, end = 13.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(BottomBarActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMicButton) {
                    ButtonAsPixeledFrame(
                        buttonSize = BottomBarActionButtonSize,
                        onClick = onMicClick,
                        frameBrush = microphoneBrush,
                    ) { pressed ->
                        GradientIcon(
                            drawableId = R.drawable.bb_microphone,
                            contentDescription = null,
                            brush = microphoneBrush.whileKsenaxPressed(pressed),
                            modifier =
                                Modifier.size(BottomBarMicrophoneIconSize),
                        )

                        if (!hasMicPermission) {
                            PixelPermissionDeniedCross(
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                }

                ButtonAsPixeledFrame(
                    buttonSize = BottomBarActionButtonSize,
                    frameBrush = theme.controlsBrush,
                    onClick = {
                        if (isGenerating) {
                            onStopClick()
                        } else if (value.isNotBlank()) {
                            onSendClick()
                        }
                    },
                ) { pressed ->
                    val pressedBrush = theme.controlsBrush.whileKsenaxPressed(pressed)
                    if (isGenerating) {
                        GenerationStopIcon(
                            brush = pressedBrush,
                            modifier = Modifier.size(BottomBarSendIconSize),
                        )
                    } else {
                        GradientIcon(
                            drawableId = R.drawable.bb_send_message,
                            contentDescription = null,
                            brush = pressedBrush,
                            modifier = Modifier
                                .size(BottomBarSendIconSize)
                                .offset(x = 1.dp, y = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationStopIcon(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val outerSize = size.minDimension * 0.76f
        val innerSize = size.minDimension * 0.34f
        val outerTopLeft = Offset(
            x = (size.width - outerSize) / 2f,
            y = (size.height - outerSize) / 2f,
        )

        drawRect(
            brush = brush,
            topLeft = outerTopLeft,
            size = Size(outerSize, outerSize),
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(
                x = (size.width - innerSize) / 2f,
                y = (size.height - innerSize) / 2f,
            ),
            size = Size(innerSize, innerSize),
        )
    }
}
