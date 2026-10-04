package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

/** @since 0.2 */
object ObsidianNoteOneShotProtocol : KsenaxOneShotToolProtocol {
    override val declarations: List<KsenaxOneShotDeclaration> =
        listOf(
            ObsidianNoteOneShot.Write,
            ObsidianNoteOneShot.AppendAnalysis,
        )

    override val riskLevel: KsenaxToolRiskLevel = KsenaxToolRiskLevel.MEDIUM
}
