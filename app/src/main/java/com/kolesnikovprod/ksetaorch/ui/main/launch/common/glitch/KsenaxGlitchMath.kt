package com.kolesnikovprod.ksetaorch.ui.main.launch.common.glitch

import androidx.compose.ui.geometry.Offset

internal fun glitchOffset(seed: Int, salt: Int): Float {
    return (glitchNoise(seed, salt) - 0.5f) * 12f
}

internal fun glitchMotion(seed: Int, salt: Int): Offset {
    return when (((glitchNoise(seed, salt) * 8f).toInt()).coerceIn(0, 7)) {
        0 -> Offset(1f, 0f)
        1 -> Offset(-1f, 0f)
        2 -> Offset(0f, 1f)
        3 -> Offset(0f, -1f)
        4 -> Offset(0.72f, 0.72f)
        5 -> Offset(-0.72f, 0.72f)
        6 -> Offset(0.72f, -0.72f)
        else -> Offset(-0.72f, -0.72f)
    }
}

internal fun glitchNoise(seed: Int, salt: Int): Float {
    val mixed = seed * 1_103_515_245 + salt * 12_345
    return ((mixed ushr 1) % 1_000) / 1_000f
}