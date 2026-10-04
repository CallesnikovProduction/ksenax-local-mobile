package com.kolesnikovprod.ksetaorch.communication.work.planning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет строгую границу JSON-плана G4.
 *
 * @author Stephan Kolesnikov
 * @since 0.4
 */
class G4PlanningResponseParserTest {

    @Test
    fun `accepts an ordered plan with allowed actions`() {
        val result = G4PlanningResponseParser.parse(
            rawText = """
                {"type":"plan","steps":[
                  {"id":"step_1","action":"torch_on","instruction":"Включи фонарик","input":{}},
                  {"id":"step_2","action":"alarm_after_minutes","instruction":"Поставь будильник","input":{"minutes":5,"count":3}}
                ]}
            """.trimIndent(),
            allowedActionNames = setOf("torch_on", "alarm_after_minutes"),
        )

        assertTrue(result is PlanningParseResult.Success)
        val plan = (result as PlanningParseResult.Success).plan as KsenaxWorkPlan.ActionPlan
        assertEquals(listOf("torch_on", "alarm_after_minutes"), plan.steps.map { it.actionName })
        assertEquals("{\"minutes\":5,\"count\":3}", plan.steps[1].plannerInputJson)
    }

    @Test
    fun `rejects an action outside the runtime catalog`() {
        val result = G4PlanningResponseParser.parse(
            rawText = """{"type":"plan","steps":[{"action":"invented_tool","input":{}}]}""",
            allowedActionNames = setOf("torch_on"),
        )

        assertTrue(result is PlanningParseResult.Failure)
    }

    @Test fun `rejects surrounding prose multiple roots and wrong scalar types`() {
        listOf("prefix {\"type\":\"refusal\",\"reason\":\"no\"}",
            """{"type":"refusal","reason":"no"}{"type":"refusal","reason":"yes"}""",
            """{"type":"clarification","question":123}""",
            """{"type":"plan","steps":[{"action":"torch_on","instruction":true}]}""")
            .forEach { assertTrue(G4PlanningResponseParser.parse(it, setOf("torch_on")) is PlanningParseResult.Failure) }
    }
}
