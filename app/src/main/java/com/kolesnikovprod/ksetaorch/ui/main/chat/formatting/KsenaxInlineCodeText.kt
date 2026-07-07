package com.kolesnikovprod.ksetaorch.ui.main.chat.formatting

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

private val InlineCodePixelBackground = Color(0xFF2A303A)

@Composable
internal fun KsenaxInlineCodeText(
    text: AnnotatedString,
    color: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    var textLayoutResult by remember(text) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    Text(
        text = text,
        color = color,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        onTextLayout = { result -> textLayoutResult = result },
        modifier = modifier.drawBehind {
            val layoutResult = textLayoutResult ?: return@drawBehind
            val pixel = 2.dp.toPx()
            val horizontalPadding = 2.dp.toPx()
            val verticalPadding = 1.dp.toPx()
            val annotations = text.getStringAnnotations(
                tag = InlineCodeAnnotationTag,
                start = 0,
                end = text.length,
            )

            annotations.forEach { annotation ->
                if (annotation.start >= annotation.end) return@forEach

                val firstLine = layoutResult.getLineForOffset(annotation.start)
                val lastLine = layoutResult.getLineForOffset(annotation.end - 1)

                for (lineIndex in firstLine..lastLine) {
                    val segmentStart = maxOf(
                        annotation.start,
                        layoutResult.getLineStart(lineIndex),
                    )
                    val segmentEnd = minOf(
                        annotation.end,
                        layoutResult.getLineEnd(lineIndex, visibleEnd = true),
                    )
                    if (segmentStart >= segmentEnd) continue

                    val firstCharacterBox = layoutResult.getBoundingBox(segmentStart)
                    val lastCharacterBox = layoutResult.getBoundingBox(segmentEnd - 1)
                    val left = (firstCharacterBox.left - horizontalPadding)
                        .coerceAtLeast(0f)
                    val right = (lastCharacterBox.right + horizontalPadding)
                        .coerceAtMost(size.width)
                    val top = (
                        minOf(firstCharacterBox.top, lastCharacterBox.top) -
                            verticalPadding
                        ).coerceAtLeast(0f)
                    val bottom = (
                        maxOf(firstCharacterBox.bottom, lastCharacterBox.bottom) +
                            verticalPadding
                        ).coerceAtMost(size.height)

                    drawPixelRoundedBackground(
                        color = InlineCodePixelBackground,
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        pixel = pixel,
                    )
                }
            }
        },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelRoundedBackground(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    pixel: Float,
) {
    val width = (right - left).coerceAtLeast(0f)
    val height = (bottom - top).coerceAtLeast(0f)
    if (width == 0f || height == 0f) return

    val cornerPixel = minOf(pixel, width / 2f, height / 2f)
    drawRect(
        color = color,
        topLeft = Offset(left + cornerPixel, top),
        size = Size(width - cornerPixel * 2f, height),
    )
    drawRect(
        color = color,
        topLeft = Offset(left, top + cornerPixel),
        size = Size(width, height - cornerPixel * 2f),
    )
}
