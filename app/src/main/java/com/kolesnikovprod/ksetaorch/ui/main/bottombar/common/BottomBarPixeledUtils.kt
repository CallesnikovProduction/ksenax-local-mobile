package com.kolesnikovprod.ksetaorch.ui.main.bottombar.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush
import kotlin.math.min

/**
 * Composable-кнопка с кастомной пиксельной рамкой (скелет построения).
 *
 * @param modifier внешний модификатор кнопки
 * @param onClick действие при нажатии
 * @param frameBrush градиентная кисть рамки из `KsenaxGradientFamily.kt`.
 * @param content содержимое внутри кнопки
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
@Composable
fun ButtonAsPixeledFrame(
    modifier:   Modifier   = Modifier,
    onClick:    () -> Unit = {},
    frameBrush: Brush      = sunsetBottomBarGradientBrush,
    content:    @Composable BoxScope.() -> Unit,
) {
    // Кликабельность кнопки, но без зависимости от Material-анимации нажатия.
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(41.dp)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(41.dp),
        ) {

            /*
             * Важно здесь понимать, что:
             * - (0, 0)            ->  левый верхний угол
             * - (size.width, 0)   ->  правый верхний
             * - (0, size.height)  ->  левый низ
             */

            val pixel = 1.dp.toPx()      // переведено в пиксели через dp
            val stroke = pixel * 2       // толщина строки
            val bg = Color(0xFF05070A)   // цвет заднего фона

            // Строится первый слой со сдвигом на stroke пикселей, поскольку
            // эти stroke пикселей займёт сама строка
            drawRect(
                color = bg,
                topLeft = Offset(stroke, stroke),
                size = Size(size.width - stroke * 2, size.height - stroke * 2),
            )

            // Рисуется верхняя сторона рамки, которая начинается не сначала,
            // вежь она будет потом дорисовываться отдельным смещением пикселей
            drawRect(
                brush = frameBrush,
                topLeft = Offset(stroke, 0f),
                size = Size(size.width - stroke * 2, stroke),
            )

            // Нижняя
            drawRect(
                brush = frameBrush,
                topLeft = Offset(stroke, size.height - stroke),
                size = Size(size.width - stroke * 2, stroke),
            )

            // Левая рамка
            drawRect(
                brush = frameBrush,
                topLeft = Offset(0f, stroke),
                size = Size(stroke, size.height - stroke * 2),
            )

            // Правая рамка
            drawRect(
                brush = frameBrush,
                topLeft = Offset(size.width - stroke, stroke),
                size = Size(stroke, size.height - stroke * 2),
            )

            /* Пиксели по бокам, создающие ощущение пиксель-арта */
            drawRect(frameBrush,
                Offset(stroke, stroke), Size(stroke, stroke)
            )
            drawRect(frameBrush,
                Offset(size.width - stroke * 2, stroke), Size(stroke, stroke)
            )
            drawRect(frameBrush,
                Offset(stroke, size.height - stroke * 2), Size(stroke, stroke)
            )
            drawRect(frameBrush,
                Offset(size.width - stroke * 2, size.height - stroke * 2), Size(stroke, stroke)
            )
        }

        // вызывается само содержимое ВНУТРИ кнопки
        content()
    }
}

/**
 * Пиксельный крестик, который указывает на недоступную функцию *(например, к микрофону
 * нет прав, следовательно, будет перекрыта иконка микрофона)*.
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
@Composable
fun PixelPermissionDeniedCross(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // 7х7
        val cell = min(size.width, size.height) / 7f

        // отступы слева и сверху (центрирование Canvas)
        val left = (size.width - cell * 7f) / 2f
        val top = (size.height - cell * 7f) / 2f

        // Тень немного сдвинута вправо и вниз
        val shadowOffset = Offset(cell * 0.22f, cell * 0.22f)

        val shadowColor = Color(0xFF170A24).copy(alpha = 0.92f)
        val crossColor = Color(0xFF9E5A86)

        repeat(7) { index ->

            /**
             * Координаты задаются по такому принципу через индекс, дабы получилось вот такое:
             *
             * ■     ■
             *  ■   ■
             *   ■ ■
             *    ■
             *   ■ ■
             *  ■   ■
             * ■     ■
             *
             */

            val points = listOf(
                Offset(left + cell * index, top + cell * index),
                Offset(left + cell * (6 - index), top + cell * index),
            )

            points.forEach { point ->
                drawRect(
                    color = shadowColor,
                    topLeft = point + shadowOffset,
                    size = Size(cell, cell),
                )
                drawRect(
                    color = crossColor,
                    topLeft = point,
                    size = Size(cell, cell),
                )
            }
        }

        // Два дополнительных пикселя для плотности
        drawRect(
            color = crossColor,
            topLeft = Offset(left + cell, top + cell),
            size = Size(cell, cell),
        )
        drawRect(
            color = crossColor,
            topLeft = Offset(left + cell * 5f, top + cell),
            size = Size(cell, cell),
        )
    }
}

/**
 * Отрисовка самого bottom-бара, не кнопок.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
internal fun BottomBarFrame(
    frameBrush: Brush,
    modifier:   Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 1.5.dp.toPx()
        val stroke = pixel * 2
        val bg = Color(0xFF05070A)

        drawRect(
            color = bg,
            topLeft = Offset(stroke, stroke),
            size = Size(size.width - stroke * 2, size.height - stroke * 2),
        )

        drawRect(
            brush = frameBrush,
            topLeft = Offset(stroke * 3, 0f),
            size = Size(size.width - stroke * 6, stroke),
        )
        drawRect(
            brush = frameBrush,
            topLeft = Offset(stroke * 3, size.height - stroke),
            size = Size(size.width - stroke * 6, stroke),
        )
        drawRect(
            brush = frameBrush,
            topLeft = Offset(0f, stroke * 3),
            size = Size(stroke, size.height - stroke * 6),
        )
        drawRect(
            brush = frameBrush,
            topLeft = Offset(size.width - stroke, stroke * 3),
            size = Size(stroke, size.height - stroke * 6),
        )

        drawRect(frameBrush, Offset(stroke * 2, stroke), Size(stroke, stroke))
        drawRect(Color.Black, Offset(stroke, stroke), Size(stroke, stroke))
        drawRect(frameBrush, Offset(stroke, stroke * 2), Size(stroke, stroke))

        drawRect(frameBrush, Offset(size.width - stroke * 3, stroke), Size(stroke, stroke))
        drawRect(frameBrush, Offset(size.width - stroke * 2, stroke * 2), Size(stroke, stroke))

        drawRect(frameBrush, Offset(stroke, size.height - stroke * 3), Size(stroke, stroke))
        drawRect(frameBrush, Offset(stroke * 2, size.height - stroke * 2), Size(stroke, stroke))

        drawRect(frameBrush, Offset(size.width - stroke * 2, size.height - stroke * 3), Size(stroke, stroke))
        drawRect(frameBrush, Offset(size.width - stroke * 3, size.height - stroke * 2), Size(stroke, stroke))
    }
}