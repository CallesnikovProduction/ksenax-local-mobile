package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelToggleIcon
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.main.download.KsenaxDownloadPresentation
import com.kolesnikovprod.ksetaorch.ui.main.download.KsenaxDownloadPresentationStage
import com.kolesnikovprod.ksetaorch.ui.theme.LocalKsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CURRENT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_INACTIVE_PIXEL_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_METRIC_TEXT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.overlayGradientColourAt
import kotlin.math.ceil
import kotlin.math.floor

private val StageRingPixels = listOf(
    3 to 0, 4 to 0, 5 to 0,
    2 to 1, 6 to 1,
    1 to 2, 7 to 2,
    0 to 3, 8 to 3,
    0 to 4, 8 to 4,
    0 to 5, 8 to 5,
    1 to 6, 7 to 6,
    2 to 7, 6 to 7,
    3 to 8, 4 to 8, 5 to 8,
)

private val StageCenterPixels = listOf(
    4 to 3,
    3 to 4, 4 to 4, 5 to 4,
    4 to 5,
)

@Composable
internal fun OverlayHeader(
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DownloadGradientText(
            text = "ЗАГРУЗКА МОДЕЛИ",
            brush = theme.overlayMainBrush,
            fontSize = 29.sp,
            lineHeight = 31.sp,
            fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "+   local model sync   +",
            color = theme.mutedColor,
            fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun NetworkPolicyToggleRow(
    text: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = theme.mutedColor.whileKsenaxPressed(pressed),
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                fontSize = 14.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f),
            )

            PixelToggleIcon(
                isEnabled = isEnabled,
                enabledBrush = theme.selectedBrush.whileKsenaxPressed(pressed),
                disabledBrush = theme.inactiveBrush.whileKsenaxPressed(pressed),
                contentDescription =
                    if (isEnabled) "$text: да" else "$text: нет",
                modifier = Modifier.size(width = 52.dp, height = 25.dp),
            )
        }
    }
}

@Composable
internal fun TransferMetricsRow(
    presentation: KsenaxDownloadPresentation,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricText(
            text = presentation.transferredText,
            modifier = Modifier.weight(1.2f),
        )
        MetricDivider()
        MetricText(
            text = presentation.speedText ?: "—",
            modifier = Modifier.weight(1f),
        )
        MetricDivider()
        MetricText(
            text = presentation.etaText ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.MetricText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = OVERLAY_METRIC_TEXT_COLOUR,
        fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = modifier.padding(horizontal = 5.dp),
    )
}

@Composable
private fun MetricDivider(
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    Canvas(
        modifier = modifier.size(width = 1.dp, height = 26.dp),
    ) {
        drawRect(color = theme.mutedColor.copy(alpha = 0.5f))
    }
}

@Composable
internal fun InstallStageTimeline(
    currentStage: KsenaxDownloadPresentationStage,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    val currentIndex =
        if (currentStage == KsenaxDownloadPresentationStage.Download) 0 else 1
    val labels = listOf("скачивание", "проверка")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            val centers = listOf(
                size.width / 4f,
                size.width * 3f / 4f,
            )
            val y = size.height / 2f
            val radius = 10.dp.toPx()
            val dashWidth = 4.dp.toPx()
            val gap = 4.dp.toPx()

            repeat(1) { lineIndex ->
                val lineColor = if (currentIndex > lineIndex) {
                    OVERLAY_CURRENT_MINT_COLOUR
                } else {
                    theme.mutedColor.copy(alpha = 0.55f)
                }
                var x = centers[lineIndex] + radius + 4.dp.toPx()
                val endX =
                    centers[lineIndex + 1] - radius - 4.dp.toPx()
                while (x < endX) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x, y),
                        end = Offset((x + dashWidth).coerceAtMost(endX), y),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Square,
                    )
                    x += dashWidth + gap
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StageDot(
                        isReached = index <= currentIndex,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = if (index <= currentIndex) {
                            OVERLAY_CURRENT_MINT_COLOUR
                        } else {
                            theme.mutedColor
                        },
                        fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun StageDot(
    isReached: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    Canvas(modifier = modifier) {
        val colour = if (isReached) {
            OVERLAY_CURRENT_MINT_COLOUR
        } else {
            theme.mutedColor
        }
        val gridSize = 9
        val cell = floor(size.minDimension / gridSize)
            .coerceAtLeast(1f)
        val gridWidth = cell * gridSize
        val origin = Offset(
            x = (size.width - gridWidth) / 2f,
            y = (size.height - gridWidth) / 2f,
        )
        StageRingPixels.forEach { (x, y) ->
            drawRect(
                color = colour,
                topLeft = Offset(
                    x = origin.x + x * cell,
                    y = origin.y + y * cell,
                ),
                size = Size(cell, cell),
            )
        }

        if (isReached) {
            StageCenterPixels.forEach { (x, y) ->
                drawRect(
                    color = OVERLAY_CURRENT_MINT_COLOUR,
                    topLeft = Offset(
                        x = origin.x + x * cell,
                        y = origin.y + y * cell,
                    ),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

@Composable
internal fun PixelDownloadRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val theme = LocalKsenaxThemeVisuals.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val backgroundPixels = listOf(
                0.07f to 0.27f,
                0.13f to 0.69f,
                0.20f to 0.17f,
                0.24f to 0.82f,
                0.76f to 0.13f,
                0.82f to 0.76f,
                0.90f to 0.29f,
                0.94f to 0.61f,
            )
            backgroundPixels.forEachIndexed { index, (x, y) ->
                val pixelSize = if (index % 3 == 0) {
                    3.dp.toPx()
                } else {
                    2.dp.toPx()
                }
                drawRect(
                    color = overlayGradientColourAt(
                        colors = theme.markerColors,
                        fraction =
                        index.toFloat() /
                            backgroundPixels.lastIndex.coerceAtLeast(1),
                    ).copy(alpha = 0.42f),
                    topLeft = Offset(
                        x = size.width * x,
                        y = size.height * y,
                    ),
                    size = Size(pixelSize, pixelSize),
                )
            }

            val backgroundDashes = listOf(
                Triple(0.04f, 0.48f, 0.13f),
                Triple(0.14f, 0.36f, 0.08f),
                Triple(0.77f, 0.35f, 0.10f),
                Triple(0.84f, 0.55f, 0.12f),
            )
            backgroundDashes.forEachIndexed { index, (x, y, width) ->
                drawRect(
                    color = overlayGradientColourAt(
                        colors = theme.markerColors,
                        fraction =
                        (index + 1f) / (backgroundDashes.size + 1f),
                    ).copy(alpha = 0.26f),
                    topLeft = Offset(
                        x = size.width * x,
                        y = size.height * y,
                    ),
                    size = Size(
                        width = size.width * width,
                        height = 1.5.dp.toPx(),
                    ),
                )
            }

            val totalPixels = 44
            val activePixels = when {
                safeProgress <= 0f -> 0
                else -> ceil(totalPixels * safeProgress)
                    .toInt()
                    .coerceIn(0, totalPixels)
            }
            val pixelWidth = 5.dp.toPx()
            val pixelHeight = 11.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(
                x = center.x - pixelWidth / 2f,
                y = 3.dp.toPx(),
            )

            repeat(totalPixels) { index ->
                rotate(
                    degrees = index * (360f / totalPixels),
                    pivot = center,
                ) {
                    if (index < activePixels) {
                        val segmentColour = overlayGradientColourAt(
                            colors = theme.markerColors,
                            fraction = index.toFloat() / (totalPixels - 1),
                        )
                        drawRect(
                            color = segmentColour.copy(alpha = 0.16f),
                            topLeft = Offset(
                                x = topLeft.x - 1.dp.toPx(),
                                y = topLeft.y - 1.dp.toPx(),
                            ),
                            size = Size(
                                width = pixelWidth + 2.dp.toPx(),
                                height = pixelHeight + 2.dp.toPx(),
                            ),
                        )
                        drawRect(
                            color = segmentColour,
                            topLeft = topLeft,
                            size = Size(pixelWidth, pixelHeight),
                        )
                    } else {
                        drawRect(
                            color = OVERLAY_INACTIVE_PIXEL_COLOUR,
                            topLeft = topLeft,
                            size = Size(pixelWidth, pixelHeight),
                        )
                    }
                }
            }
        }

        GradientIcon(
            drawableId = R.drawable.olay_download_brand,
            contentDescription = "Прогресс загрузки модели",
            brush = theme.overlayMainBrush,
            modifier = Modifier.size(66.dp),
        )
    }
}
