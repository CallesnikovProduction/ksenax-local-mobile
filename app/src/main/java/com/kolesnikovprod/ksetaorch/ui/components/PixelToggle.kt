/*
 * Визуальная часть пиксельного переключателя.
 * Файл собирает фон-трек и растровую кнопку, но оставляет обработку нажатия и
 * изменение состояния вызывающему компоненту.
 */
package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.R
import kotlin.math.roundToInt

/** Зелёный градиент включённого состояния переключателя. */
private val PixelToggleEnabledBrush = Brush.linearGradient(
    listOf(
        Color(0xFFB8F7B5),
        Color(0xFF6EEFA1),
        Color(0xFF375D4A),
    ),
)

/** Розовый градиент выключенного состояния переключателя. */
private val PixelToggleDisabledBrush = Brush.linearGradient(
    listOf(
        Color(0xFFF0A6BE),
        Color(0xFFD26B98),
        Color(0xFF49303F),
    ),
)

internal fun pixelToggleStateBrush(isEnabled: Boolean): Brush {
    return if (isEnabled) {
        PixelToggleEnabledBrush
    } else {
        PixelToggleDisabledBrush
    }
}

/**
 * Рисует пиксельный индикатор включённого или выключенного состояния.
 *
 * [isEnabled] выбирает градиент, drawable кнопки и её сторону на треке.
 * Компонент отвечает только за внешний вид. Клик, смену состояния и поведение
 * недоступного элемента должен обрабатывать родительский компонент.
 *
 * @param isEnabled текущее состояние, которое нужно отобразить.
 * @param contentDescription описание состояния для accessibility-сервисов.
 * @param modifier размер, расположение и внешнее поведение индикатора.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun PixelToggleIcon(
    isEnabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val brush = pixelToggleStateBrush(isEnabled)
    val knobSize = 30.dp
    val knobOffset = 3.dp

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        PixelToggleTrack(
            brush = brush,
            modifier = Modifier.matchParentSize(),
        )

        PixelToggleKnob(
            drawableId = if (isEnabled) R.drawable.tumbutton_ok else R.drawable.tumbutton_no,
            brush = brush,
            contentDescription = contentDescription,
            modifier = Modifier
                .align(if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                .offset(x = if (isEnabled) knobOffset else -knobOffset)
                .size(knobSize),
        )
    }
}

/**
 * Рисует подвижную кнопку переключателя из фрагмента bitmap-ресурса.
 *
 * Функция вырезает область `13 x 12` пикселей с позиции `(25, 26)`, растягивает
 * её до размера Canvas без сглаживания и окрашивает через [BlendMode.SrcAtop].
 * Жёсткие координаты связаны с текущими ресурсами `tumbutton_ok` и
 * `tumbutton_no`.
 *
 * @param drawableId bitmap-ресурс кнопки для текущего состояния.
 * @param brush градиент, которым окрашивается bitmap-маска.
 * @param contentDescription описание состояния для accessibility-сервисов.
 * @param modifier размер, выравнивание и смещение кнопки на треке.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
private fun PixelToggleKnob(
    drawableId: Int,
    brush: Brush,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(id = drawableId)

    Canvas(
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
            }
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
    ) {
        drawImage(
            image = image,
            srcOffset = IntOffset(x = 25, y = 26),
            srcSize = IntSize(width = 13, height = 12),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(
                width = size.width.roundToInt(),
                height = size.height.roundToInt(),
            ),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Рисует тёмный трек переключателя и его градиентный пиксельный контур.
 *
 * Геометрия строится по сетке `3.dp`: два прямоугольника заполняют середину,
 * четыре линии образуют стороны, восемь квадратов формируют ступенчатые углы.
 * Функция не рисует кнопку и не хранит состояние переключателя.
 *
 * @param brush кисть контура, согласованная с текущим состоянием.
 * @param modifier задаёт размер и расположение Canvas.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
private fun PixelToggleTrack(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 3.dp.toPx()
        val innerColor = Color(0xEE050710)

        drawRect(
            color = innerColor,
            topLeft = Offset(pixel * 3f, pixel),
            size = Size(size.width - pixel * 6f, size.height - pixel * 2f),
        )
        drawRect(
            color = innerColor,
            topLeft = Offset(pixel, pixel * 3f),
            size = Size(size.width - pixel * 2f, size.height - pixel * 6f),
        )

        drawRect(brush, Offset(pixel * 3f, 0f), Size(size.width - pixel * 6f, pixel))
        drawRect(
            brush,
            Offset(pixel * 3f, size.height - pixel),
            Size(size.width - pixel * 6f, pixel),
        )
        drawRect(brush, Offset(0f, pixel * 3f), Size(pixel, size.height - pixel * 6f))
        drawRect(
            brush,
            Offset(size.width - pixel, pixel * 3f),
            Size(pixel, size.height - pixel * 6f),
        )

        drawRect(brush, Offset(pixel, pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(pixel * 2f, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(pixel, size.height - pixel * 3f), Size(pixel, pixel))
        drawRect(brush, Offset(pixel * 2f, size.height - pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 3f, pixel), Size(pixel, pixel))
        drawRect(
            brush,
            Offset(size.width - pixel * 2f, size.height - pixel * 3f),
            Size(pixel, pixel),
        )
        drawRect(
            brush,
            Offset(size.width - pixel * 3f, size.height - pixel * 2f),
            Size(pixel, pixel),
        )
    }
}
