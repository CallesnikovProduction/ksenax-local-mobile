package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxDirectActionRoute
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionInputDraft
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxActionSourceText
import java.time.ZonedDateTime
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxOneShotActionKit
import com.kolesnikovprod.ksetaorch.communication.work.actions.KsenaxWorkActionSpec
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.MissingActionArgument

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

    override fun validateSourceRequest(userMessage: String) {
        if (sourceDirections(userMessage).isEmpty()) {
            throw MissingActionArgument("явный запрос управления светом телефона")
        }
    }

    override fun buildFastActionDraft(userMessage: String, actionName: String, now: ZonedDateTime): KsenaxActionInputDraft? {
        val expected = expectedActions(userMessage).singleOrNull() ?: return null
        return KsenaxActionInputDraft(expectedActionName = expected, instruction = userMessage)
    }

    override fun validateSourceCall(userMessage: String, actionName: String) {
        validateSourceRequest(userMessage)
        val expected = expectedActions(userMessage)
        if (expected.isNotEmpty() && actionName !in expected) {
            throw MissingActionArgument("команду фонарика без противоречия направлению включить/выключить")
        }
    }

    override fun validatePlannedInputs(userMessage: String, requestTime: ZonedDateTime, calls: List<KsenaxToolCall>) {
        val expected = sourceDirections(userMessage)
        if (calls.size != expected.size || calls.zip(expected).any { (call, name) -> name != null && call.name != name }) {
            throw MissingActionArgument("полный план фонарика в исходном порядке включения и выключения")
        }
    }

    private fun expectedActions(userMessage: String): Set<String> = sourceDirections(userMessage).filterNotNull().toSet()

    private fun sourceDirections(userMessage: String): List<String?> {
        val parts = KsenaxActionSourceText.executableParts(userMessage)
        val owned = parts.map(TorchOneShotKeywords::matches)
        return buildList {
            parts.forEachIndexed { index, part ->
                val text = part.lowercase().trim(' ', '.', ',', '!', '?')
                // «Включи, затем выключи фонарь» разделяет объект, но не чужое действие.
                val sharesObject = bareDirection.matches(text) &&
                    (owned.getOrNull(index - 1) == true || owned.getOrNull(index + 1) == true)
                val requested = torchCommand.containsMatchIn(text) || desiredState.containsMatchIn(text) ||
                    bareTorch.matches(text) || KsenaxActionSourceText.hasExplicitNeed(text, torchObject)
                if (requested && (owned[index] || sharesObject)) {
                    val before = size
                    if (offDirection.containsMatchIn(text)) add(TorchToolOneShot.Off.codeName)
                    if (onDirection.containsMatchIn(text)) add(TorchToolOneShot.On.codeName)
                    if (bareTorch.matches(text)) add(TorchToolOneShot.Toggle.codeName)
                    // Для неявного направления сохраняем место команды, не выбирая за FG.
                    if (size == before) add(null)
                }
            }
        }
    }

    private val offDirection = Regex("выключ|погаси|погасить|отключ|выруб")
    private val onDirection = Regex("включ|зажги|зажечь|вруб")
    private val bareTorch = Regex("^(фонар\\p{L}*|flashlight|torch)$")
    private val torchObject = Regex("(?:фонар\\p{L}*|вспыш\\p{L}*|flashlight|torch|свет)(?![\\p{L}\\p{N}_])")
    private val torchCommand = Regex("(?<![\\p{L}\\p{N}_])(?:включи(?:те|ть)?|выключи(?:те|ть)?|включай(?:те)?|выключай(?:те)?|зажги(?:те)?|зажечь|погаси(?:те|ть)?|гаси(?:те|ть)?|вруби(?:те|ть)?|выруби(?:те|ть)?|отключи(?:те|ть)?|turn(?=\\s))(?![\\p{L}\\p{N}_])")
    private val desiredState = Regex("сделай\\s+(?:так[,\\s]+)?чтобы")
    private val bareDirection = Regex(
        "^(?:пожалуйста\\s+)?(?:включ|выключ|заж|погас|отключ|вруб|выруб)\\p{L}*(?:\\s+(?:его|ее|это|пожалуйста))*$",
    )
}
