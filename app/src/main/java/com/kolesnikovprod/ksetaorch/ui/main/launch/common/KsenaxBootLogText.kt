package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

internal val BootHighlights = listOf(
    "Android" to BOOT_GREEN,
    "AI" to BOOT_CYAN,
    "Agent" to BOOT_CYAN,
    "Local" to BOOT_BLUE,
    "LOCAL" to BOOT_BLUE,
    "Kotlin" to BOOT_CYAN,
    "Java" to BOOT_GOLD,
    "ARM64" to BOOT_GREEN,
    "GGUF" to BOOT_GOLD,
    "Q4_K_M" to BOOT_MAGENTA,
    "Q5_K_M" to BOOT_MAGENTA,
    "Q6_K" to BOOT_MAGENTA,
    "GPU" to BOOT_GREEN,
    "CPU" to BOOT_GOLD,
    "NNAPI" to BOOT_CYAN,
    "Vulkan" to BOOT_MAGENTA,
    "OpenCL" to BOOT_MAGENTA,
    "Metal" to BOOT_CYAN,
    "KSENAX" to BOOT_CYAN,
    "OK" to BOOT_GREEN,
    "ERR0R" to BOOT_RED,
    "FATAL" to BOOT_RED,
    "NULL_REF" to BOOT_MAGENTA,
    "DEADBEEF" to BOOT_GOLD,
    "SYSTEM CØRRUPTED" to BOOT_RED,
    "NΞUR∆L" to BOOT_MAGENTA,
)

internal fun bootLineText(
    message: String,
    status: BootStatus,
    isStatusVisible: Boolean,
) = buildAnnotatedString {
    appendHighlightedMessage(message)

    if (isStatusVisible) {
        append("  ")

        withStyle(
            SpanStyle(
                color = status.color,
                background = Color.Transparent,
                shadow = null,
            ),
        ) {
            append("[${status.label}]")
        }
    }
}

internal fun plainBootLineText(
    message: String,
    status: BootStatus,
    isStatusVisible: Boolean,
) = buildAnnotatedString {
    append(message)

    if (isStatusVisible) {
        append("  [${status.label}]")
    }
}

internal fun androidx.compose.ui.text.AnnotatedString.Builder.appendHighlightedMessage(
    message: String,
) {
    var index = 0

    while (index < message.length) {
        val nextHighlight = BootHighlights
            .mapNotNull { (token, color) ->
                val foundIndex = message.indexOf(token, startIndex = index)
                if (foundIndex >= 0) {
                    BootHighlightMatch(token, color, foundIndex)
                } else {
                    null
                }
            }
            .minByOrNull { it.index }

        if (nextHighlight == null) {
            append(message.substring(index))
            break
        }

        if (nextHighlight.index > index) {
            append(message.substring(index, nextHighlight.index))
        }

        withStyle(
            SpanStyle(
                color = nextHighlight.color,
                shadow = Shadow(
                    color = nextHighlight.color.copy(alpha = 0.62f),
                    offset = Offset.Zero,
                    blurRadius = 7f,
                ),
            ),
        ) {
            append(nextHighlight.token)
        }

        index = nextHighlight.index + nextHighlight.token.length
    }
}
