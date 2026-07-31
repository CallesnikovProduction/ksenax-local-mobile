package com.kolesnikovprod.ksetaorch.ui.main.background

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kolesnikovprod.ksetaorch.ui.main.background.common.TwinklingStarsLayer
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals

/**
 * Главная функция по ЗАДНЕМУ ФОНУ со звёздочками + горы, дорога, закат.
 *
 * @param showScenicOverlay когда чата нет — показывается красивый scenic overlay.
 * Когда чат открыт — убирается, чтобы не мешал сообщениям.
 * @param themeBackgroundSaturation цветность картинок и звёзд в диапазоне
 * `0f..1f`: от полного Ч/Б до исходного оформления темы.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxMainBackground(
    theme: KsenaxThemeVisuals,
    showScenicOverlay: Boolean = true,
    themeBackgroundSaturation: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val normalizedSaturation = themeBackgroundSaturation.coerceIn(0f, 1f)
    val themeImageColorFilter = remember(normalizedSaturation) {
        if (normalizedSaturation >= 1f) {
            null
        } else {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(normalizedSaturation)
                },
            )
        }
    }
    val sparkleColors = remember(
        theme.sparkleColors,
        normalizedSaturation,
    ) {
        if (normalizedSaturation >= 1f) {
            theme.sparkleColors
        } else {
            theme.sparkleColors.map { color ->
                color.withKsenaxSaturation(normalizedSaturation)
            }
        }
    }

    Image(
        painter            = painterResource(theme.backgroundDrawableRes),
        contentDescription = null,
        contentScale       = ContentScale.Crop, // картинка заполнит полностью область
        colorFilter        = themeImageColorFilter,
        modifier           = modifier,
    )

    // Поверх картинки рисуется слой мерцающих звёзд
    TwinklingStarsLayer(
        sparkleColors = sparkleColors,
        modifier = modifier,
    )

    if (showScenicOverlay) {
        Image(
            painter            = painterResource(theme.foregroundDrawableRes),
            contentDescription = null,
            contentScale       = ContentScale.Crop, // картинка заполнит полностью область
            colorFilter        = themeImageColorFilter,
            modifier           = modifier,
        )
    }
}

private fun Color.withKsenaxSaturation(saturation: Float): Color {
    val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
    return Color(
        red = luminance + (red - luminance) * saturation,
        green = luminance + (green - luminance) * saturation,
        blue = luminance + (blue - luminance) * saturation,
        alpha = alpha,
    )
}
