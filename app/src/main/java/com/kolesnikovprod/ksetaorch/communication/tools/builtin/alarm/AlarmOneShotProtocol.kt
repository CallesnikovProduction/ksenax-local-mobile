package com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

/**
 * Набор атомарных alarm-declarations поверх общего FunctionGemma protocol.
 *
 * @since 0.2
 */
object AlarmOneShotProtocol : KsenaxOneShotToolProtocol {

    override val declarations: List<KsenaxOneShotDeclaration> =
        listOf(
            AlarmToolOneShot.AtTime,
            AlarmToolOneShot.AfterHours,
            AlarmToolOneShot.AfterMinutes,
            AlarmToolOneShot.AtDateTime,
            AlarmToolOneShot.ClearAll,
        )

    override val riskLevel: KsenaxToolRiskLevel = KsenaxToolRiskLevel.MEDIUM
}
