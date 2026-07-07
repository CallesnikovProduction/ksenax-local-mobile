package com.kolesnikovprod.ksetaorch.ui.main.bottombar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarDefaultBottomPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarDefaultInputShieldHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarFrame
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarInputShieldFadeHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarKeyboardBottomPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarKeyboardInputShieldHeight
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.BottomBarTopPadding
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.ButtonAsPixeledFrame
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.PixelPermissionDeniedCross
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.VoiceActivityPanel
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.VoicePanelBottomOffset
import com.kolesnikovprod.ksetaorch.ui.main.bottombar.common.VoicePanelHeight
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.mintLoaderGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush
import kotlinx.coroutines.flow.distinctUntilChanged


@Composable
fun GlowingBottomBar(
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
    onHeightChanged:   (Dp) -> Unit = {},
    modifier:          Modifier = Modifier,
) {
    val density = LocalDensity.current
    var pixelizedBottomBarHeight by remember {
        mutableStateOf(0.dp)
    }
    var inputFrameHeight by remember {
        mutableStateOf(62.dp)
    }
    val containerHeight =
        190.dp + (inputFrameHeight - 62.dp).coerceAtLeast(0.dp)

    // Системная клавиатура поднимается -> bottomBar тоже
    val imeBottomPadding = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val isKeyboardOpen = imeBottomPadding > 0.dp
    val bottomBarBottomPadding = if (isKeyboardOpen) {
        BottomBarKeyboardBottomPadding
    } else {
        BottomBarDefaultBottomPadding
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = -imeBottomPadding)
            .height(containerHeight),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.32f to Color.Black.copy(alpha = 0.34f),
                        0.68f to Color.Black.copy(alpha = 0.84f),
                        1.00f to Color.Black.copy(alpha = 0.98f),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
                size = size,
            )

            val deepGlowHeight = 116.dp.toPx()

            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.44f to Color.Black.copy(alpha = 0.68f),
                        1.00f to Color.Black,
                    ),
                    startY = size.height - deepGlowHeight,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - deepGlowHeight),
                size = Size(size.width, deepGlowHeight),
            )

            val keyboardTopY = size.height
            val shieldHeight = if (isKeyboardOpen) {
                BottomBarKeyboardInputShieldHeight
            } else {
                BottomBarDefaultInputShieldHeight
            }.toPx()
            val fadeHeight = BottomBarInputShieldFadeHeight.toPx()
            val shieldTopY = (keyboardTopY - shieldHeight).coerceAtLeast(0f)
            val fadeTopY = (shieldTopY - fadeHeight).coerceAtLeast(0f)

            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.58f to Color.Black.copy(alpha = 0.56f),
                        1.00f to Color.Black.copy(alpha = 0.94f),
                    ),
                    startY = fadeTopY,
                    endY = shieldTopY,
                ),
                topLeft = Offset(0f, fadeTopY),
                size = Size(size.width, shieldTopY - fadeTopY),
            )

            drawRect(
                color = Color.Black.copy(alpha = 0.96f),
                topLeft = Offset(0f, shieldTopY),
                size = Size(size.width, keyboardTopY - shieldTopY),
            )
        }

        AnimatedVisibility(
            visible = isRecordingVoice || isProcessingVoice,
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
                .offset(
                    y = -(
                        if (pixelizedBottomBarHeight > 0.dp) {
                            pixelizedBottomBarHeight - BottomBarTopPadding + 8.dp
                        } else {
                            bottomBarBottomPadding + VoicePanelBottomOffset
                        }
                    ),
                )
                .padding(horizontal = 27.dp),
        ) {
            VoiceActivityPanel(
                isRecordingVoice = isRecordingVoice,
                isProcessingVoice = isProcessingVoice,
                voiceLevel = voiceLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VoicePanelHeight),
            )
        }

        PixelizedBottomBar(
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
            onFrameHeightChanged = { height -> inputFrameHeight = height },
            modifier             = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PixelizedBottomBar(
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
        if (isRecordingVoice) mintLoaderGradientBrush else sunsetBottomBarGradientBrush
    val inputState = rememberTextFieldState(initialText = value)
    val inputScrollState = rememberScrollState()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnHeightChanged by rememberUpdatedState(onHeightChanged)
    var measuredFrameHeight by remember {
        mutableStateOf(62.dp)
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
            .heightIn(min = 62.dp, max = 106.dp)
            .onSizeChanged { size ->
                val nextFrameHeight = with(density) { size.height.toDp() }
                measuredFrameHeight = nextFrameHeight
                onFrameHeightChanged(nextFrameHeight)
            },
    ) {
        BottomBarFrame(
            modifier = Modifier.matchParentSize(),
            frameBrush = sunsetBottomBarGradientBrush
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
                cursorBrush = sunsetBottomBarGradientBrush,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = KsenaxFontFamily.tiny5,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 17.dp, vertical = 18.dp),
                decorator = { innerTextField ->
                    if (inputState.text.isEmpty()) {
                        Text(
                            text = "Введите команду...",
                            color = Color(0xFF6F7C8A),
                            fontSize = 18.sp,
                            fontFamily = KsenaxFontFamily.tiny5,
                        )
                    }
                    innerTextField()
                },
            )

            Row(
                modifier = Modifier.padding(start = 23.dp, end = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMicButton) {
                    ButtonAsPixeledFrame(
                        onClick = onMicClick,
                        frameBrush = microphoneBrush,
                    ) {
                        GradientIcon(
                            drawableId = R.drawable.soft_ic_mic,
                            contentDescription = null,
                            brush = microphoneBrush,
                            modifier = Modifier.size(50.dp),
                        )

                        if (!hasMicPermission) {
                            PixelPermissionDeniedCross(
                                modifier = Modifier.size(31.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(17.dp))
                }

                ButtonAsPixeledFrame(
                    onClick = {
                        if (isGenerating) {
                            onStopClick()
                        } else if (value.isNotBlank()) {
                            onSendClick()
                        }
                    },
                ) {
                    if (isGenerating) {
                        GenerationStopIcon(
                            modifier = Modifier.size(30.dp),
                        )
                    } else {
                        GradientIcon(
                            drawableId = R.drawable.soft_ic_send,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp).offset(x = 1.dp, y = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationStopIcon(
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
            brush = sunsetBottomBarGradientBrush,
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
