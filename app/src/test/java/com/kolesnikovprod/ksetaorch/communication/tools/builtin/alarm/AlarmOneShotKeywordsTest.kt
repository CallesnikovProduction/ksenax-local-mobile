package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.afterhours.AlarmAfterHoursOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.keywords.afterminutes.AlarmAfterMinutesOneShotKeywords
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет alarm-keywords и сохранение чисел из пользовательского запроса.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class AlarmOneShotKeywordsTest {

    @Test
    fun `matches alarm prompts`() {
        assertTrue(AlarmOneShotKeywords.matches("Поставь будильник на 19:00"))
        assertTrue(AlarmOneShotKeywords.matches("Поставь 3 будильника через 9 часов"))
        assertTrue(AlarmOneShotKeywords.matches("Разбуди меня через 30 минут"))
    }

    @Test
    fun `does not match unrelated prompts`() {
        assertFalse(AlarmOneShotKeywords.matches("Включи фонарик"))
        assertFalse(AlarmOneShotKeywords.matches("Привет"))
    }

    @Test
    fun `function specific keywords detect relative hours and minutes`() {
        assertTrue(AlarmAfterHoursOneShotKeywords.matches("Поставь будильник через 9 часов"))
        assertTrue(AlarmAfterMinutesOneShotKeywords.matches("Разбуди через 30 минут"))
        assertFalse(AlarmAfterHoursOneShotKeywords.matches("Поставь будильник на 9:00"))
    }

    @Test
    fun `draft preserves count and relative hours`() {
        val draft = AlarmUserPromptDraft.build("поставь 5 будильников через 10 часов")

        assertTrue(draft?.expectedActionName == AlarmToolOneShot.AfterHours.codeName)
        assertTrue(draft?.argumentsJson?.contains(""""count":5""") == true)
        assertTrue(draft?.argumentsJson?.contains(""""hours":10""") == true)
    }

    @Test
    fun `draft preserves count and relative minutes`() {
        val draft = AlarmUserPromptDraft.build("поставь 20 будильников через 4 минуты")

        assertTrue(draft?.expectedActionName == AlarmToolOneShot.AfterMinutes.codeName)
        assertTrue(draft?.argumentsJson?.contains(""""count":20""") == true)
        assertTrue(draft?.argumentsJson?.contains(""""minutes":4""") == true)
    }
}
