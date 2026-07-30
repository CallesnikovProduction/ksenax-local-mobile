package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Сегментированный пиксельный progress bar для install-состояний.
 *
 * Процент рисуется поверх сегментов. Каждая цифра темнеет отдельно только после
 * того, как заполнение физически дошло до её центра.
 *
 * @since 0.3
 */
@Composable
fun PixelSegmentedProgressBar(
    progress: Float,
    activeBrush: Brush,
    borderBrush: Brush,
    activeTextColor: Color,
    coveredTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val percentText = "${(safeProgress * 100f).toInt().coerceIn(0, 100)}%"

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.dp.toPx()
            val edge = strokeWidth / 2f
            val step = 3.dp.toPx()
            val corner = step * 2f
            val left = edge
            val top = edge
            val right = size.width - edge
            val bottom = size.height - edge
            val framePath = Path().apply {
                moveTo(left + corner, top)
                lineTo(right - corner, top)
                lineTo(right - corner, top + step)
                lineTo(right - step, top + step)
                lineTo(right - step, top + corner)
                lineTo(right, top + corner)
                lineTo(right, bottom - corner)
                lineTo(right - step, bottom - corner)
                lineTo(right - step, bottom - step)
                lineTo(right - corner, bottom - step)
                lineTo(right - corner, bottom)
                lineTo(left + corner, bottom)
                lineTo(left + corner, bottom - step)
                lineTo(left + step, bottom - step)
                lineTo(left + step, bottom - corner)
                lineTo(left, bottom - corner)
                lineTo(left, top + corner)
                lineTo(left + step, top + corner)
                lineTo(left + step, top + step)
                lineTo(left + corner, top + step)
                close()
            }

            drawPath(
                path = framePath,
                color = Color(0xE8070A13),
            )
            drawPath(
                path = framePath,
                brush = borderBrush,
                style = Stroke(
                    width = strokeWidth,
                    join = StrokeJoin.Miter,
                ),
            )

            val horizontalInset = 10.dp.toPx()
            val verticalInset = 10.dp.toPx()
            val segmentGap = 1.dp.toPx()
            val preferredSegmentWidth = 4.dp.toPx()
            val availableWidth = (size.width - horizontalInset * 2f)
                .coerceAtLeast(preferredSegmentWidth)
            val segmentCount = floor(
                (availableWidth + segmentGap) /
                    (preferredSegmentWidth + segmentGap),
            ).toInt().coerceAtLeast(1)
            val segmentWidth = (
                availableWidth - segmentGap * (segmentCount - 1)
            ) / segmentCount
            val segmentHeight = (size.height - verticalInset * 2f)
                .coerceAtLeast(1f)
            val activeCount = when {
                safeProgress <= 0f -> 0
                else -> ceil(segmentCount * safeProgress)
                    .toInt()
                    .coerceIn(0, segmentCount)
            }

            repeat(segmentCount) { index ->
                val topLeft = Offset(
                    x = horizontalInset +
                        index * (segmentWidth + segmentGap),
                    y = verticalInset,
                )
                if (index < activeCount) {
                    drawRect(
                        brush = activeBrush,
                        topLeft = topLeft,
                        size = Size(segmentWidth, segmentHeight),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            percentText.forEachIndexed { index, character ->
                val characterCenterFraction =
                    0.82f + (index + 0.5f) * (0.18f / percentText.length)
                val isCovered =
                    character.isDigit() &&
                        safeProgress >= characterCenterFraction

                Text(
                    text = character.toString(),
                    color = if (isCovered) {
                        coveredTextColor
                    } else {
                        activeTextColor
                    },
                    fontFamily =
                        KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(9.dp),
                )
            }
        }
    }
}
