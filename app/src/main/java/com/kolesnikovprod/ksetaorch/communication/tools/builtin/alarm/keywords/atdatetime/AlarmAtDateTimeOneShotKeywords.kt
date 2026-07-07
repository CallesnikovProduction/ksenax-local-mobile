package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.atdatetime

import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmKeywordText
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords

object AlarmAtDateTimeOneShotKeywords : KsenaxOneShotKeywords {

    override fun matches(userMessage: String): Boolean {
        val text = AlarmKeywordText.normalize(userMessage)
        return AlarmKeywordText.looksLikeAlarm(text) &&
            AlarmKeywordText.mentionsClockTime(text) &&
            AlarmKeywordText.mentionsDate(text)
    }
}
