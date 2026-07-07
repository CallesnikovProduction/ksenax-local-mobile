package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration

/**
 * Маленькие FunctionGemma declarations для системных будильников.
 *
 * Большой `alarm_tool` со сложным `oneOf` FunctionGemma заполняет нестабильно:
 * относительное "через 9 часов" может превращаться в локальное `9:00`.
 * Поэтому one-shot слой режет сценарий на функции с короткими именами и одним
 * главным аргументом.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface AlarmToolOneShot : KsenaxOneShotDeclaration {

    object AtTime : AlarmToolOneShot {
        override val codeName: String = "alarm_at_time"
        override val description: String =
            "Creates Android alarms at a local clock time. Use for requests like 'на 19:00'."
        override val parameters: String = """
            {"type":"object","properties":{"time":{"type":"string","description":"24-hour HH:mm time, for example 19:00"},"count":{"type":"integer","minimum":1,"maximum":50},"label":{"type":"string"}},"required":["time"],"additionalProperties":false}
        """.trimIndent()
    }

    object AfterHours : AlarmToolOneShot {
        override val codeName: String = "alarm_after_hours"
        override val description: String =
            "Creates Android alarms after a number of hours. Use for requests like 'через 9 часов'. Do not convert hours to clock time."
        override val parameters: String = """
            {"type":"object","properties":{"hours":{"type":"number","minimum":0},"count":{"type":"integer","minimum":1,"maximum":50},"label":{"type":"string"}},"required":["hours"],"additionalProperties":false}
        """.trimIndent()
    }

    object AfterMinutes : AlarmToolOneShot {
        override val codeName: String = "alarm_after_minutes"
        override val description: String =
            "Creates Android alarms after a number of minutes. Use for requests like 'через 30 минут'."
        override val parameters: String = """
            {"type":"object","properties":{"minutes":{"type":"integer","minimum":0},"count":{"type":"integer","minimum":1,"maximum":50},"label":{"type":"string"}},"required":["minutes"],"additionalProperties":false}
        """.trimIndent()
    }

    object AtDateTime : AlarmToolOneShot {
        override val codeName: String = "alarm_at_date_time"
        override val description: String =
            "Creates Android alarms at an exact local date-time. Use yyyy-MM-dd'T'HH:mm."
        override val parameters: String = """
            {"type":"object","properties":{"date_time":{"type":"string","description":"yyyy-MM-dd'T'HH:mm local date-time"},"count":{"type":"integer","minimum":1,"maximum":50},"label":{"type":"string"}},"required":["date_time"],"additionalProperties":false}
        """.trimIndent()
    }

    object ClearAll : AlarmToolOneShot {
        override val codeName: String = "alarm_clear_all"
        override val description: String =
            "Requests deletion of all Android Clock alarms. Normal Android apps cannot actually delete all system alarms."
        override val parameters: String? = null
    }
}
