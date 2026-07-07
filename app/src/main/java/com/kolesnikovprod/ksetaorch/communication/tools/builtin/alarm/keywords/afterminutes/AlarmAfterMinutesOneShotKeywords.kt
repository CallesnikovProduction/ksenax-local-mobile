package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.afterminutes

import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmKeywordText
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords

object AlarmAfterMinutesOneShotKeywords : KsenaxOneShotKeywords {

    override fun matches(userMessage: String): Boolean {
        val text = AlarmKeywordText.normalize(userMessage)
        return AlarmKeywordText.looksLikeAlarm(text) &&
            AlarmKeywordText.mentionsMinutes(text)
    }
}
