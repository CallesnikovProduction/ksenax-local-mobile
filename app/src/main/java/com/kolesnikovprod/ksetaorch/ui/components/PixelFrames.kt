/*
 * Canvas-компоненты для пиксельных рамок интерфейса.
 * Файл содержит две геометрии: компактную квадратную и широкую со ступенчатыми
 * углами. Оба компонента рисуют фон и рамку, но не размещают внутренний контент.
 */
package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Рисует компактную пиксельную рамку с шагом сетки `4.dp`.
 *
 * Два фоновых прямоугольника формируют тёмное заполнение без прямых внешних
 * углов. Четыре линии и четыре угловых пикселя поверх них образуют градиентный
 * контур. Родитель размещает содержимое отдельным слоем поверх или внутри
 * Canvas.
 *
 * Переданный размер должен быть не меньше `16.dp` по каждой оси, иначе
 * вычисляемые размеры прямоугольников станут отрицательными.
 *
 * @param brush кисть для четырёх сторон и углов рамки.
 * @param modifier задаёт размер и расположение Canvas.
 * @param backgroundColor цвет внутреннего заполнения.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun PixelSquareFrame(
    brush: Brush,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xEE03070D),
) {
    Canvas(modifier = modifier) {
        val pixel = 4.dp.toPx()

        drawRect(
            color = backgroundColor,
            topLeft = Offset(pixel * 2f, pixel),
            size = Size(size.width - pixel * 4f, size.height - pixel * 2f),
        )
        drawRect(
            color = backgroundColor,
            topLeft = Offset(pixel, pixel * 2f),
            size = Size(size.width - pixel * 2f, size.height - pixel * 4f),
        )

        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, 0f),
            size = Size(size.width - pixel * 4f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, size.height - pixel),
            size = Size(size.width - pixel * 4f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(0f, pixel * 2f),
            size = Size(pixel, size.height - pixel * 4f),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(size.width - pixel, pixel * 2f),
            size = Size(pixel, size.height - pixel * 4f),
        )

        drawRect(brush, Offset(pixel, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(pixel, size.height - pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, size.height - pixel * 2f), Size(pixel, pixel))
    }
}

/**
 * Рисует широкую пиксельную рамку со ступенчатыми углами.
 *
 * Компонент использует сетку `4.dp`. Фон складывается из центральных
 * прямоугольников и четырёх угловых пикселей. Градиентный контур состоит из
 * четырёх сторон и двух ступеней на каждом углу.
 *
 * Переданный размер должен быть не меньше `24.dp` по каждой оси. Компонент
 * рисует только рамку и фон; содержимое размещает вызывающий контейнер.
 *
 * @param brush кисть для сторон и ступенчатых углов рамки.
 * @param modifier задаёт размер и расположение Canvas.
 * @param backgroundColor цвет внутреннего заполнения.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun PixelWideFrame(
    brush: Brush,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xEE03070D),
) {
    Canvas(modifier = modifier) {
        val pixel = 4.dp.toPx()

        drawRect(
            color = backgroundColor,
            topLeft = Offset(pixel * 3f, pixel),
            size = Size(size.width - pixel * 6f, size.height - pixel * 2f),
        )
        drawRect(
            color = backgroundColor,
            topLeft = Offset(pixel, pixel * 3f),
            size = Size(size.width - pixel * 2f, size.height - pixel * 6f),
        )
        drawRect(backgroundColor, Offset(pixel * 2f, pixel * 2f), Size(pixel, pixel))
        drawRect(backgroundColor, Offset(size.width - pixel * 3f, pixel * 2f), Size(pixel, pixel))
        drawRect(backgroundColor, Offset(pixel * 2f, size.height - pixel * 3f), Size(pixel, pixel))
        drawRect(backgroundColor, Offset(size.width - pixel * 3f, size.height - pixel * 3f), Size(pixel, pixel))

        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 3f, 0f),
            size = Size(size.width - pixel * 6f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 3f, size.height - pixel),
            size = Size(size.width - pixel * 6f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(0f, pixel * 3f),
            size = Size(pixel, size.height - pixel * 6f),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(size.width - pixel, pixel * 3f),
            size = Size(pixel, size.height - pixel * 6f),
        )

        drawRect(brush, Offset(pixel, pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(pixel * 2f, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(pixel, size.height - pixel * 3f), Size(pixel, pixel))
        drawRect(brush, Offset(pixel * 2f, size.height - pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 3f, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, size.height - pixel * 3f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 3f, size.height - pixel * 2f), Size(pixel, pixel))
    }
}
