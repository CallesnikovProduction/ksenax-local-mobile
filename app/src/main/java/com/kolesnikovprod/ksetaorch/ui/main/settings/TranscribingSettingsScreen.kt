package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxBackArrowButton
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily

private val ExperimentalPink = Color(0xFFE58AB8)

@Composable
fun TranscribingSettingsScreen(
    theme: KsenaxThemeVisuals,
    onBackClick: () -> Unit,
    selectedModel: KsenaxTranscribingModel?,
    isGemmaInstalled: Boolean,
    isVoskInstalled: Boolean,
    onModelClick: (KsenaxTranscribingModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050710)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            TranscribingSettingsTopBar(
                theme = theme,
                onBackClick = onBackClick,
            )

            Spacer(modifier = Modifier.height(14.dp))

            TranscribingOptionButton(
                theme = theme,
                model = KsenaxTranscribingModel.Gemma,
                isSelected = selectedModel == KsenaxTranscribingModel.Gemma,
                isInstalled = isGemmaInstalled,
                onClick = { onModelClick(KsenaxTranscribingModel.Gemma) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            TranscribingOptionButton(
                theme = theme,
                model = KsenaxTranscribingModel.Vosk,
                isSelected = selectedModel == KsenaxTranscribingModel.Vosk,
                isInstalled = isVoskInstalled,
                onClick = { onModelClick(KsenaxTranscribingModel.Vosk) },
            )
        }
    }
}

@Composable
private fun TranscribingSettingsTopBar(
    theme: KsenaxThemeVisuals,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
    ) {
        PixelBackButton(
            brush = theme.controlsBrush,
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        GradientText(
            text = "Choose Voice-model",
            fontSize = 14.sp,
            lineHeight = 10.sp,
            brush = theme.settingsBrush,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )

        GradientIcon(
            drawableId = R.drawable.settings_ic_chaptered,
            contentDescription = null,
            brush = theme.controlsBrush,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(54.dp)
                .offset(y = 2.dp),
        )
    }
}

@Composable
internal fun PixelBackButton(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KsenaxBackArrowButton(
        brush = brush,
        onClick = onClick,
        pointsLeft = true,
        modifier = modifier.size(40.dp),
    )
}

@Composable
private fun TranscribingOptionButton(
    theme: KsenaxThemeVisuals,
    model: KsenaxTranscribingModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionBrush = if (isSelected && isInstalled) {
        theme.selectedBrush
    } else {
        theme.inactiveBrush
    }

    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp),
    ) { pressed ->
        val pressedBrush = optionBrush.whileKsenaxPressed(pressed)

        PixelTransparentButtonFrame(
            modifier = Modifier.matchParentSize(),
            brush = pressedBrush,
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                TranscribingModelTitle(
                    model = model,
                    brush = pressedBrush,
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = model.description,
                    color = theme.mutedColor.whileKsenaxPressed(pressed),
                    fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }

            if (!isInstalled) {
                Spacer(modifier = Modifier.width(14.dp))
                PixelDownloadButton(
                    brush = theme.controlsBrush,
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun TranscribingModelTitle(
    model: KsenaxTranscribingModel,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientText(
            text = model.title,
            fontSize = 14.sp,
            lineHeight = 15.sp,
            brush = brush,
            fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            textAlign = TextAlign.Start,
        )

        if (model.experimentalLabel != null) {
            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = model.experimentalLabel,
                color = ExperimentalPink,
                fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 14.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
internal fun PixelDownloadButton(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .size(42.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        GradientIcon(
            drawableId = R.drawable.settings_ic_download,
            contentDescription = "Download model",
            brush = brush.whileKsenaxPressed(pressed),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
internal fun PixelTransparentButtonFrame(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 2.dp.toPx()

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

@Composable
internal fun GradientText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    brush: Brush,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = textAlign,
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
