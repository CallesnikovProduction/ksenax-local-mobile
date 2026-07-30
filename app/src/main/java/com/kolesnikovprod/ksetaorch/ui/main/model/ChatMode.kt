package com.kolesnikovprod.ksetaorch.ui.main.model

import com.kolesnikovprod.ksetaorch.R

enum class ChatMode(
    val label: String,
    val icon: Int,
) {
    Basic(
        "basic",
        R.drawable.tb_basic_mode,
    ),
    Agentic(
        "agentic",
        R.drawable.tb_agentic_mode,
    ),
    Temporaric(
        "temporaric",
        R.drawable.tb_temporaric_mode,
    )
}
