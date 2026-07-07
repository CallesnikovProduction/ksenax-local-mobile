package com.kolesnikovprod.ksetaorch.ui.main.center

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.main.center.common.FolderActionButton
import com.kolesnikovprod.ksetaorch.ui.main.center.common.GradientHeroText
import com.kolesnikovprod.ksetaorch.ui.main.center.common.PixelHeroDivider
import com.kolesnikovprod.ksetaorch.ui.main.center.common.TypingAndroidPathLabel
import com.kolesnikovprod.ksetaorch.ui.main.center.common.TypingPhraseText
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily

/**
 * Главный контент в главном меню.
 *
 * Компонент показывает логотип приложения, декоративный разделитель,
 * анимированную фразу и agentic-действия, доступные только в **агентном режиме**.
 *
 * @param isTypingStarted запускать ли эффект печатающегося текста?
 * @param isAgenticModeSelected выбран ли в данный момент агентный режим?
 * @param workingFolderPath путь рабочей папки (маленькая строчка сверху)
 * @param workingFolderError текст ошибки, если с рабочей папкой не так что-то
 * @param onWorkingFolderClick callback к кнопке «рабочая папка»
 * @param onLogoClick callback к логотипу
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxCenterContent(
    isTypingStarted:       Boolean = true,
    isAgenticModeSelected: Boolean = false,
    workingFolderPath:     String,
    workingFolderError:    String? = null,
    onWorkingFolderClick:  () -> Unit,
    onLogoClick:           () -> Unit,
    modifier:              Modifier = Modifier,
) {
    // для кликабельности логотипчика "KSENAX"
    val heroInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        TypingAndroidPathLabel(
            path = workingFolderPath,     // отображается реальный путь
            isVisible = isAgenticModeSelected, // врубается только в agentic-режиме
            modifier = Modifier
                .align(Alignment.TopCenter)  // верхняя центральная часть
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .padding(top = 16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)  // уже ниже, чем TypingAndroidPathLabel
                .padding(horizontal = 26.dp)
                .padding(top = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GradientHeroText(
                text = "KSENAX",
                modifier = Modifier.clickable(
                    interactionSource = heroInteractionSource,
                    indication = null, // Material-круг нажатия убираем.
                    onClick = onLogoClick,
                )
            )

            Spacer(modifier = Modifier.height(5.dp))

            PixelHeroDivider(
                modifier = Modifier
                    .width(205.dp)
                    .height(25.dp)
            )

            Spacer(modifier = Modifier.height(15.dp))

            TypingPhraseText(
                isStarted = isTypingStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            )

            Spacer(modifier = Modifier.height(15.dp))

            AnimatedVisibility(
                visible = isAgenticModeSelected,
                enter = slideInVertically(
                    // ниже своей норм позиции на половину своей высоты
                    initialOffsetY = { height -> height / 2 },
                    // за 270 мс приезжает в нормальное место
                    animationSpec = tween(durationMillis = 270),
                ) + fadeIn(
                    // за 200 мс прозрачность с 0% поднимается к 100%
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = slideOutVertically(
                    // симметрично: конечное положение при исчезновении ниже на h/2
                    targetOffsetY = { height -> height / 2 },
                    animationSpec = tween(durationMillis = 240),
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 180)
                ),
            ) {
                FolderActionButton(
                    onClick = onWorkingFolderClick,
                )
            }

            workingFolderError
                ?.takeIf(String::isNotBlank)
                ?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text       = error,
                        color      = Color(0xFFE98A86),
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize   = 10.sp,
                        lineHeight = 12.sp,
                        textAlign  = TextAlign.Center,
                    )
                }
        }
    }
}
