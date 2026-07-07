package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.components.PixelSquareFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.aquaSunsetLightBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush

@Composable
internal fun SettingsTopScrollShadow(
    strength: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (strength <= 0f) return@Canvas

        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF03050A).copy(alpha = 0.88f * strength),
                0.28f to Color(0xFF03050A).copy(alpha = 0.62f * strength),
                0.64f to Color(0xFF03050A).copy(alpha = 0.22f * strength),
                1f to Color.Transparent,
            ),
        )

        val pixel = 2.dp.toPx()
        val step = pixel * 2.6f
        var x = 0f
        var index = 0

        while (x < size.width) {
            val dispersion = (index * 37 % 13) / 13f
            val y = size.height * (0.58f + dispersion * 0.34f)
            val alpha = (0.025f + dispersion * 0.055f) * strength

            drawRect(
                color = Color.Black.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(
                    width = pixel * if (index % 3 == 0) 2f else 1f,
                    height = pixel,
                ),
            )

            x += step
            index += 1
        }
    }
}

@Composable
internal fun AppSettingsTopBar(
    hasUnsavedChanges: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(68.dp),
    ) {
        SettingsTopBarButton(
            brush = sunsetBottomBarGradientBrush,
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            PixelBackArrow(
                brush = sunsetBottomBarGradientBrush,
                modifier = Modifier.size(23.dp),
            )
        }

        SettingsTopBarButton(
            brush = if (hasUnsavedChanges) {
                aquaSunsetLightBrush
            } else {
                inactiveGradientBrush
            },
            onClick = onSaveClick,
            enabled = hasUnsavedChanges,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            PixelCheckMark(
                brush = if (hasUnsavedChanges) {
                    aquaSunsetLightBrush
                } else {
                    inactiveGradientBrush
                },
                modifier = Modifier
                    .size(25.dp)
                    .offset(x = (-2).dp, y = 1.dp),
            )
        }
    }
}

@Composable
private fun SettingsTopBarButton(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PixelSquareFrame(
            brush = brush,
            modifier = Modifier.matchParentSize(),
            backgroundColor = Color(0xD9050810),
        )
        content()
    }
}

@Composable
private fun PixelBackArrow(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = size.minDimension / 7f
        val points = listOf(
            1 to 3,
            2 to 2,
            2 to 3,
            2 to 4,
            3 to 1,
            3 to 3,
            3 to 5,
            4 to 3,
            5 to 3,
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

@Composable
private fun PixelCheckMark(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = size.minDimension / 7f
        val points = listOf(
            1 to 3,
            2 to 4,
            3 to 5,
            4 to 4,
            4 to 3,
            5 to 2,
            6 to 1,
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
