package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.theme.LocalKsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ACTIVE_LIGHT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.overlayBackdropBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.overlayGradientColourAt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val FastTargetRotationMillis = 1_150
private const val SlowOuterRotationMillis = 6_800
private const val OuterRingPixelCount = 72

private data class VerificationBackdropPixel(
    val x: Float,
    val y: Float,
    val size: Float,
    val colourIndex: Int,
    val alpha: Float,
)

private val VerificationBackdropPixels = listOf(
    VerificationBackdropPixel(-144f, -55f, 3f, 0, 0.65f),
    VerificationBackdropPixel(-132f, -22f, 2f, 1, 0.42f),
    VerificationBackdropPixel(-146f, 20f, 5f, 1, 0.68f),
    VerificationBackdropPixel(-129f, 57f, 2f, 2, 0.46f),
    VerificationBackdropPixel(-115f, -70f, 2f, 0, 0.40f),
    VerificationBackdropPixel(-111f, -36f, 4f, 1, 0.72f),
    VerificationBackdropPixel(-103f, 52f, 3f, 2, 0.54f),
    VerificationBackdropPixel(-91f, -19f, 2f, 0, 0.42f),
    VerificationBackdropPixel(-82f, 69f, 2f, 1, 0.36f),
    VerificationBackdropPixel(-69f, -76f, 3f, 2, 0.48f),
    VerificationBackdropPixel(-58f, 58f, 2f, 1, 0.40f),
    VerificationBackdropPixel(-43f, -55f, 2f, 2, 0.36f),
    VerificationBackdropPixel(-31f, 73f, 3f, 1, 0.42f),
    VerificationBackdropPixel(32f, -72f, 2f, 2, 0.38f),
    VerificationBackdropPixel(49f, 61f, 3f, 3, 0.45f),
    VerificationBackdropPixel(64f, -62f, 2f, 2, 0.42f),
    VerificationBackdropPixel(78f, 72f, 2f, 3, 0.38f),
    VerificationBackdropPixel(92f, -42f, 3f, 2, 0.52f),
    VerificationBackdropPixel(101f, 17f, 2f, 3, 0.44f),
    VerificationBackdropPixel(111f, 53f, 4f, 2, 0.62f),
    VerificationBackdropPixel(119f, -67f, 2f, 3, 0.43f),
    VerificationBackdropPixel(128f, -24f, 3f, 2, 0.50f),
    VerificationBackdropPixel(137f, 12f, 5f, 3, 0.70f),
    VerificationBackdropPixel(145f, 59f, 2f, 2, 0.44f),
)

/**
 * Центральная validation-анимация: медленное внешнее кольцо и быстрый
 * внутренний target вращаются по часовой стрелке независимо друг от друга.
 *
 * @since 0.3
 */
@Composable
internal fun KsenaxModelValidationOrb(
    isAnimating: Boolean,
    faceState: KsenaxModelVerificationFaceState,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current
    val fastRotation = remember { Animatable(0f) }
    val slowRotation = remember { Animatable(0f) }

    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect

        coroutineScope {
            launch {
                while (isActive) {
                    fastRotation.animateTo(
                        targetValue = fastRotation.value + 360f,
                        animationSpec = tween(
                            durationMillis = FastTargetRotationMillis,
                            easing = LinearEasing,
                        ),
                    )
                    fastRotation.snapTo(fastRotation.value % 360f)
                }
            }
            launch {
                while (isActive) {
                    slowRotation.animateTo(
                        targetValue = slowRotation.value + 360f,
                        animationSpec = tween(
                            durationMillis = SlowOuterRotationMillis,
                            easing = LinearEasing,
                        ),
                    )
                    slowRotation.snapTo(slowRotation.value % 360f)
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVerificationOrbBackdrop(theme.markerColors)
        }

        Box(
            modifier = Modifier.size(174.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRotatingOuterPixelRing(
                    rotation = slowRotation.value,
                    gradientColors = theme.markerColors,
                )
            }

            Canvas(modifier = Modifier.size(132.dp)) {
                drawRotatingTarget(
                    rotation = fastRotation.value,
                    mainBrush = theme.overlayMainBrush,
                )
            }

            PixelVerificationBrushIcon(
                drawableId = R.drawable.olay_verify_brand_shield,
                brush = theme.overlayMiniBrush,
                accessibilityDescription = null,
                modifier = Modifier.size(94.dp),
            )

            PixelVerificationBrushIcon(
                drawableId = when (faceState) {
                    KsenaxModelVerificationFaceState.InProcess ->
                        R.drawable.olay_verify_brand_onvalidation

                    KsenaxModelVerificationFaceState.Failure ->
                        R.drawable.olay_verify_brand_failure

                    KsenaxModelVerificationFaceState.Success ->
                        R.drawable.olay_verify_brand_success
                },
                brush = theme.overlayMainBrush,
                accessibilityDescription = when (faceState) {
                    KsenaxModelVerificationFaceState.InProcess ->
                        "Локальная модель проверяется"

                    KsenaxModelVerificationFaceState.Failure ->
                        "Проверка локальной модели завершилась ошибкой"

                    KsenaxModelVerificationFaceState.Success ->
                        "Локальная модель успешно проверена"
                },
                modifier = Modifier.size(76.dp),
            )
        }
    }
}

private fun DrawScope.drawRotatingOuterPixelRing(
    rotation: Float,
    gradientColors: List<Color>,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val pixelWidth = 4.2.dp.toPx()
    val pixelHeight = 8.dp.toPx()
    val topLeft = Offset(
        x = center.x - pixelWidth / 2f,
        y = 2.dp.toPx(),
    )

    rotate(degrees = rotation, pivot = center) {
        repeat(OuterRingPixelCount) { index ->
            val segmentDegrees =
                index * (360f / OuterRingPixelCount)
            val horizontalFraction = (
                sin(Math.toRadians(segmentDegrees.toDouble())).toFloat() +
                    1f
                ) / 2f
            val segmentColour =
                overlayGradientColourAt(
                    colors = gradientColors,
                    fraction = horizontalFraction,
                )

            rotate(
                degrees = segmentDegrees,
                pivot = center,
            ) {
                drawRect(
                    color = segmentColour.copy(alpha = 0.18f),
                    topLeft = Offset(
                        x = topLeft.x - 1.5.dp.toPx(),
                        y = topLeft.y - 1.5.dp.toPx(),
                    ),
                    size = Size(
                        width = pixelWidth + 3.dp.toPx(),
                        height = pixelHeight + 3.dp.toPx(),
                    ),
                )
                drawRect(
                    color = segmentColour,
                    topLeft = topLeft,
                    size = Size(pixelWidth, pixelHeight),
                )
            }
        }
    }
}

private fun DrawScope.drawVerificationOrbBackdrop(
    gradientColors: List<Color>,
) {
    val center = Offset(size.width / 2f, size.height / 2f)

    drawCircle(
        brush = overlayBackdropBrush(
            colors = gradientColors,
            center = center,
            radius = 112.dp.toPx(),
        ),
        radius = 112.dp.toPx(),
        center = center,
    )

    VerificationBackdropPixels.forEach { pixel ->
        val pixelSize = pixel.size.dp.toPx()
        drawRect(
            color = gradientColors[
                pixel.colourIndex % gradientColors.size
            ]
                .copy(alpha = pixel.alpha),
            topLeft = Offset(
                x = center.x + pixel.x.dp.toPx() - pixelSize / 2f,
                y = center.y + pixel.y.dp.toPx() - pixelSize / 2f,
            ),
            size = Size(pixelSize, pixelSize),
        )
    }

    drawPixelCaptureCorners(
        center = center,
        gradientColors = gradientColors,
    )
}

private fun DrawScope.drawPixelCaptureCorners(
    center: Offset,
    gradientColors: List<Color>,
) {
    val halfWidth = 94.dp.toPx()
    val halfHeight = 81.dp.toPx()
    val arm = 10.dp.toPx()
    val thickness = 3.dp.toPx()
    val left = center.x - halfWidth
    val right = center.x + halfWidth
    val top = center.y - halfHeight
    val bottom = center.y + halfHeight
    val leftColour = overlayGradientColourAt(
        colors = gradientColors,
        fraction = 0.12f,
    )
    val rightColour = overlayGradientColourAt(
        colors = gradientColors,
        fraction = 0.88f,
    )

    drawRect(
        color = leftColour,
        topLeft = Offset(left, top),
        size = Size(arm, thickness),
    )
    drawRect(
        color = leftColour,
        topLeft = Offset(left, top),
        size = Size(thickness, arm),
    )
    drawRect(
        color = rightColour,
        topLeft = Offset(right - arm, top),
        size = Size(arm, thickness),
    )
    drawRect(
        color = rightColour,
        topLeft = Offset(right - thickness, top),
        size = Size(thickness, arm),
    )
    drawRect(
        color = leftColour,
        topLeft = Offset(left, bottom - thickness),
        size = Size(arm, thickness),
    )
    drawRect(
        color = leftColour,
        topLeft = Offset(left, bottom - arm),
        size = Size(thickness, arm),
    )
    drawRect(
        color = rightColour,
        topLeft = Offset(right - arm, bottom - thickness),
        size = Size(arm, thickness),
    )
    drawRect(
        color = rightColour,
        topLeft = Offset(right - thickness, bottom - arm),
        size = Size(thickness, arm),
    )
}

private fun DrawScope.drawRotatingTarget(
    rotation: Float,
    mainBrush: Brush,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val ringRadius = size.minDimension * 0.42f
    val pixel = 2.dp.toPx()

    rotate(degrees = rotation, pivot = center) {
        repeat(32) { index ->
            val angle = Math.toRadians(index * (360.0 / 32.0) - 90.0)
            drawRect(
                color = OVERLAY_ACTIVE_LIGHT_MINT_COLOUR.copy(alpha = 0.18f),
                topLeft = Offset(
                    x = center.x + cos(angle).toFloat() * ringRadius -
                        pixel / 2f,
                    y = center.y + sin(angle).toFloat() * ringRadius -
                        pixel / 2f,
                ),
                size = Size(pixel, pixel),
            )
        }

        repeat(4) { direction ->
            rotate(
                degrees = direction * 90f,
                pivot = center,
            ) {
                repeat(4) { segment ->
                    drawRect(
                        brush = mainBrush,
                        topLeft = Offset(
                            x = center.x - pixel / 2f,
                            y = 1.dp.toPx() + segment * 5.dp.toPx(),
                        ),
                        size = Size(pixel, 3.dp.toPx()),
                        alpha = 0.72f,
                    )
                }
            }
        }
    }
}
