package com.kolesnikovprod.ksetaorch.ui.main.model

private const val ChatTitleMaxChars = 18

internal fun String.toChatPanelTitle(): String {
    val normalized = trim().replace(Regex("\\s+"), " ")

    if (normalized.length <= ChatTitleMaxChars) {
        return normalized
    }

    return normalized.take(ChatTitleMaxChars).trimEnd() + "..."
}
