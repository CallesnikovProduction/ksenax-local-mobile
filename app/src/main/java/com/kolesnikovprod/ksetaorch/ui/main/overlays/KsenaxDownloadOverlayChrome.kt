package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.LocalKsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_BUTTON_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CARD_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ERROR_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_GRADIENT_MASK_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_NEGATIVE_BRUSH
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_PRIMARY_TEXT_COLOUR

@Composable
internal fun OverlayTextButton(
    text: String,
    color: Color,
    brush: Brush? = null,
    onClick: () -> Unit,
    showFrame: Boolean,
    modifier: Modifier = Modifier,
    textBrush: Brush? = null,
    enabled: Boolean = true,
) {
    val theme = LocalKsenaxThemeVisuals.current

    KsenaxPressableBox(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(43.dp)
            .widthIn(min = 112.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        if (showFrame) {
            PixelSteppedCornerFrame(
                brush = (brush ?: theme.overlayMainBrush).whileKsenaxPressed(pressed),
                backgroundColor = OVERLAY_BUTTON_BACKGROUND_COLOUR,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (textBrush == null) {
            Text(
                text = text,
                color = color.whileKsenaxPressed(pressed),
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
                fontSize = 14.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 15.dp),
            )
        } else {
            DownloadGradientText(
                text = text,
                brush = textBrush.whileKsenaxPressed(pressed),
                fontSize = 14.sp,
                lineHeight = 15.sp,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
                modifier = Modifier.padding(horizontal = 15.dp),
            )
        }
    }
}

@Composable
internal fun CancelConfirmationCard(
    onConfirmCancelClick: () -> Unit,
    onKeepDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    PixelOverlayCard(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 310.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 22.dp,
                vertical = 22.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Отменить загрузку модели?",
                color = OVERLAY_PRIMARY_TEXT_COLOUR,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverlayTextButton(
                    text = "НЕТ",
                    color = theme.mutedColor,
                    onClick = onKeepDownloadClick,
                    showFrame = false,
                )
                OverlayTextButton(
                    text = "ДА",
                    color = OVERLAY_ERROR_COLOUR,
                    onClick = onConfirmCancelClick,
                    showFrame = true,
                    brush = OVERLAY_NEGATIVE_BRUSH
                )
            }
        }
    }
}

/**
 * Общая пиксельная карточка overlay.
 *
 * Пустой tap-detector не объявляет карточку кнопкой в accessibility-дереве,
 * но не пропускает нажатие к fullscreen-dismiss слою позади неё. Реальные
 * кнопки внутри content сохраняют собственную обработку.
 *
 * @since 0.3
 */
@Composable
internal fun PixelOverlayCard(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    val theme = LocalKsenaxThemeVisuals.current

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {})
        },
    ) {
        PixelSteppedCornerFrame(
            brush = theme.overlayMainBrush,
            backgroundColor = OVERLAY_CARD_BACKGROUND_COLOUR,
            strokeWidth = borderWidth,
            modifier = Modifier.matchParentSize(),
        )
        content()
    }
}

/**
 * Рисует тонкую градиентную рамку с двумя ортогональными пиксельными
 * ступенями на каждом углу.
 *
 * Компонент не размещает контент и не обрабатывает события, поэтому может
 * переиспользоваться install- и validation-overlay.
 *
 * @since 0.3
 */
@Composable
internal fun PixelSteppedCornerFrame(
    brush: Brush,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 1.dp,
) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()
        val edge = strokeWidthPx / 2f
        val step = 4.dp.toPx()
        val corner = step * 2f
        val left = edge
        val top = edge
        val right = size.width - edge
        val bottom = size.height - edge
        val path = Path().apply {
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
            path = path,
            color = backgroundColor,
        )
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(
                width = strokeWidthPx,
                join = StrokeJoin.Miter,
            ),
        )
    }
}

@Composable
internal fun DownloadGradientText(
    text: String,
    brush: Brush,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = OVERLAY_GRADIENT_MASK_COLOUR,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        textAlign = TextAlign.Center,
        modifier = modifier
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.Offscreen,
            )
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
