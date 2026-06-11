package com.kolesnikovprod.ksetaorch.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Возвращает фирменный градиент Ksenax для текста и активных поверхностей.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
fun ksenaxTextGradient() = Brush.linearGradient(
    colors = listOf(
        Color(0xFF8B52FF),
        Color(0xFF806FFF),
        Color(0xFF42C7F4),
    )
)
