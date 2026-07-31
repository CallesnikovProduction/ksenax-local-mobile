package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.main.background.KsenaxMainBackground
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxAvailableThemes
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeId
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.THEME_SWITCHER_CARD_TEXT_SCRIM_BRUSH
import com.kolesnikovprod.ksetaorch.ui.theme.design.THEME_SWITCHER_FRAME_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.THEME_SWITCHER_RADIO_INACTIVE_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.THEME_SWITCHER_SCRIM_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.THEME_SWITCHER_SURFACE_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.visuals
import kotlin.math.roundToInt

@Composable
fun ThemeSwitcherScreen(
    currentThemeId: KsenaxThemeId,
    currentThemeBackgroundSaturation: Float,
    onBackClick: () -> Unit,
    onApplyTheme: (KsenaxThemeId, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingThemeName by rememberSaveable(currentThemeId) {
        mutableStateOf(currentThemeId.name)
    }
    val pendingThemeId = KsenaxThemeId.entries.firstOrNull { theme ->
        theme.name == pendingThemeName
    } ?: currentThemeId
    val pendingTheme = pendingThemeId.visuals
    var pendingThemeBackgroundSaturation by rememberSaveable(
        currentThemeBackgroundSaturation,
    ) {
        mutableStateOf(currentThemeBackgroundSaturation.coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        KsenaxMainBackground(
            theme = pendingTheme,
            showScenicOverlay = false,
            themeBackgroundSaturation = pendingThemeBackgroundSaturation,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(THEME_SWITCHER_SCRIM_COLOUR)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            ThemeSwitcherTopBar(
                theme = pendingTheme,
                onBackClick = onBackClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            CurrentThemeStatus(
                theme = pendingTheme,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = KsenaxAvailableThemes,
                    key = { theme -> theme.id.name },
                ) { option ->
                    ThemeOptionRow(
                        option = option,
                        isSelected = option.id == pendingThemeId,
                        onClick = { pendingThemeName = option.id.name },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ThemeBackgroundSaturationControl(
                theme = pendingTheme,
                saturation = pendingThemeBackgroundSaturation,
                onSaturationChange = { saturation ->
                    pendingThemeBackgroundSaturation = saturation
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeActionButton(
                    text = "ОТМЕНА",
                    brush = pendingTheme.controlsBrush,
                    onClick = onBackClick,
                    modifier = Modifier.weight(1f),
                )
                ThemeActionButton(
                    text = "ПРИМЕНИТЬ",
                    brush = pendingTheme.selectedBrush,
                    filled = true,
                    onClick = {
                        onApplyTheme(
                            pendingThemeId,
                            pendingThemeBackgroundSaturation,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            ThemeStatusFooter(
                theme = pendingTheme,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThemeSwitcherTopBar(
    theme: KsenaxThemeVisuals,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        PixelBackButton(
            brush = theme.controlsBrush,
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GradientText(
                text = "НАСТРОЙКИ",
                fontSize = 24.sp,
                lineHeight = 25.sp,
                brush = theme.settingsBrush,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                textAlign = TextAlign.Center,
            )
            GradientText(
                text = "✦ оформление ✦",
                fontSize = 9.sp,
                lineHeight = 11.sp,
                brush = theme.controlsBrush,
                fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                textAlign = TextAlign.Center,
            )
        }

    }
}

@Composable
private fun CurrentThemeStatus(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(48.dp),
    ) {
        PixelWideFrame(
            brush = theme.inactiveBrush,
            backgroundColor = THEME_SWITCHER_SURFACE_COLOUR,
            modifier = Modifier.matchParentSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientText(
                text = "✦",
                fontSize = 16.sp,
                lineHeight = 16.sp,
                brush = theme.controlsBrush,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = "Текущая тема:",
                color = theme.mutedColor,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                fontSize = 9.sp,
            )
            Spacer(modifier = Modifier.width(7.dp))
            GradientText(
                text = theme.displayName,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                brush = theme.controlsBrush,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                modifier = Modifier.weight(1f),
            )
            SelectedThemeBadge(
                theme = theme,
            )
        }
    }
}

@Composable
private fun SelectedThemeBadge(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(94.dp)
            .height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemePixelRoundedFrame(
            brush = theme.selectedBrush,
            cornerSize = 7.dp,
            backgroundColor = THEME_SWITCHER_SURFACE_COLOUR,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = "✓ ВЫБРАНО",
            color = theme.accentColor,
            fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            fontSize = 9.sp,
            lineHeight = 10.sp,
        )
    }
}

@Composable
private fun ThemeOptionRow(
    option: KsenaxThemeVisuals,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelThemeRadio(
                isSelected = isSelected,
                selectedColor = option.accentColor.whileKsenaxPressed(pressed),
                modifier = Modifier.size(28.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp),
            ) {
                Image(
                    painter = painterResource(option.previewDrawableRes),
                    contentDescription = option.displayName,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )

                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(
                        brush = THEME_SWITCHER_CARD_TEXT_SCRIM_BRUSH,
                    )
                    if (pressed) {
                        drawRect(color = Color.White.copy(alpha = 0.14f))
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 17.dp)
                        .width(152.dp),
                ) {
                GradientText(
                    text = option.displayName,
                    fontSize = 16.sp,
                    lineHeight = 17.sp,
                    brush = option.controlsBrush,
                    fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                )

                Spacer(modifier = Modifier.height(5.dp))

                if (option.symbolDrawableRes.isEmpty()) {
                    GradientText(
                        text = "○  ✦  ≈",
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        brush = option.controlsBrush,
                        fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        option.symbolDrawableRes.forEach { symbolRes ->
                            Image(
                                painter = painterResource(symbolRes),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    option.markerColors.forEach { markerColor ->
                        Image(
                            painter = painterResource(
                                R.drawable.settings_theme_marker,
                            ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(markerColor),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun PixelThemeRadio(
    isSelected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = size.minDimension / 9f
        val inactive = THEME_SWITCHER_RADIO_INACTIVE_COLOUR
        val ringColor = if (isSelected) selectedColor else inactive
        val ringPoints = listOf(
            3 to 0, 4 to 0, 5 to 0,
            1 to 2, 2 to 1, 6 to 1, 7 to 2,
            0 to 3, 0 to 4, 0 to 5,
            8 to 3, 8 to 4, 8 to 5,
            1 to 6, 2 to 7, 6 to 7, 7 to 6,
            3 to 8, 4 to 8, 5 to 8,
        )
        ringPoints.forEach { (x, y) ->
            drawRect(
                color = ringColor,
                topLeft = Offset(x * pixel, y * pixel),
                size = Size(pixel, pixel),
            )
        }
        if (isSelected) {
            val checkPoints = listOf(
                2 to 4,
                3 to 5,
                4 to 4,
                5 to 3,
                6 to 2,
            )
            checkPoints.forEach { (x, y) ->
                drawRect(
                    color = selectedColor,
                    topLeft = Offset(x * pixel, y * pixel),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}

@Composable
private fun ThemeBackgroundSaturationControl(
    theme: KsenaxThemeVisuals,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedSaturation = saturation.coerceIn(0f, 1f)

    Box(
        modifier = modifier.height(52.dp),
    ) {
        ThemePixelRoundedFrame(
            brush = theme.controlsBrush,
            backgroundColor = THEME_SWITCHER_SURFACE_COLOUR,
            cornerSize = 9.dp,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "КОНТРАСТНОСТЬ ФОНА",
                    color = Color.White,
                    fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.weight(1f),
                )

                GradientText(
                    text = "${(normalizedSaturation * 100f).roundToInt()}%",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    brush = theme.selectedBrush,
                    fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "0% · Ч/Б",
                    color = theme.mutedColor,
                    fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                    fontSize = 8.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                ThemeSaturationSlider(
                    theme = theme,
                    value = normalizedSaturation,
                    onValueChange = onSaturationChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "100%",
                    color = theme.accentColor,
                    fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

@Composable
private fun ThemeSaturationSlider(
    theme: KsenaxThemeVisuals,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedValue = value.coerceIn(0f, 1f)
    val updateFromPosition: (Float, Float) -> Unit = { x, width ->
        if (width > 0f) {
            val percent = ((x / width).coerceIn(0f, 1f) * 100f)
                .roundToInt()
            onValueChange(percent / 100f)
        }
    }

    Canvas(
        modifier = modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = normalizedValue,
                    range = 0f..1f,
                    steps = 99,
                )
                setProgress { targetValue ->
                    onValueChange(targetValue.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(onValueChange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateFromPosition(
                        down.position.x,
                        size.width.toFloat(),
                    )
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { pointer ->
                            pointer.id == down.id
                        } ?: break

                        if (!change.pressed) break

                        updateFromPosition(
                            change.position.x,
                            size.width.toFloat(),
                        )
                        change.consume()
                    }
                }
            },
    ) {
        val segmentCount = 24
        val sidePadding = 4.dp.toPx()
        val segmentGap = 2.dp.toPx()
        val trackWidth = (size.width - sidePadding * 2f).coerceAtLeast(1f)
        val segmentWidth = ((
            trackWidth - segmentGap * (segmentCount - 1)
        ) / segmentCount).coerceAtLeast(1f)
        val segmentHeight = 5.dp.toPx()
        val trackTop = (size.height - segmentHeight) / 2f
        val activeSegments = (normalizedValue * segmentCount).roundToInt()

        repeat(segmentCount) { index ->
            val segmentLeft = sidePadding +
                index * (segmentWidth + segmentGap)
            if (index < activeSegments) {
                drawRect(
                    brush = theme.controlsBrush,
                    topLeft = Offset(segmentLeft, trackTop),
                    size = Size(segmentWidth, segmentHeight),
                )
            } else {
                drawRect(
                    color = theme.mutedColor.copy(alpha = 0.24f),
                    topLeft = Offset(segmentLeft, trackTop),
                    size = Size(segmentWidth, segmentHeight),
                )
            }
        }

        val knobPixel = 2.dp.toPx()
        val knobCenterX = sidePadding + trackWidth * normalizedValue
        drawRect(
            brush = theme.selectedBrush,
            topLeft = Offset(
                knobCenterX - knobPixel,
                size.height / 2f - knobPixel * 3f,
            ),
            size = Size(knobPixel * 2f, knobPixel * 6f),
        )
        drawRect(
            brush = theme.selectedBrush,
            topLeft = Offset(
                knobCenterX - knobPixel * 2f,
                size.height / 2f - knobPixel * 2f,
            ),
            size = Size(knobPixel * 4f, knobPixel * 4f),
        )
    }
}

@Composable
private fun ThemeActionButton(
    text: String,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        val pressedBrush = brush.whileKsenaxPressed(pressed)

        ThemePixelRoundedFrame(
            brush = pressedBrush,
            backgroundColor = THEME_SWITCHER_SURFACE_COLOUR,
            fillBrush = pressedBrush.takeIf { filled },
            cornerSize = 12.dp,
            modifier = Modifier.matchParentSize(),
        )
        if (filled) {
            Text(
                text = text,
                color = Color.Black,
                fontSize = 15.sp,
                lineHeight = 16.sp,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            )
        } else {
            GradientText(
                text = text,
                fontSize = 15.sp,
                lineHeight = 16.sp,
                brush = pressedBrush,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            )
        }
    }
}

@Composable
private fun ThemeStatusFooter(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(28.dp),
    ) {
        ThemePixelRoundedFrame(
            brush = theme.inactiveBrush,
            backgroundColor = THEME_SWITCHER_SURFACE_COLOUR,
            cornerSize = 8.dp,
            modifier = Modifier.matchParentSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STATUS: READY",
                color = theme.accentColor,
                fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 8.sp,
            )

            ThemeHeartbeatDivider(
                theme = theme,
                modifier = Modifier
                    .weight(1f)
                    .height(15.dp)
                    .padding(horizontal = 9.dp),
            )

            Text(
                text = "◆ LOCAL",
                color = theme.mutedColor,
                fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun ThemeHeartbeatDivider(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        val pulseHalfWidth = 19.dp.toPx().coerceAtMost(size.width * 0.24f)
        val dashWidth = 6.dp.toPx()
        val dashGap = 4.dp.toPx()
        val pixelHeight = 1.dp.toPx()

        fun drawDashes(startX: Float, endX: Float) {
            var x = startX
            while (x < endX) {
                drawRect(
                    color = theme.mutedColor.copy(alpha = 0.55f),
                    topLeft = Offset(x, centerY - pixelHeight / 2f),
                    size = Size(
                        width = dashWidth.coerceAtMost(endX - x),
                        height = pixelHeight,
                    ),
                )
                x += dashWidth + dashGap
            }
        }

        drawDashes(0f, centerX - pulseHalfWidth)
        drawDashes(centerX + pulseHalfWidth, size.width)

        val pulse = Path().apply {
            moveTo(centerX - pulseHalfWidth, centerY)
            lineTo(centerX - 10.dp.toPx(), centerY)
            lineTo(centerX - 7.dp.toPx(), centerY - 2.dp.toPx())
            lineTo(centerX - 3.dp.toPx(), centerY + 4.dp.toPx())
            lineTo(centerX + 1.dp.toPx(), centerY - 7.dp.toPx())
            lineTo(centerX + 5.dp.toPx(), centerY + 3.dp.toPx())
            lineTo(centerX + 9.dp.toPx(), centerY)
            lineTo(centerX + pulseHalfWidth, centerY)
        }
        drawPath(
            path = pulse,
            brush = theme.controlsBrush,
            style = Stroke(
                width = 1.dp.toPx(),
                join = StrokeJoin.Miter,
            ),
        )
    }
}

@Composable
private fun ThemePixelRoundedFrame(
    brush: Brush,
    cornerSize: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = THEME_SWITCHER_FRAME_BACKGROUND_COLOUR,
    fillBrush: Brush? = null,
) {
    Canvas(modifier = modifier) {
        val step = (cornerSize.toPx() / 3f)
            .coerceAtMost(size.minDimension / 8f)
        val right = size.width
        val bottom = size.height
        val path = Path().apply {
            moveTo(step * 3f, 0f)
            lineTo(right - step * 3f, 0f)
            lineTo(right - step * 3f, step)
            lineTo(right - step * 2f, step)
            lineTo(right - step * 2f, step * 2f)
            lineTo(right - step, step * 2f)
            lineTo(right - step, step * 3f)
            lineTo(right, step * 3f)
            lineTo(right, bottom - step * 3f)
            lineTo(right - step, bottom - step * 3f)
            lineTo(right - step, bottom - step * 2f)
            lineTo(right - step * 2f, bottom - step * 2f)
            lineTo(right - step * 2f, bottom - step)
            lineTo(right - step * 3f, bottom - step)
            lineTo(right - step * 3f, bottom)
            lineTo(step * 3f, bottom)
            lineTo(step * 3f, bottom - step)
            lineTo(step * 2f, bottom - step)
            lineTo(step * 2f, bottom - step * 2f)
            lineTo(step, bottom - step * 2f)
            lineTo(step, bottom - step * 3f)
            lineTo(0f, bottom - step * 3f)
            lineTo(0f, step * 3f)
            lineTo(step, step * 3f)
            lineTo(step, step * 2f)
            lineTo(step * 2f, step * 2f)
            lineTo(step * 2f, step)
            lineTo(step * 3f, step)
            close()
        }

        if (fillBrush != null) {
            drawPath(path = path, brush = fillBrush)
        } else {
            drawPath(path = path, color = backgroundColor)
        }
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(
                width = 1.dp.toPx(),
                join = StrokeJoin.Miter,
            ),
        )
    }
}
