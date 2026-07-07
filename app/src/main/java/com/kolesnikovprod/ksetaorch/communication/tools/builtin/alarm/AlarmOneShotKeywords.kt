package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.afterhours.AlarmAfterHoursOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.afterminutes.AlarmAfterMinutesOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.atdatetime.AlarmAtDateTimeOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.attime.AlarmAtTimeOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords

object AlarmOneShotKeywords : KsenaxOneShotKeywords {

    private val functionKeywords: List<KsenaxOneShotKeywords> =
        listOf(
            AlarmAtDateTimeOneShotKeywords,
            AlarmAfterHoursOneShotKeywords,
            AlarmAfterMinutesOneShotKeywords,
            AlarmAtTimeOneShotKeywords,
        )

    override fun matches(userMessage: String): Boolean {
        val text = AlarmKeywordText.normalize(userMessage)
        return AlarmKeywordText.looksLikeAlarm(text) ||
            functionKeywords.any { keywords -> keywords.matches(text) }
    }
}
