package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily

@Composable
internal fun BootLog(
    lines: List<BootLine>,
    currentLine: BootLine?,
    currentText: String,
    isCurrentStatusVisible: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier,
    glitchTint: Color? = null,
) {
    Column(
        modifier = modifier.graphicsLayer(alpha = alpha),
    ) {
        lines.forEach { line ->
            BootLineText(
                message = line.message,
                status = line.status,
                isStatusVisible = true,
                glitchTint = glitchTint,
            )
        }

        if (currentLine != null && currentText.isNotBlank()) {
            BootLineText(
                message = currentText,
                status = currentLine.status,
                isStatusVisible = isCurrentStatusVisible,
                glitchTint = glitchTint,
            )
        }
    }
}

@Composable
internal fun BootLineText(
    message: String,
    status: BootStatus,
    isStatusVisible: Boolean,
    modifier: Modifier = Modifier,
    glitchTint: Color? = null,
) {
    val lineColor = glitchTint ?: BOOT_BASE_COLOR

    Text(
        text = if (glitchTint == null) {
            bootLineText(
                message = message,
                status = status,
                isStatusVisible = isStatusVisible,
            )
        } else {
            plainBootLineText(
                message = message,
                status = status,
                isStatusVisible = isStatusVisible,
            )
        },
        color = lineColor,
        fontFamily = KsenaxFontFamily.tiny5,
        fontSize = BOOT_LINE_FONT_SIZE,
        lineHeight = BOOT_LINE_HEIGHT,
        maxLines = 1,
        style = TextStyle(
            shadow = Shadow(
                color = (glitchTint ?: BOOT_BLUE).copy(
                    alpha = if (glitchTint == null) 0.28f else 0.72f,
                ),
                offset = Offset.Zero,
                blurRadius = if (glitchTint == null) 5f else 8f,
            ),
        ),
        modifier = modifier,
    )
}
