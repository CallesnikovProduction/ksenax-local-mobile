package com.kolesnikovprod.ksetaorch.ui.generalscreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Анимированный текстовый логотип для фазы проверки модели.
 *
 * По буквам проходит неоновая волна, которая визуально обозначает процесс
 * индексации/валидации локального файла.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun KsenaxIndexingLogo() {
    val logoText = "Ksenax"
    val transition = rememberInfiniteTransition(label = "ksenax-indexing-transition")
    val sweepPosition by transition.animateFloat(
        initialValue = -1f,
        targetValue = logoText.length.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ksenax-indexing-sweep",
    )

    Row {
        logoText.forEachIndexed { index, letter ->
            val glow = (1f - abs(sweepPosition - index)).coerceIn(0f, 1f)
            val textColor = Color(
                red = 0.52f + glow * 0.48f,
                green = 0.30f + glow * 0.16f,
                blue = 0.72f + glow * 0.28f,
                alpha = 0.42f + glow * 0.58f,
            )

            Text(
                text = letter.toString(),
                color = textColor,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}
