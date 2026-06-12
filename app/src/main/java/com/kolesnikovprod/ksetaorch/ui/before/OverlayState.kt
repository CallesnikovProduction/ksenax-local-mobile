package com.kolesnikovprod.ksetaorch.ui.before

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kolesnikovprod.ksetaorch.ui.before.OverlayLoadingState.*

/**
 * Хранит состояние одного из пунктов оверлей-контура
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
data class OverlayState(
    val paragraph: OverlayLoadingState = NON_CONFIRMED
)

/**
 * Перечислительный класс статических иконок для отрисовки прогресса оверлей-проверки.
 *
 * - [SUCCESS]: если проверка успешна - показывается зеленая галочка
 * - [NON_CONFIRMED]: если не подтверждено - показывается оранжевый значок внимания
 * - [LOADING]: состояние-заглушка, которая означает, что должна быть крутящаяся анимация
 * - [FAILURE]: если проверка дала сбой - показывается красный блок
 */
enum class OverlayLoadingState(
    val icon: ImageVector,
    val tint: Color
) {
    SUCCESS(Icons.Rounded.Check, Color(0xFF39FF14)),
    NON_CONFIRMED(Icons.Rounded.WarningAmber, Color(0xFFFF7A00)),
    LOADING(Icons.Rounded.QuestionMark, Color.White.copy(alpha = 0.8f)),
    FAILURE(Icons.Rounded.Block, Color(0xFFFF1744)),
}