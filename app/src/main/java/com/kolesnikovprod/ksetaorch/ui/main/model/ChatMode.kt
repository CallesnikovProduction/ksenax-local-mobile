package com.kolesnikovprod.ksetaorch.ui.main.model

import androidx.compose.ui.graphics.Brush
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.theme.design.agenticModeGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.basicModeGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.temporaricModeGradientBrush

enum class ChatMode(
    val label:          String,
    val icon:           Int,
    val activeGradient: Brush,
) {
    Basic(
        "basic",
        R.drawable.ic_basic_llm_chat,
        basicModeGradientBrush
    ),
    Agentic(
        "agentic",
        R.drawable.ic_agentic_llm_chat,
        agenticModeGradientBrush
    ),
    Temporaric(
        "temporaric",
        R.drawable.ic_temporaric_llm_chat,
        temporaricModeGradientBrush
    )
}
