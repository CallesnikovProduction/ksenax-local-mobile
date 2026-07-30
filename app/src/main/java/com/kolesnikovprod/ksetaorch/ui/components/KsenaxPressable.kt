package com.kolesnikovprod.ksetaorch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Унифицированный контейнер физического отклика для видимых элементов управления.
 *
 * Пока палец удерживается на элементе, контейнер опускается на [pressedOffset],
 * а [content] получает `pressed = true`. Компонент самостоятельно владеет единым
 * [MutableInteractionSource], поэтому геометрия, белая рамка и белый контент
 * синхронизированы одним press-состоянием без ripple-эффекта.
 *
 * Полноэкранные scrim-области и невидимые gesture-зоны не должны использовать
 * этот компонент: смещение имеет смысл только для визуально оформленного control.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxPressableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedOffset: Dp = 1.dp,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isPressed = enabled && pressed
    val gestureModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(
            enabled = enabled,
            role = role,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    }

    Box(
        modifier = modifier
            .offset(y = if (isPressed) pressedOffset else 0.dp)
            .then(gestureModifier),
        contentAlignment = contentAlignment,
    ) {
        content(isPressed)
    }
}

/**
 * Возвращает белую кисть удержания либо исходную тематическую кисть.
 *
 * @since 0.3
 */
fun Brush.whileKsenaxPressed(pressed: Boolean): Brush {
    return if (pressed) SolidColor(Color.White) else this
}

/**
 * Возвращает белый цвет удержания либо исходный цвет элемента.
 *
 * @since 0.3
 */
fun Color.whileKsenaxPressed(pressed: Boolean): Color {
    return if (pressed) Color.White else this
}
