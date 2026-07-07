package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.main.launch.common.glitch.glitchMotion
import com.kolesnikovprod.ksetaorch.ui.main.launch.common.glitch.glitchNoise
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun BootScanlines(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val step = 5.dp.toPx()
        var y = 0f

        while (y < size.height) {
            drawRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(0f, y),
                size = Size(size.width, 1.dp.toPx()),
            )
            y += step
        }
    }
}

@Composable
internal fun BootGlitchOverlay(
    seed: Int,
    alpha: Float,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val clampedIntensity = intensity.coerceIn(0.08f, 1f)
        val bandCount = (5 + clampedIntensity * 13f).roundToInt()
        val pulseMotion = glitchMotion(seed, 811)
        val motionX = pulseMotion.x * (14.dp.toPx() + 48.dp.toPx() * clampedIntensity)
        val motionY = pulseMotion.y * (8.dp.toPx() + 26.dp.toPx() * clampedIntensity)

        repeat(bandCount) { index ->
            val noise = glitchNoise(seed, index)
            val y = size.height * noise + motionY * 0.34f
            val height = (1.dp.toPx() + glitchNoise(seed, index + 11) * (6.dp.toPx() + 14.dp.toPx() * clampedIntensity))
            val xShift = (glitchNoise(seed, index + 23) - 0.5f) *
                    (30.dp.toPx() + 52.dp.toPx() * clampedIntensity) + motionX
            val color = when (index % 4) {
                0 -> BOOT_CYAN
                1 -> BOOT_MAGENTA
                2 -> BOOT_BLUE
                else -> Color.White
            }

            drawRect(
                color = color.copy(alpha = (0.04f + noise * 0.15f * clampedIntensity) * alpha),
                topLeft = Offset(xShift, y),
                size = Size(size.width * (0.24f + glitchNoise(seed, index + 31) * (0.42f + clampedIntensity * 0.48f)), height),
            )

            if (index % 3 == 0) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.18f * alpha),
                    topLeft = Offset(0f, y + height),
                    size = Size(size.width, 2.dp.toPx()),
                )
            }
        }

        val artifactCount = (3 + clampedIntensity * 9f).roundToInt()

        repeat(artifactCount) { index ->
            val shapeNoise = glitchNoise(seed, index + 101)
            val width = (4.dp.toPx() + glitchNoise(seed, index + 119) * 22.dp.toPx()) *
                    (0.72f + clampedIntensity)
            val height = (4.dp.toPx() + glitchNoise(seed, index + 137) * 20.dp.toPx()) *
                    (0.68f + clampedIntensity)
            val x = size.width * glitchNoise(seed, index + 151) + motionX * (0.35f + shapeNoise)
            val y = size.height * glitchNoise(seed, index + 173) + motionY * (0.35f + shapeNoise)
            val color = when (index % 5) {
                0 -> BOOT_CYAN
                1 -> BOOT_MAGENTA
                2 -> BOOT_BLUE
                3 -> BOOT_RED
                else -> Color.White
            }
            val artifactAlpha = (0.08f + shapeNoise * 0.24f * clampedIntensity) * alpha

            when (((shapeNoise * 4f).roundToInt()).coerceIn(0, 3)) {
                0 -> drawRect(
                    color = color.copy(alpha = artifactAlpha),
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                )

                1 -> drawCircle(
                    color = color.copy(alpha = artifactAlpha * 0.86f),
                    radius = min(width, height) * 0.48f,
                    center = Offset(x + width * 0.5f, y + height * 0.5f),
                )

                2 -> drawGlitchTriangle(
                    left = x,
                    top = y,
                    width = width,
                    height = height,
                    color = color.copy(alpha = artifactAlpha),
                    mirror = glitchNoise(seed, index + 191) > 0.5f,
                )

                else -> drawGlitchClusterArtifact(
                    left = x,
                    top = y,
                    width = width,
                    height = height,
                    color = color.copy(alpha = artifactAlpha),
                    pixel = 2.dp.toPx(),
                )
            }
        }

        repeat((2 + clampedIntensity * 7f).roundToInt()) { index ->
            val y = size.height * glitchNoise(seed, index + 71) - motionY * 0.42f
            drawRect(
                color = BOOT_RED.copy(
                    alpha = (0.05f + clampedIntensity * 0.11f) * alpha,
                ),
                topLeft = Offset(-motionX * 0.45f, y),
                size = Size(size.width, 1.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun BootRisingPixelTriangles(
    run: BootTriangleRun,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 3.dp.toPx()
        val triangleCount = 4 + (glitchNoise(run.seed, 901) * 6f).roundToInt()

        repeat(triangleCount) { index ->
            val startStep = (glitchNoise(run.seed, index + 919) * 8f).toInt()
            val cadence = 1 + (glitchNoise(run.seed, index + 931) * 3f).toInt()
            val elapsedSteps = run.step - startStep
            if (elapsedSteps < 0) {
                return@repeat
            }
            val particleStep = elapsedSteps / cadence

            val widthInPixels = 3 + (glitchNoise(run.seed, index + 941) * 8f).roundToInt()
            val heightInPixels = 2 + (glitchNoise(run.seed, index + 967) * 7f).roundToInt()
            val width = widthInPixels * pixel
            val height = heightInPixels * pixel
            val startBelowScreen = glitchNoise(run.seed, index + 979) * size.height * 0.24f
            val laneX = glitchNoise(run.seed, index + 991) * (size.width - width).coerceAtLeast(0f)
            val verticalJump = size.height * (
                    0.08f + glitchNoise(run.seed, index + 1_001) * 0.14f
                    )
            val jumpX = (
                    glitchNoise(run.seed + particleStep, index + 1_013) - 0.5f
                    ) * 42.dp.toPx()
            val x = (laneX + jumpX).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            val y = size.height + height + startBelowScreen - particleStep * verticalJump
            if (y < -height) {
                return@repeat
            }
            val color = when (index % 4) {
                0 -> BOOT_CYAN
                1 -> BOOT_MAGENTA
                2 -> BOOT_BLUE
                else -> Color.White
            }
            val triangleAlpha = alpha * (0.32f + glitchNoise(run.seed, index + 1_037) * 0.48f)

            drawPixelTriangle(
                left = x,
                top = y,
                widthInPixels = widthInPixels,
                heightInPixels = heightInPixels,
                pixel = pixel,
                color = color.copy(alpha = triangleAlpha),
                mirror = glitchNoise(run.seed, index + 1_061) > 0.5f,
            )

            if (index % 2 == 0) {
                drawRect(
                    color = color.copy(alpha = triangleAlpha * 0.38f),
                    topLeft = Offset(x - pixel * 2f, y + height * 0.5f),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}

@Composable
internal fun BootCollapseCursorOverlay(
    seed: Int,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 3.dp.toPx()
        val shakeX = (glitchNoise(seed, 511) - 0.5f) * 18.dp.toPx()
        val shakeY = (glitchNoise(seed, 512) - 0.5f) * 10.dp.toPx()
        val cursorX = size.width * 0.57f + shakeX
        val cursorY = size.height * 0.73f + shakeY

        drawRect(
            color = BOOT_RED.copy(alpha = 0.16f * alpha),
            topLeft = Offset(0f, cursorY - 12.dp.toPx()),
            size = Size(size.width, 5.dp.toPx()),
        )
        drawRect(
            color = BOOT_MAGENTA.copy(alpha = 0.18f * alpha),
            topLeft = Offset(0f, cursorY + 21.dp.toPx()),
            size = Size(size.width, 3.dp.toPx()),
        )

        repeat(18) { index ->
            val noise = glitchNoise(seed, index + 540)
            val width = pixel * (1f + (index % 4))
            val height = pixel * (1f + (index % 2))
            val x = cursorX + (noise - 0.5f) * 96.dp.toPx()
            val y = cursorY + (glitchNoise(seed, index + 570) - 0.5f) * 42.dp.toPx()
            val color = when (index % 3) {
                0 -> BOOT_RED
                1 -> BOOT_MAGENTA
                else -> Color.White
            }

            drawRect(
                color = color.copy(alpha = (0.22f + noise * 0.38f) * alpha),
                topLeft = Offset(x, y),
                size = Size(width, height),
            )
        }

        drawRect(
            color = BOOT_CYAN.copy(alpha = 0.24f * alpha),
            topLeft = Offset(cursorX - pixel, cursorY - pixel * 4f),
            size = Size(pixel * 4f, pixel * 10f),
        )
        drawRect(
            color = Color.White.copy(alpha = 0.86f * alpha),
            topLeft = Offset(cursorX, cursorY - pixel * 3f),
            size = Size(pixel * 2f, pixel * 8f),
        )
    }
}
