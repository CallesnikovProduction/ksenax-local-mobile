package com.kolesnikovprod.ksetaorch.ui.main.background.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Модель одной мерцающей звезды.
 *
 * @property x
 * @property y позиции звезды в долях экрана `0f...1f`
 * @property color цвет её
 * @property startMillis когда она появляется
 * @property durationMillis продолжительность жизни звёздочки
 * @property pixelSize размер квадратного пикселя в [dp]
 * @property maxAlpha максимальная прозрачность/яркость
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
private data class RandomStarSparkle(
    val x:              Float,
    val y:              Float,
    val color:          Color,
    val startMillis:    Long,
    val durationMillis: Long,
    val pixelSize:      Float,
    val maxAlpha:       Float,
)

/**
 * Палитра цветов звёзд, выбирается на рандом.
 *
 * @since 0.2
 */
private val SparkleColors = listOf(
    Color.White,
    Color(0xFF2731F5), Color(0xFF454DED),  Color(0xFF656AEB), Color(0xFFAAABE5),
    Color(0xFF1AA338), Color(0xFF3CB556),  Color(0xFF74CC88), Color(0xFFA2E0D6),
    Color(0xFFB51D72), Color(0xFFB53E81),  Color(0xFFC775A5), Color(0xFFD494BB)
)

/**
 * Максимально допустимое число звёзд на экране
 *
 * @since 0.2
 */
private const val MaxSparkles = 16

/**
 * С каким шансом будет появляться больше звезд, чем обычно
 *
 * @since 0.2
 */
private const val DoubleBurstChance = 0.22f

private const val MinSpawnDelayMillis = 280L
private const val MaxSpawnDelayMillis = 720L

/**
 * Динамический слой звёзд, делающий три вещи:
 *
 * 1. Хранит список активных звёзд
 * 2. Обновляет время кадра
 * 3. Рисует звёзды на Canvas
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
internal fun TwinklingStarsLayer(
    modifier: Modifier = Modifier,
) {
    val sparkles = remember { mutableStateListOf<RandomStarSparkle>() }
    var frameTimeMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            frameTimeMillis = withFrameMillis { it }
            sparkles.removeAll { sparkle ->
                frameTimeMillis - sparkle.startMillis > sparkle.durationMillis
            }
        }
    }

    // Генератор новых звёздочек
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(
                MinSpawnDelayMillis,
                MaxSpawnDelayMillis).milliseconds
            )

            val startMillis = withFrameMillis { it }
            val burstCount = if (Random.nextFloat() < DoubleBurstChance) 2 else 1

            repeat(burstCount) {
                sparkles += randomStarSparkle(startMillis)
            }

            while (sparkles.size > MaxSparkles) {
                sparkles.removeAt(0)
            }
        }
    }

    Canvas(modifier = modifier) {
        sparkles.forEach { sparkle ->
            val progress = ((frameTimeMillis - sparkle.startMillis).toFloat() / sparkle.durationMillis)
                .coerceIn(0f, 1f)
            val pulse = progress.toTwinklePulse()

            if (pulse <= 0.02f) {
                return@forEach
            }

            val centerX = size.width * sparkle.x
            val centerY = size.height * sparkle.y
            val pixel = sparkle.pixelSize.dp.toPx()
            val alpha = sparkle.maxAlpha * pulse

            // Центральная точка звезды
            val coreAlpha = alpha.coerceAtMost(0.95f)
            // Ближайшие лучи слабее
            val armAlpha = (alpha * 0.8f).coerceAtMost(0.78f)
            // Дальние лучи ещё слабее
            val outerArmAlpha = (alpha * 0.42f).coerceAtMost(0.38f)

            drawRect(
                color = sparkle.color.copy(alpha = coreAlpha),
                topLeft = Offset(centerX - pixel / 2f, centerY - pixel / 2f),
                size = Size(pixel, pixel),
            )

            if (pulse > 0.42f) {
                drawRect(
                    color = sparkle.color.copy(alpha = armAlpha),
                    topLeft = Offset(centerX - pixel / 2f, centerY - pixel * 1.7f),
                    size = Size(pixel, pixel),
                )
                drawRect(
                    color = sparkle.color.copy(alpha = armAlpha),
                    topLeft = Offset(centerX - pixel / 2f, centerY + pixel * 0.7f),
                    size = Size(pixel, pixel),
                )
                drawRect(
                    color = sparkle.color.copy(alpha = armAlpha),
                    topLeft = Offset(centerX - pixel * 1.7f, centerY - pixel / 2f),
                    size = Size(pixel, pixel),
                )
                drawRect(
                    color = sparkle.color.copy(alpha = armAlpha),
                    topLeft = Offset(centerX + pixel * 0.7f, centerY - pixel / 2f),
                    size = Size(pixel, pixel),
                )
            }

            if (pulse > 0.72f) {
                drawRect(
                    color = sparkle.color.copy(alpha = outerArmAlpha),
                    topLeft = Offset(centerX - pixel / 2f, centerY - pixel * 2.9f),
                    size = Size(pixel, pixel),
                )
                drawRect(
                    color = sparkle.color.copy(alpha = outerArmAlpha),
                    topLeft = Offset(centerX - pixel / 2f, centerY + pixel * 1.9f),
                    size = Size(pixel, pixel),
                )
                drawRect(
                    color = sparkle.color.copy(alpha = outerArmAlpha),
                    topLeft = Offset(centerX - pixel * 2.9f, centerY - pixel / 2f),
                    size = Size(pixel, pixel),
                )
                drawRect(
                    color = sparkle.color.copy(alpha = outerArmAlpha),
                    topLeft = Offset(centerX + pixel * 1.9f, centerY - pixel / 2f),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}

/**
 * Генерация произвольной звезды на основе DTO — [RandomStarSparkle].
 *
 * @since 0.2
 */
private fun randomStarSparkle(startMillis: Long): RandomStarSparkle {
    return RandomStarSparkle(
        // Не у краёв экрана
        x              = Random.nextFloatIn(0.04f, 0.96f),
        // Чтобы звёзды не лезли в scenic-зону
        y              = Random.nextFloatIn(0.04f, 0.78f),
        color          = SparkleColors.random(),
        startMillis    = startMillis,
        // Звезда живет меньше полутора секунд
        durationMillis = Random.nextLong(680L, 1_260L),
        // Пиксельные квадраты по размеру
        pixelSize      = Random.nextFloatIn(1.5f, 2.9f),
        // Разная максимальная яркость звезды
        maxAlpha       = Random.nextFloatIn(0.58f, 0.96f),
    )
}

private fun Float.toTwinklePulse(): Float {
    return sin(this * PI).toFloat()
}

private fun Random.nextFloatIn(from: Float, until: Float): Float {
    return from + nextFloat() * (until - from)
}
