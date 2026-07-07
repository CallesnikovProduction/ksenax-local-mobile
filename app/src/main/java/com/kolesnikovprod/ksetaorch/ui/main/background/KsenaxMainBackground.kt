package com.kolesnikovprod.ksetaorch.ui.main.background

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.main.background.common.TwinklingStarsLayer

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
    showScenicOverlay: Boolean  = true,
    modifier:          Modifier = Modifier,
) {
    Image(
        painter            = painterResource(R.drawable.main_bg_fundamental),
        contentDescription = null,
        contentScale       = ContentScale.Crop, // картинка заполнит полностью область
        modifier           = modifier,
    )

    // Поверх картинки рисуется слой мерцающих звёзд
    TwinklingStarsLayer(
        modifier = modifier,
    )

    if (showScenicOverlay) {
        Image(
            painter            = painterResource(R.drawable.main_bg_bottom_pic),
            contentDescription = null,
            contentScale       = ContentScale.Crop, // картинка заполнит полностью область
            modifier           = modifier,
        )
    }
}