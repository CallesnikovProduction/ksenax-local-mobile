package com.kolesnikovprod.ksetaorch.ui.before

import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.kolesnikovprod.ksetaorch.ui.KsenaxFontFamily
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

    // Просто строка, которая будет расцениваться как массив
    val logoText = "Ksenax"

    // Объект Compose, который умеет запускать бесконечные анимации
    val transition = rememberInfiniteTransition(label = "ksenax-indexing-transition")

    // Переменная, которая автоматически меняется во времени (Composable сам обновляет).
    val sweepPosition by transition.animateFloat(
        initialValue = -1f,                        // Левее первой буквы
        targetValue = logoText.length.toFloat(),   // До конца длины
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1450,
                easing = FastOutSlowInEasing       // Начало медленно, ускоряясь в конец
            ),
            repeatMode = RepeatMode.Reverse,       // Дойдя до конца, обратно летит
        ),
        label = "ksenax-indexing-sweep",
    )

    Row {
        logoText.forEachIndexed { index, letter ->
            val glow = (1f - abs(sweepPosition - index)).coerceIn(0f, 1.5f)

            val textColor = Color(
                red = 0.52f + glow * 0.48f,
                green = 0.30f + glow * 0.16f,
                blue = 0.72f + glow * 0.28f,
                alpha = 0.42f + glow * 0.58f,
            )

            Text(
                text = letter.toString(),
                color = textColor,
                fontSize = 72.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = KsenaxFontFamily,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}
