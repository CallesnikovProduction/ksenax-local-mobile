package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ACTIVE_LIGHT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CARD_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CURRENT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ERROR_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_INACTIVE_PIXEL_COLOUR
import kotlin.math.roundToInt

/**
 * Тёмная подложка внутренних validation-панелей.
 *
 * @since 0.3
 */
internal val VerificationPanelBackground = OVERLAY_CARD_BACKGROUND_COLOUR

/**
 * Рисует пиксельный drawable без сглаживания и окрашивает его указанной
 * кистью через offscreen-mask.
 *
 * @since 0.3
 */
@Composable
internal fun PixelVerificationBrushIcon(
    @DrawableRes drawableId: Int,
    brush: Brush,
    accessibilityDescription: String?,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(id = drawableId)
    Canvas(
        modifier = modifier
            .semantics {
                accessibilityDescription?.let { description ->
                    contentDescription = description
                }
            }
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
    ) {
        drawImage(
            image = image,
            dstSize = IntSize(
                width = size.width.roundToInt(),
                height = size.height.roundToInt(),
            ),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Цвет визуального состояния validation-этапа.
 *
 * @since 0.3
 */
internal val KsenaxModelVerificationStatus.colour: Color
    get() = when (this) {
        KsenaxModelVerificationStatus.Pending ->
            OVERLAY_INACTIVE_PIXEL_COLOUR
        KsenaxModelVerificationStatus.Active ->
            OVERLAY_ACTIVE_LIGHT_MINT_COLOUR
        KsenaxModelVerificationStatus.Success ->
            OVERLAY_CURRENT_MINT_COLOUR
        KsenaxModelVerificationStatus.Failure -> OVERLAY_ERROR_COLOUR
    }

/**
 * Короткая подпись визуального состояния validation-этапа.
 *
 * @since 0.3
 */
internal val KsenaxModelVerificationStatus.statusText: String
    get() = when (this) {
        KsenaxModelVerificationStatus.Pending -> "ожидание"
        KsenaxModelVerificationStatus.Active -> "проверка"
        KsenaxModelVerificationStatus.Success -> "OK"
        KsenaxModelVerificationStatus.Failure -> "ошибка"
    }
