package com.kolesnikovprod.ksetaorch.ui.main.topbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelSquareFrame
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed

@Composable
fun PixelMenuButton(
    brush: Brush,
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .graphicsLayer(rotationZ = rotation),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        val pressedBrush = brush.whileKsenaxPressed(pressed)

        PixelSquareFrame(
            brush = pressedBrush,
            modifier = Modifier.matchParentSize(),
        )

        PixelMenuGlyph(
            brush = pressedBrush,
            modifier = Modifier.size(width = 33.dp, height = 27.dp),
        )
    }
}

@Composable
private fun PixelMenuGlyph(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val lineHeight = 3.dp.toPx()
        val gap = 4.dp.toPx()
        val lineWidth = size.width * 0.60f
        val startX = (size.width - lineWidth) / 2f
        val startY = (size.height - lineHeight * 3f - gap * 2f) / 2f

        repeat(3) { index ->
            drawRect(
                brush = brush,
                topLeft = Offset(startX, startY + (lineHeight + gap) * index),
                size = Size(lineWidth, lineHeight),
            )
        }
    }
}
