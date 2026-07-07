package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import java.util.Locale

object ObsidianNoteOneShotKeywords : KsenaxOneShotKeywords {

    override fun matches(userMessage: String): Boolean {
        val text = userMessage.lowercase(Locale.ROOT)
        return "замет" in text ||
            "obsidian" in text ||
            "запиши" in text ||
            "сохрани мысл" in text ||
            "проанализируй" in text && "мысл" in text
    }
}
