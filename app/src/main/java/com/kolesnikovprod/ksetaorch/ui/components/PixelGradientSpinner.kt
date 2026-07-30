package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.theme.design.overlayGradientColourAt

private const val SpinnerPixelCount = 18
private const val SpinnerRotationMillis = 1_350

/**
 * Компактный пиксельный индикатор неопределённой загрузки.
 *
 * Сегменты сохраняют градиент по часовой стрелке, а всё кольцо вращается как
 * единая фигура. Компонент не зависит от download state и пригоден для любого
 * короткого фонового ожидания.
 *
 * @since 0.3
 */
@Composable
fun PixelGradientSpinner(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(
        label = "pixelGradientSpinner",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SpinnerRotationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pixelGradientSpinnerRotation",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val pixelWidth = 2.5.dp.toPx()
        val pixelHeight = 6.dp.toPx()
        val topLeft = Offset(
            x = center.x - pixelWidth / 2f,
            y = 1.dp.toPx(),
        )

        rotate(degrees = rotation, pivot = center) {
            repeat(SpinnerPixelCount) { index ->
                val fraction = index.toFloat() / (SpinnerPixelCount - 1)
                rotate(
                    degrees = index * (360f / SpinnerPixelCount),
                    pivot = center,
                ) {
                    drawRect(
                        color = overlayGradientColourAt(
                            colors = gradientColors,
                            fraction = fraction,
                        ),
                        topLeft = topLeft,
                        size = Size(pixelWidth, pixelHeight),
                    )
                }
            }
        }
    }
}
