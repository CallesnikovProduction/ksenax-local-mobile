package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import java.util.Locale

object CalendarEventOneShotKeywords : KsenaxOneShotKeywords {
    override fun matches(userMessage: String): Boolean {
        val text = userMessage.lowercase(Locale.ROOT)
        return "календар" in text ||
            "событи" in text ||
            "встреч" in text ||
            "созвон" in text ||
            "запланируй" in text
    }
}
