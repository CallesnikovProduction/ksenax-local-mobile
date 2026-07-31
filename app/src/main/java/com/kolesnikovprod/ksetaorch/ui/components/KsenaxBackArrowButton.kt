package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.R

/**
 * Общая кнопка возврата без декоративной рамки.
 *
 * Одна drawable поворачивается для левой и правой боковой панели, поэтому
 * визуальный язык навигации остаётся одинаковым во всех экранах.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxBackArrowButton(
    brush: Brush,
    onClick: () -> Unit,
    pointsLeft: Boolean = true,
    contentDescription: String = "Назад",
    modifier: Modifier = Modifier,
) {
    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) { pressed ->
        GradientIcon(
            drawableId = R.drawable.sidepanel_goback,
            contentDescription = contentDescription,
            brush = brush.whileKsenaxPressed(pressed),
            modifier = Modifier
                .size(width = 24.dp, height = 30.dp)
                .graphicsLayer {
                    rotationZ = if (pointsLeft) 0f else 180f
                },
        )
    }
}
