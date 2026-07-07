package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.roundToInt

internal fun DrawScope.drawGlitchTriangle(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    mirror: Boolean,
) {
    val path = Path().apply {
        if (mirror) {
            moveTo(left + width, top)
            lineTo(left + width, top + height)
            lineTo(left, top + height * 0.5f)
        } else {
            moveTo(left, top)
            lineTo(left + width, top + height * 0.48f)
            lineTo(left, top + height)
        }
        close()
    }

    drawPath(
        path = path,
        color = color,
    )
}

internal fun DrawScope.drawPixelTriangle(
    left: Float,
    top: Float,
    widthInPixels: Int,
    heightInPixels: Int,
    pixel: Float,
    color: Color,
    mirror: Boolean,
) {
    repeat(heightInPixels) { row ->
        val normalizedRow = if (heightInPixels <= 1) {
            1f
        } else {
            row / (heightInPixels - 1f)
        }
        val rowWidthInPixels = (1f + normalizedRow * (widthInPixels - 1))
            .roundToInt()
            .coerceAtLeast(1)
        val rowWidth = rowWidthInPixels * pixel
        val rowLeft = if (mirror) {
            left + widthInPixels * pixel - rowWidth
        } else {
            left
        }

        drawRect(
            color = color,
            topLeft = Offset(rowLeft, top + row * pixel),
            size = Size(rowWidth, pixel),
        )

        if (row > 1 && row % 3 == 0 && rowWidthInPixels > 3) {
            drawRect(
                color = BOOT_BACKGROUND.copy(alpha = color.alpha * 0.72f),
                topLeft = Offset(
                    x = if (mirror) rowLeft + pixel else rowLeft + rowWidth - pixel * 2f,
                    y = top + row * pixel,
                ),
                size = Size(pixel, pixel),
            )
        }
    }
}

internal fun DrawScope.drawGlitchClusterArtifact(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    pixel: Float,
) {
    drawRect(
        color = color,
        topLeft = Offset(left, top + height * 0.15f),
        size = Size(width * 0.72f, pixel),
    )
    drawRect(
        color = color.copy(alpha = color.alpha * 0.84f),
        topLeft = Offset(left + width * 0.34f, top + height * 0.44f),
        size = Size(width * 0.48f, pixel * 2f),
    )
    drawRect(
        color = color.copy(alpha = color.alpha * 0.72f),
        topLeft = Offset(left + width * 0.12f, top + height * 0.74f),
        size = Size(width * 0.88f, pixel),
    )
    drawRect(
        color = Color.Black.copy(alpha = color.alpha * 0.36f),
        topLeft = Offset(left + width * 0.48f, top + height * 0.30f),
        size = Size(pixel * 2f, height * 0.48f),
    )
}
