package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.themeSwitcherGradientBrush

@Composable
internal fun SettingsThemeSwitcherCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(),
    ) { pressed ->
        val pressedBrush = themeSwitcherGradientBrush.whileKsenaxPressed(pressed)

        PixelWideFrame(
            brush = pressedBrush,
            backgroundColor = Color(0xE1080712),
            modifier = Modifier.matchParentSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.settings_theme_chooser),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                colorFilter = ColorFilter.tint(Color.White).takeIf { pressed },
            )

            Spacer(modifier = Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                GradientText(
                    text = "THEME SWITCHER",
                    fontSize = 21.sp,
                    lineHeight = 22.sp,
                    brush = pressedBrush,
                    fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Кастомизация темы из выбранных",
                    color = Color(0xFFB8BBCF).whileKsenaxPressed(pressed),
                    fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionFrame(
    theme: KsenaxThemeVisuals,
    iconRes: Int,
    iconSize: Dp,
    title: String,
    titleOffsetX: Dp = 0.dp,
    iconModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        PixelWideFrame(
            brush = theme.settingsBrush,
            backgroundColor = Color(0xD9050810),
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(iconSize)
                            .height(29.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GradientIcon(
                            drawableId = iconRes,
                            contentDescription = null,
                            brush = theme.controlsBrush,
                            modifier = Modifier
                                .requiredSize(iconSize)
                                .then(iconModifier),
                        )
                    }

                    Spacer(modifier = Modifier.width(9.dp))

                    GradientText(
                        text = title,
                        fontSize = 21.sp,
                        lineHeight = 22.sp,
                        brush = theme.settingsBrush,
                        fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                        modifier = Modifier.layoutAwareOffsetX(titleOffsetX),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PixelDottedLine(
                    color = theme.mutedColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                )
            }

            Spacer(modifier = Modifier.height(11.dp))
            content()
        }
    }
}

private fun Modifier.layoutAwareOffsetX(offset: Dp): Modifier {
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val offsetPx = offset.roundToPx()
        val reportedWidth = (placeable.width + offsetPx).coerceIn(
            minimumValue = constraints.minWidth,
            maximumValue = constraints.maxWidth,
        )

        layout(reportedWidth, placeable.height) {
            placeable.placeRelative(x = offsetPx, y = 0)
        }
    }
}

@Composable
internal fun SettingsValueRow(
    theme: KsenaxThemeVisuals,
    iconRes: Int,
    label: String,
    labelFontSize: TextUnit,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIcon(
            drawableId = iconRes,
            contentDescription = null,
            brush = theme.controlsBrush,
            modifier = Modifier.size(39.dp),
        )

        Spacer(modifier = Modifier.width(10.dp))

        GradientText(
            text = label,
            fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
            fontSize = labelFontSize,
            lineHeight = labelFontSize * 1.25f,
            brush = theme.settingsBrush,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))
        control()
    }
}

@Composable
internal fun SettingsPickerButton(
    theme: KsenaxThemeVisuals,
    value: String,
    onChooseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.width(148.dp)) {
        PixelSelectorSurface(
            theme = theme,
            value = value,
            onClick = { expanded = true },
        )

        SettingsDropdownMenu(
            theme = theme,
            expanded = expanded,
            options = listOf("Выбрать"),
            selectedOption = null,
            onDismiss = { expanded = false },
            onOptionSelected = {
                expanded = false
                onChooseClick()
            },
        )
    }
}

@Composable
internal fun SettingsActionButton(
    theme: KsenaxThemeVisuals,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .width(128.dp)
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        FilledGradientPixelSurface(
            theme = theme,
            brush = theme.settingsBrush.whileKsenaxPressed(pressed),
            modifier = Modifier.matchParentSize(),
        )

        Text(
            text = text,
            color = Color.Black,
            fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
            fontSize = 14.sp,
        )
    }
}

/**
 * Заполняет широкую ступенчатую кнопку градиентом активной темы.
 *
 * @since 0.3
 */
@Composable
private fun FilledGradientPixelSurface(
    theme: KsenaxThemeVisuals,
    brush: androidx.compose.ui.graphics.Brush = theme.settingsBrush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 4.dp.toPx()

        // Та же геометрия заполнения, что у PixelWideFrame.
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 3f, pixel),
            size = Size(
                size.width - pixel * 6f,
                size.height - pixel * 2f,
            ),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel, pixel * 3f),
            size = Size(
                size.width - pixel * 2f,
                size.height - pixel * 6f,
            ),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, pixel * 2f),
            size = Size(pixel, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(size.width - pixel * 3f, pixel * 2f),
            size = Size(pixel, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, size.height - pixel * 3f),
            size = Size(pixel, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(
                size.width - pixel * 3f,
                size.height - pixel * 3f,
            ),
            size = Size(pixel, pixel),
        )

        // Контур повторяет PixelWideFrame, но сливается с градиентной заливкой.
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 3f, 0f),
            size = Size(size.width - pixel * 6f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 3f, size.height - pixel),
            size = Size(size.width - pixel * 6f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(0f, pixel * 3f),
            size = Size(pixel, size.height - pixel * 6f),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(size.width - pixel, pixel * 3f),
            size = Size(pixel, size.height - pixel * 6f),
        )

        listOf(
            Offset(pixel, pixel * 2f),
            Offset(pixel * 2f, pixel),
            Offset(pixel, size.height - pixel * 3f),
            Offset(pixel * 2f, size.height - pixel * 2f),
            Offset(size.width - pixel * 2f, pixel * 2f),
            Offset(size.width - pixel * 3f, pixel),
            Offset(
                size.width - pixel * 2f,
                size.height - pixel * 3f,
            ),
            Offset(
                size.width - pixel * 3f,
                size.height - pixel * 2f,
            ),
        ).forEach { cornerPixel ->
            drawRect(
                brush = brush,
                topLeft = cornerPixel,
                size = Size(pixel, pixel),
            )
        }
    }
}

@Composable
internal fun ContextWindowPicker(
    theme: KsenaxThemeVisuals,
    selected: KsenaxContextWindow,
    onSelected: (KsenaxContextWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.width(148.dp)) {
        PixelSelectorSurface(
            theme = theme,
            value = selected.label,
            onClick = { expanded = true },
        )

        SettingsDropdownMenu(
            theme = theme,
            expanded = expanded,
            options = KsenaxContextWindow.entries.map(KsenaxContextWindow::label),
            selectedOption = selected.label,
            onDismiss = { expanded = false },
            onOptionSelected = { label ->
                KsenaxContextWindow.entries
                    .firstOrNull { option -> option.label == label }
                    ?.let(onSelected)
                expanded = false
            },
        )
    }
}

@Composable
private fun PixelSelectorSurface(
    theme: KsenaxThemeVisuals,
    value: String,
    onClick: () -> Unit,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        contentAlignment = Alignment.CenterStart,
    ) { pressed ->
        val pressedBrush = theme.settingsBrush.whileKsenaxPressed(pressed)

        PixelWideFrame(
            brush = pressedBrush,
            backgroundColor = Color(0xEC050811),
            modifier = Modifier.matchParentSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                color = Color.White.whileKsenaxPressed(pressed),
                fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            PixelDownArrow(
                brush = theme.selectedBrush.whileKsenaxPressed(pressed),
                modifier = Modifier.size(width = 14.dp, height = 10.dp),
            )
        }
    }
}

@Composable
private fun SettingsDropdownMenu(
    theme: KsenaxThemeVisuals,
    expanded: Boolean,
    options: List<String>,
    selectedOption: String?,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Color(0xFF080C14),
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
        modifier = Modifier.width(148.dp),
    ) {
        options.forEach { option ->
            KsenaxPressableBox(
                onClick = { onOptionSelected(option) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { pressed ->
                    Text(
                        text = option,
                        color = (
                            if (option == selectedOption) {
                                theme.accentColor
                            } else {
                                Color(0xFFD7DDEA)
                            }
                        ).whileKsenaxPressed(pressed),
                        fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                        fontSize = 10.sp,
                    )
            }
        }
    }
}

@Composable
internal fun SettingsSectionDivider(
    color: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val dashWidth = 4.dp.toPx()
        val gap = 5.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = color.copy(alpha = 0.36f),
                topLeft = Offset(x, 0f),
                size = Size(dashWidth, size.height),
            )
            x += dashWidth + gap
        }
    }
}

@Composable
private fun PixelDottedLine(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 2.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = color.copy(alpha = 0.72f),
                topLeft = Offset(x, (size.height - pixel) / 2f),
                size = Size(pixel, pixel),
            )
            x += pixel * 3f
        }
    }
}

@Composable
private fun PixelDownArrow(
    brush: androidx.compose.ui.graphics.Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = size.width / 7f
        val points = listOf(
            0 to 0,
            1 to 1,
            2 to 2,
            3 to 3,
            4 to 2,
            5 to 1,
            6 to 0,
        )
        points.forEach { (x, y) ->
            drawRect(
                brush = brush,
                topLeft = Offset(x * pixel, y * pixel),
                size = Size(pixel, pixel),
            )
        }
    }
}

internal val KsenaxTranscribingModel.settingsLabel: String
    get() = when (this) {
        KsenaxTranscribingModel.Gemma -> "Gemma STT"
        KsenaxTranscribingModel.Vosk -> "Vosk"
    }
