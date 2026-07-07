package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.ui.graphics.Color

enum class BootStatus(
    val label: String,
    val color: Color,
) {
    UpToDate("UP-TO-DATE", BOOT_GREEN),
    Warning("WARNING", BOOT_GOLD),
    Severe("SEVERE", BOOT_SEVERE),
    Refusal("REFUSAL", BOOT_MAGENTA),
    Error("ERROR", BOOT_RED),
}

internal val BootStatus.effectStrength: Float
    get() = when (this) {
        BootStatus.UpToDate -> 0.02f
        BootStatus.Warning  -> 0.18f
        BootStatus.Refusal  -> 0.44f
        BootStatus.Error    -> 0.88f
        BootStatus.Severe   -> 0.68f
    }

internal val BootStatus.glitchBurstMillis: Long
    get() = when (this) {
        BootStatus.UpToDate -> 0L
        BootStatus.Warning  -> 28L
        BootStatus.Refusal  -> 36L
        BootStatus.Error    -> 48L
        BootStatus.Severe   -> 42L
    }
