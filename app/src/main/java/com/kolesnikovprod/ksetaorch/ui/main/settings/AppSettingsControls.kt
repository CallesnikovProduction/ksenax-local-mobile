package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.alternativeMainGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.aquaSunsetLightBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetLightBrush

@Composable
internal fun SettingsSectionFrame(
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
            brush = alternativeMainGradientBrush,
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
                            brush = sunsetBottomBarGradientBrush,
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
                        brush = alternativeMainGradientBrush,
                        fontFamily = KsenaxFontFamily.jersey10,
                        modifier = Modifier.layoutAwareOffsetX(titleOffsetX),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PixelDottedLine(
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
            brush = sunsetBottomBarGradientBrush,
            modifier = Modifier.size(39.dp),
        )

        Spacer(modifier = Modifier.width(10.dp))

        GradientText(
            text = label,
            fontFamily = KsenaxFontFamily.minecraftFont,
            fontSize = labelFontSize,
            lineHeight = labelFontSize * 1.25f,
            brush = sunsetLightBrush,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))
        control()
    }
}

@Composable
internal fun SettingsPickerButton(
    value: String,
    onChooseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.width(148.dp)) {
        PixelSelectorSurface(
            value = value,
            onClick = { expanded = true },
        )

        SettingsDropdownMenu(
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
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.width(148.dp)) {
        PixelSelectorSurface(
            value = text,
            onClick = onClick,
        )
    }
}

@Composable
internal fun ContextWindowPicker(
    selected: KsenaxContextWindow,
    onSelected: (KsenaxContextWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.width(148.dp)) {
        PixelSelectorSurface(
            value = selected.label,
            onClick = { expanded = true },
        )

        SettingsDropdownMenu(
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
    value: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        PixelWideFrame(
            brush = alternativeMainGradientBrush,
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
                color = Color.White,
                fontFamily = KsenaxFontFamily.minecraftFont,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            PixelDownArrow(
                modifier = Modifier.size(width = 14.dp, height = 10.dp),
            )
        }
    }
}

@Composable
private fun SettingsDropdownMenu(
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
            DropdownMenuItem(
                text = {
                    Text(
                        text = option,
                        color = if (option == selectedOption) {
                            Color(0xFF8EF7C9)
                        } else {
                            Color(0xFFD7DDEA)
                        },
                        fontFamily = KsenaxFontFamily.minecraftFont,
                        fontSize = 10.sp,
                    )
                },
                onClick = { onOptionSelected(option) },
            )
        }
    }
}

@Composable
internal fun SettingsSectionDivider() {
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
                color = Color(0xFF24264B),
                topLeft = Offset(x, 0f),
                size = Size(dashWidth, size.height),
            )
            x += dashWidth + gap
        }
    }
}

@Composable
private fun PixelDottedLine(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 2.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = Color(0xFF29306A),
                topLeft = Offset(x, (size.height - pixel) / 2f),
                size = Size(pixel, pixel),
            )
            x += pixel * 3f
        }
    }
}

@Composable
private fun PixelDownArrow(
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
                brush = aquaSunsetLightBrush,
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
