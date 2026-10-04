package com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

/** @since 0.2 */
object CalendarEventOneShotProtocol : KsenaxOneShotToolProtocol {
    override val declarations: List<KsenaxOneShotDeclaration> = listOf(CalendarEventOneShot)
    override val riskLevel: KsenaxToolRiskLevel = KsenaxToolRiskLevel.MEDIUM
}
