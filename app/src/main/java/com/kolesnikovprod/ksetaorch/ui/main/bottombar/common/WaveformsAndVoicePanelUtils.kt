package com.kolesnikovprod.ksetaorch.ui.main.bottombar.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.theme.design.aquaSunsetVoiceBottomBarGradientBrush
import kotlin.math.roundToInt


/**
 * Рамка + содержимое голосового состояния.
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
@Composable
internal fun VoiceActivityPanel(
    isRecordingVoice:  Boolean,
    isProcessingVoice: Boolean,
    voiceLevel:        Float,
    modifier:          Modifier = Modifier,
) {
    val voiceSamples = remember { mutableStateListOf<Float>() }

    LaunchedEffect(isRecordingVoice, isProcessingVoice, voiceLevel) {
        when {
            isRecordingVoice -> {
                voiceSamples += voiceLevel.coerceIn(0f, 1f)
                while (voiceSamples.size > VoiceWaveSampleCount) {
                    voiceSamples.removeAt(0)
                }
            }
            !isProcessingVoice -> voiceSamples.clear()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        BottomBarFrame(
            modifier = Modifier.matchParentSize(),
            frameBrush = aquaSunsetVoiceBottomBarGradientBrush,
        )

        when {
            isProcessingVoice -> PixelVoiceProcessingLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 17.dp),
            )
            isRecordingVoice -> PixelVoiceWaveform(
                voiceSamples = voiceSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 14.dp),
            )
        }
    }
}

/**
 * Canvas-осциллограмма.
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
@Composable
private fun PixelVoiceWaveform(
    frameBrush:   Brush    = aquaSunsetVoiceBottomBarGradientBrush,
    voiceSamples: List<Float>,
    modifier:     Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 3.dp.toPx()
        val availableWidth = size.width
        val slotWidth = availableWidth / VoiceWaveSampleCount
        val centerY = size.height / 2f
        val missingSamples = (VoiceWaveSampleCount - voiceSamples.size).coerceAtLeast(0)

        repeat(VoiceWaveSampleCount) { index ->
            val sampleIndex = index - missingSamples
            val level = voiceSamples
                .getOrNull(sampleIndex)
                ?.coerceIn(0f, 1f)
                ?: 0f

            // ЭТА СТРОЧКА ОПРЕДЕЛЯЕТ ВЫСОТУ ОДНОГО ПИКА
            val halfHeightInPixels = (1f + level * 5f)
                .roundToInt()
                .coerceIn(1, 4)
            val x = index * slotWidth + (slotWidth - pixel) / 2f

            for (pixelIndex in -halfHeightInPixels..halfHeightInPixels) {
                drawRect(
                    brush = frameBrush,
                    topLeft = Offset(
                        x = x,
                        y = centerY + pixelIndex * pixel - pixel / 2f,
                    ),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}

@Composable
private fun PixelVoiceProcessingLine(
    modifier:   Modifier = Modifier,
    frameBrush: Brush    = aquaSunsetVoiceBottomBarGradientBrush
) {
    val transition = rememberInfiniteTransition(label = "voice_processing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 920,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voice_processing_phase",
    )

    Canvas(modifier = modifier) {
        val pixel = 4.dp.toPx()
        val gap = 2.dp.toPx()
        val slotWidth = pixel + gap
        val segmentCount = (size.width / slotWidth).toInt().coerceAtLeast(1)
        val trainLength = 8
        val trainHead = (phase * (segmentCount + trainLength)).roundToInt() - trainLength
        val lineWidth = segmentCount * slotWidth - gap
        val startX = (size.width - lineWidth) / 2f
        val y = (size.height - pixel) / 2f

        repeat(segmentCount) { index ->
            val distanceFromHead = trainHead - index

            if (distanceFromHead in 0 until trainLength) {
                drawRect(
                    brush = frameBrush,
                    topLeft = Offset(
                        x = startX + index * slotWidth,
                        y = y,
                    ),
                    size = Size(pixel, pixel),
                    alpha = 1f - distanceFromHead * 0.11f,
                )
            }
        }
    }
}