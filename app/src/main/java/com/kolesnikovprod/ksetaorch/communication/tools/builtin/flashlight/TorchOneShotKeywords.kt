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

    private val objectWords: Set<String> =
        setOf(
            "фонарик",
            "фонарь",
            "фонаря",
            "фонарём",
            "фонарем",
            "вспышка",
            "вспышку",
            "свет",
            "torch",
            "flashlight",
        )

    /**
     * Проверяет нормализованный prompt без учёта регистра и лишних пробелов.
     */
    override fun matches(userMessage: String): Boolean {
        val normalizedPrompt = userMessage
            .lowercase(Locale.ROOT)
            .normalizeWhitespace()
            .trim()

        return objectWords.any(normalizedPrompt::contains)
    }

    private fun String.normalizeWhitespace(): String =
        buildString(length) {
            var previousWasWhitespace = false
            this@normalizeWhitespace.forEach { symbol ->
                if (symbol.isWhitespace()) {
                    if (!previousWasWhitespace) {
                        append(' ')
                        previousWasWhitespace = true
                    }
                } else {
                    append(symbol)
                    previousWasWhitespace = false
                }
            }
        }
}
