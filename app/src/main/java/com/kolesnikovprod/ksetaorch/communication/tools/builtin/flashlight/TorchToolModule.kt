package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionPlanningMode
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import java.util.Locale

/**
 * OneShot-kit фонарика для короткого FunctionGemma prompt-а.
 *
 * В agentic-режиме фонарик не планируется через G4: runtime узнаёт его по
 * ключевым словам, подсказывает целевую FG function и всё равно пропускает
 * команду через FunctionGemma OneShot.
 *
 * @property executor исполнитель `torch_on`, `torch_off` и `torch_toggle`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class TorchToolModule(
    override val executor: KsenaxToolExecutor,
) : KsenaxOneShotActionKit {

    override val id: String = "system.torch.oneshot"

    override val namespace: String = "system"

    override val planningMode: KsenaxActionPlanningMode =
        KsenaxActionPlanningMode.NonPlanable

    override val actionSpecs: List<KsenaxWorkActionSpec> =
        listOf(
            KsenaxWorkActionSpec(
                name = TorchToolOneShot.On.codeName,
                description = "Turns on the device flashlight.",
                inputHint = "No input object is needed.",
                examples = listOf("Включи фонарик -> torch_on"),
            ),
            KsenaxWorkActionSpec(
                name = TorchToolOneShot.Off.codeName,
                description = "Turns off the device flashlight.",
                inputHint = "No input object is needed.",
                examples = listOf("Выключи фонарик -> torch_off"),
            ),
            KsenaxWorkActionSpec(
                name = TorchToolOneShot.Toggle.codeName,
                description = "Toggles the device flashlight when the user does not say on or off.",
                inputHint = "No input object is needed.",
                examples = listOf("Фонарик -> torch_toggle"),
            ),
        )

    override val keywords: KsenaxOneShotKeywords = TorchOneShotKeywords

    override val protocol: KsenaxOneShotToolProtocol = TorchOneShotProtocol

    override fun preferredDirectActionName(userMessage: String): String? {
        val text = userMessage
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
        return when {
            "выключ" in text ||
                "отключ" in text ||
                "погас" in text ||
                "убери свет" in text ||
                "потуш" in text ->
                TorchToolOneShot.Off.codeName
            "включ" in text ||
                "зажг" in text ||
                "вруби" in text ||
                "дай свет" in text ||
                "посвет" in text ->
                TorchToolOneShot.On.codeName
            text == "фонарик" ||
                text == "фонарь" ||
                "переключ" in text ->
                TorchToolOneShot.Toggle.codeName
            else -> null
        }
    }

    override fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall =
        if (supportsAction(step.actionName)) {
            compiledCall.copy(name = step.actionName)
        } else {
            compiledCall
        }

}
