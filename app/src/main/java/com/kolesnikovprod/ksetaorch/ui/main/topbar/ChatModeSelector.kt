package com.kolesnikovprod.ksetaorch.ui.main.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import kotlinx.coroutines.delay

private val ChatModeItems = listOf(
    ChatMode.Basic,
    ChatMode.Agentic,
    ChatMode.Temporaric,
)

private const val ChatModeDropdownExitMillis = 160
private val ChatModeDropdownWidth = 248.dp
private val ChatModeDropdownRightOffset = (-158).dp

@Composable
fun PixelChatModeSelect(
    theme: KsenaxThemeVisuals,
    selectedMode: ChatMode?,
    onModeSelected: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveSelectedMode = selectedMode ?: ChatMode.Basic
    var isDropdownMounted by remember { mutableStateOf(false) }
    var isDropdownVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isDropdownVisible, isDropdownMounted) {
        if (!isDropdownVisible && isDropdownMounted) {
            delay(ChatModeDropdownExitMillis.toLong())
            isDropdownMounted = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd,
    ) {
        PixelSelectButton(
            label = effectiveSelectedMode.label,
            gradient = theme.modeBrush(effectiveSelectedMode),
            onClick = {
                if (isDropdownVisible) {
                    isDropdownVisible = false
                } else {
                    isDropdownMounted = true
                    isDropdownVisible = true
                }
            },
        )

        DropdownMenu(
            expanded = isDropdownMounted,
            onDismissRequest = { isDropdownVisible = false },
            offset = DpOffset(x = ChatModeDropdownRightOffset, y = 7.dp),
            modifier = Modifier.width(ChatModeDropdownWidth),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            AnimatedVisibility(
                visible = isDropdownVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 120)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 120),
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = ChatModeDropdownExitMillis)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = ChatModeDropdownExitMillis),
                    ),
            ) {
                PixelModeDropdownWindow(
                    theme = theme,
                    selectedMode = effectiveSelectedMode,
                    onModeSelected = { mode ->
                        onModeSelected(mode)
                        isDropdownVisible = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun PixelChatModeBadge(
    theme: KsenaxThemeVisuals,
    mode: ChatMode,
    chatTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        chatTitle
            ?.takeIf(String::isNotBlank)
            ?.let { title ->
                Text(
                    text = title,
                    color = Color(0xFF9299A6),
                    fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 230.dp),
                )
            }

        Text(
            text = mode.label,
            color = Color.White,
            fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
            fontSize = 24.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = theme.modeBrush(mode),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )
    }
}

@Composable
private fun PixelSelectButton(
    label: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isClickable: Boolean = true,
) {
    KsenaxPressableBox(
        onClick = onClick,
        enabled = isClickable,
        modifier = modifier
            .width(90.dp)
            .height(43.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        val pressedBrush = gradient.whileKsenaxPressed(pressed)

        PixelWideFrame(
            brush = pressedBrush,
            modifier = Modifier.matchParentSize(),
        )

        Text(
            text = label,
            color = Color.White,
            fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
            fontSize = if (label.length > 8) 18.sp else 22.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 1.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = pressedBrush,
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )
    }
}

@Composable
private fun PixelModeDropdownWindow(
    theme: KsenaxThemeVisuals,
    selectedMode: ChatMode?,
    onModeSelected: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(205.dp),
    ) {
        PixelWideFrame(
            brush = theme.inactiveBrush,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ChatModeItems.forEach { mode ->
                PixelModeOption(
                    theme = theme,
                    mode = mode,
                    isSelected = mode == selectedMode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PixelModeOption(
    theme: KsenaxThemeVisuals,
    mode: ChatMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionGradient = if (isSelected) {
        theme.modeBrush(mode)
    } else {
        theme.inactiveBrush
    }

    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .height(58.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        val pressedGradient = optionGradient.whileKsenaxPressed(pressed)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientIcon(
                drawableId = mode.icon,
                contentDescription = null,
                brush = pressedGradient,
                modifier = Modifier
                    .size(
                        if (mode == ChatMode.Basic) 40.dp
                        else if (mode == ChatMode.Temporaric) 50.dp
                        else 30.dp
                    )
                    .offset(x =
                        if (mode == ChatMode.Basic) (-5).dp
                        else if (mode == ChatMode.Temporaric) (-11).dp
                        else 0.dp
                    ),
            )

            Spacer(modifier = Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f)
                    .offset(x =
                        if (mode == ChatMode.Basic) (-9).dp
                        else if (mode == ChatMode.Temporaric) (-20).dp
                        else 0.dp
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                GradientModeText(
                    text = mode.label,
                    brush = pressedGradient,
                    fontSize = 22.sp,
                    lineHeight = 16.sp,
                    fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                )

                Spacer(modifier = Modifier.height(2.dp))

                GradientModeText(
                    text = mode.description,
                    brush = pressedGradient,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                )
            }
        }
    }
}

@Composable
private fun GradientModeText(
    text: String,
    brush: Brush,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = brush,
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    )
}

private val ChatMode.description: String
    get() = when (this) {
        ChatMode.Basic -> "Стандартный диалог с локальной моделью"
        ChatMode.Agentic -> "Режим для действий\nна устройстве из рабочей папки."
        ChatMode.Temporaric -> "Временный чат. Не регистрируется в памяти."
    }
