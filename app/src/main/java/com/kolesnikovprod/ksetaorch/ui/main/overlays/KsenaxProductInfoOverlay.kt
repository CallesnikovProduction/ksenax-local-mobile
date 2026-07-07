package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.mainGradient

private val ProductOverlayScrim = Color.Black.copy(alpha = 0.88f)
private val ProductBodyColor = Color(0xFFD5DBE7)
private val ProductMetaColor = Color(0xFF748091)

@Composable
fun KsenaxProductInfoOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    currentVersionOfApplication: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .drawWithCache {
                    onDrawBehind {
                        drawRect(color = ProductOverlayScrim)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(316.dp)
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                PixelWideFrame(
                    brush = inactiveGradientBrush,
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = Color(0xF2050710),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GradientIcon(
                        drawableId = R.drawable.soft_ic_info,
                        contentDescription = null,
                        brush = mainGradient,
                        modifier = Modifier.size(35.dp),
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    GradientText(
                        text = "OpenKsenax v.$currentVersionOfApplication",
                        fontSize = 34.sp,
                        lineHeight = 30.sp,
                        brush = mainGradient,
                        fontFamily = KsenaxFontFamily.jersey10,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = KSENAX_DESCRIPTION,
                        color = ProductBodyColor,
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Рабочих строк кода: 17.4k",
                        color = ProductMetaColor,
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Строк документации: 5.2k",
                        color = ProductMetaColor,
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Kotlin/Java-файлов: 179",
                        color = ProductMetaColor,
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    brush: Brush,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = TextAlign.Center,
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

val KSENAX_DESCRIPTION = """
    Open-source проект оркестрации локальных моделей под Android.
    Автор собирает его один как курсовую и будущую дипломную работу.
    Сейчас проект строит честное local-first ядро: текст, голос, выбор моделей, tool-calling и безопасные действия в реальных границах Android.
    Дальше OpenKsenax пойдёт в сторону Accessibility Service, новых локальных инструментов и более широкого агентного контура за пределами текущего приложения.
""".trimIndent()