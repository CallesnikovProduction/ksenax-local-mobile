package com.kolesnikovprod.ksetaorch.ui.main.background

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kolesnikovprod.ksetaorch.ui.main.background.common.TwinklingStarsLayer
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals

/**
 * Главная функция по ЗАДНЕМУ ФОНУ со звёздочками + горы, дорога, закат.
 *
 * @param showScenicOverlay когда чата нет — показывается красивый scenic overlay.
 * Когда чат открыт — убирается, чтобы не мешал сообщениям.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxMainBackground(
    theme: KsenaxThemeVisuals,
    showScenicOverlay: Boolean  = true,
    modifier:          Modifier = Modifier,
) {
    Image(
        painter            = painterResource(theme.backgroundDrawableRes),
        contentDescription = null,
        contentScale       = ContentScale.Crop, // картинка заполнит полностью область
        modifier           = modifier,
    )

    // Поверх картинки рисуется слой мерцающих звёзд
    TwinklingStarsLayer(
        sparkleColors = theme.sparkleColors,
        modifier = modifier,
    )

    if (showScenicOverlay) {
        Image(
            painter            = painterResource(theme.foregroundDrawableRes),
            contentDescription = null,
            contentScale       = ContentScale.Crop, // картинка заполнит полностью область
            modifier           = modifier,
        )
    }
}
