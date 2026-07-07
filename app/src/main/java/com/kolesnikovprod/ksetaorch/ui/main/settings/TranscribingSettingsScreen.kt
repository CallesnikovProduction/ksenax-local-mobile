package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelSquareFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.mainGradient
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush

private val ExperimentalPink = Color(0xFFE58AB8)

@Composable
fun TranscribingSettingsScreen(
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
                onBackClick = onBackClick,
            )

            Spacer(modifier = Modifier.height(14.dp))

            TranscribingOptionButton(
                model = KsenaxTranscribingModel.Gemma,
                isSelected = selectedModel == KsenaxTranscribingModel.Gemma,
                isInstalled = isGemmaInstalled,
                onClick = { onModelClick(KsenaxTranscribingModel.Gemma) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            TranscribingOptionButton(
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
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
    ) {
        PixelBackButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        GradientText(
            text = "Choose Voice-model",
            fontSize = 14.sp,
            lineHeight = 10.sp,
            brush = sunsetBottomBarGradientBrush,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )

        GradientIcon(
            drawableId = R.drawable.soft_ic_transcribesettings,
            contentDescription = null,
            brush = sunsetBottomBarGradientBrush,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(54.dp)
                .offset(y = 2.dp),
        )
    }
}

@Composable
internal fun PixelBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PixelSquareFrame(
            brush = sunsetBottomBarGradientBrush,
            modifier = Modifier.matchParentSize(),
        )

        PixelBackGlyph(
            modifier = Modifier.size(width = 19.dp, height = 23.dp),
        )
    }
}

@Composable
private fun PixelBackGlyph(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 3.dp.toPx()
        val centerY = size.height / 2f - pixel / 2f
        val leftX = (size.width - pixel * 6f) / 2f

        val arrowPixels = listOf(
            0 to 2,
            1 to 1,
            1 to 2,
            1 to 3,
            2 to 0,
            2 to 2,
            2 to 4,
            3 to 2,
            4 to 2,
            5 to 2,
        )

        arrowPixels.forEach { (x, y) ->
            drawRect(
                brush = sunsetBottomBarGradientBrush,
                topLeft = Offset(
                    x = leftX + x * pixel,
                    y = centerY + (y - 2) * pixel,
                ),
                size = Size(pixel, pixel),
            )
        }
    }
}

@Composable
private fun TranscribingOptionButton(
    model: KsenaxTranscribingModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val optionBrush = if (isSelected && isInstalled) mainGradient else inactiveGradientBrush

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        PixelTransparentButtonFrame(
            modifier = Modifier.matchParentSize(),
            brush = optionBrush,
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
                    brush = optionBrush,
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = model.description,
                    color = Color(0xFF6F7C8A),
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }

            if (!isInstalled) {
                Spacer(modifier = Modifier.width(14.dp))
                PixelDownloadButton(
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
            fontFamily = KsenaxFontFamily.tiny5,
            textAlign = TextAlign.Start,
        )

        if (model.experimentalLabel != null) {
            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = model.experimentalLabel,
                color = ExperimentalPink,
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 14.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
internal fun PixelDownloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(42.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.soft_ic_download),
            contentDescription = "Download model",
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
    fontFamily: FontFamily = KsenaxFontFamily.minecraftFont,
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
