/*
 * Компонент для иконок, которым нужен градиент вместо одноцветного Material tint.
 * Файл хранит весь графический приём в одном месте, чтобы экраны не повторяли
 * настройку offscreen-композиции и BlendMode.
 */
package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush

/**
 * Рисует drawable как Material-иконку и окрашивает его переданным градиентом.
 *
 * Сначала [Icon] создаёт белую маску drawable. Затем `drawWithCache` рисует
 * [brush] поверх содержимого с режимом [BlendMode.SrcAtop]. Offscreen-слой
 * ограничивает градиент непрозрачными пикселями иконки.
 *
 * @param drawableId идентификатор drawable-ресурса с формой иконки.
 * @param contentDescription текст для accessibility-сервисов; `null` подходит
 * для декоративной иконки.
 * @param modifier внешний размер, позиционирование и поведение компонента.
 * @param brush градиент иконки. По умолчанию используется градиент нижней панели.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun GradientIcon(
    drawableId:         Int,
    contentDescription: String?,
    modifier:           Modifier = Modifier,
    brush:              Brush = sunsetBottomBarGradientBrush,
) {
    Icon(
        painter = painterResource(drawableId),
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
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
