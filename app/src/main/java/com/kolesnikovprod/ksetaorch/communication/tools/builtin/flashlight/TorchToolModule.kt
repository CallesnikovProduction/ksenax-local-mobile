package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxDirectActionRoute
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionInputDraft
import java.time.ZonedDateTime
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol

/**
 * OneShot-kit фонарика для короткого FunctionGemma prompt-а.
 *
 * Фонарик доступен как быстрый direct-action и как атомарный шаг G4-плана.
 * `torch_on`, `torch_off` или `torch_toggle` выбирает FunctionGemma.
 * Локальная проверка не допускает противоречия явной команде включить/выключить.
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

    override val supportsFastPath = true

    override val directRoute: KsenaxDirectActionRoute =
        KsenaxDirectActionRoute(
            description = "one flashlight on, off, or toggle command",
            keywords = TorchOneShotKeywords,
        )

    override val actionSpecs: List<KsenaxWorkActionSpec> =
        listOf(
            KsenaxWorkActionSpec(
                name = TorchToolOneShot.On.codeName,
                description = "Turns on the device flashlight.",
                inputHint = "No input object is needed.",
            ),
            KsenaxWorkActionSpec(
                name = TorchToolOneShot.Off.codeName,
                description = "Turns off the device flashlight.",
                inputHint = "No input object is needed.",
            ),
            KsenaxWorkActionSpec(
                name = TorchToolOneShot.Toggle.codeName,
                description = "Toggles the device flashlight when the user does not say on or off.",
                inputHint = "No input object is needed.",
            ),
        )

    override val protocol: KsenaxOneShotToolProtocol = TorchOneShotProtocol

    override fun buildFastActionDraft(userMessage: String, actionName: String, now: ZonedDateTime): KsenaxActionInputDraft? {
        val text = userMessage.lowercase().trim(' ', '.', ',', '!', '?')
        val expected = when {
            Regex("выключ|погаси|погасить|отключ").containsMatchIn(text) -> TorchToolOneShot.Off.codeName
            Regex("включ|зажги|зажечь").containsMatchIn(text) -> TorchToolOneShot.On.codeName
            Regex("^(фонар\\p{L}*|flashlight|torch)$").matches(text) -> TorchToolOneShot.Toggle.codeName
            else -> return null
        }
        return KsenaxActionInputDraft(expectedActionName = expected, instruction = userMessage)
    }
}
