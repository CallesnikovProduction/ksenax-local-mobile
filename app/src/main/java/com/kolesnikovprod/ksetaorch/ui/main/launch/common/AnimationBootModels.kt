package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.ui.graphics.Color

internal data class BootLine(
    val message: String,
    val status: BootStatus,
    val isFailureTail: Boolean = false,
)

internal data class BootHighlightMatch(
    val token: String,
    val color: Color,
    val index: Int,
)

internal data class BootTriangleRun(
    val seed: Int,
    val step: Int,
)