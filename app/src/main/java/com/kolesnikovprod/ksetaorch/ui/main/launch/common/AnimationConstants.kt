package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val BOOT_TYPE_CHUNK_SIZE                = 12
internal const val BOOT_FAILURE_TYPE_CHUNK_SIZE        = 24
internal const val BOOT_TYPE_DELAY_MILLIS              = 1L
internal const val BOOT_LINE_PAUSE_MILLIS              = 2L
internal const val BOOT_STATUS_BANG_MILLIS             = 5L
internal const val BOOT_BURST_PAUSE_MILLIS             = 12L
internal const val BOOT_STARTUP_PAUSE_MILLIS           = 20L
internal const val BOOT_HOLD_AFTER_LINES_MILLIS        = 55L
internal const val BOOT_FADE_MILLIS                    = 420
internal const val BOOT_GLITCH_TAIL_LINES              = 8
internal const val BOOT_EARLY_CORRUPTION_DELAY_MILLIS  = 520L
internal const val BOOT_FINAL_SILENCE_MILLIS           = 500L
internal const val BOOT_MAX_SPEED_MULTIPLIER           = 2.45f
internal const val BOOT_TAP_SPEED_STEP                 = 0.34f
internal const val BOOT_HOLD_SPEED_STEP                = 0.11f
internal const val BOOT_HOLD_ACCELERATION_DELAY_MILLIS = 360L
internal const val BOOT_HOLD_ACCELERATION_TICK_MILLIS  = 145L
internal const val BOOT_BLACKOUT_MILLIS                = 170
internal const val BOOT_TRIANGLE_RUN_MIN_DELAY_MILLIS  = 420L
internal const val BOOT_TRIANGLE_RUN_MAX_DELAY_MILLIS  = 1_850L
internal const val BOOT_TRIANGLE_STEP_MIN_MILLIS       = 18L
internal const val BOOT_TRIANGLE_STEP_MAX_MILLIS       = 43L
internal const val MAX_BOOT_LINES                      = 42
internal const val MIN_BOOT_LINES                      = 22
internal const val BOOT_FINAL_FAILURE_MESSAGE          = "ERROR: CONTEXT MAPPING FAILED"

internal val BOOT_HORIZONTAL_PADDING: Dp       = 12.dp
internal val BOOT_VERTICAL_PADDING:   Dp       = 18.dp
internal val BOOT_LINE_FONT_SIZE:     TextUnit = 11.sp
internal val BOOT_LINE_HEIGHT:        TextUnit = 15.sp

internal val BOOT_BACKGROUND = Color(0xFF050710)
internal val BOOT_BASE_COLOR = Color(0xFF7A8191)
internal val BOOT_GREEN      = Color(0xFF58FF9D)
internal val BOOT_MAGENTA    = Color(0xFFFF4EBC)
internal val BOOT_RED        = Color(0xFFFF4B64)
internal val BOOT_GOLD       = Color(0xFFFFD166)
internal val BOOT_CYAN       = Color(0xFF55DFFF)
internal val BOOT_BLUE       = Color(0xFF5C88FF)
internal val BOOT_SEVERE     = Color(0xFFFF7A45)
