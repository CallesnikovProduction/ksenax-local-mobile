package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotKeywords
import java.util.Locale

/**
 * Ключевые слова для входа в экспериментальный one-shot контур фонарика.
 *
 * Объект только выбирает подходящий protocol для текущего пользовательского
 * текста. Он не решает, включать или выключать фонарик: это по-прежнему
 * выбирает FunctionGemma между `torch_on` и `torch_off`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
object TorchOneShotKeywords : KsenaxOneShotKeywords {

    override fun matches(userMessage: String): Boolean {
        val words = userMessage
            .lowercase(Locale.ROOT)
            .split(WORD_SEPARATOR)
            .filter(String::isNotBlank)

        return words.any { word ->
            word.startsWith("фонар") ||
                word.startsWith("вспыш") ||
                word == "torch" ||
                word == "flashlight"
        } || (
            "свет" in words && words.any { word -> word.startsWith("телефон") }
        )
    }

    private val WORD_SEPARATOR = Regex("""[^\p{L}\p{N}_]+""")
}
