package com.kolesnikovprod.ksetaorch.live

import com.google.ai.edge.litertlm.*

/** Добавляет официальный non-thinking suffix, когда metadata-шаблон игнорирует флаг.
 * Не меняет UP, каталог или значения ответа. Только для последовательных live-тестов.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@OptIn(ExperimentalApi::class)
internal object LiveQwenConversation {
    private const val ASSISTANT_START = "<|im_start|>assistant\n"
    private const val NO_THINKING = "<think>\n\n</think>\n\n"

    data class Prepared(val conversation: Conversation, val prompt: String)

    fun create(engine: Engine, config: ConversationConfig, template: String?): Conversation {
        if (template == null) return engine.createConversation(config)
        val previous = ExperimentalFlags.overwritePromptTemplate
        ExperimentalFlags.overwritePromptTemplate = template
        return try { engine.createConversation(config) }
        finally { ExperimentalFlags.overwritePromptTemplate = previous }
    }

    fun prepare(engine: Engine, config: ConversationConfig, prompt: String): Prepared {
        val original = engine.createConversation(config).use { it.renderMessageIntoString(Message.user(prompt)) }
        val rendered = when {
            original.endsWith(ASSISTANT_START + NO_THINKING) -> original
            original.endsWith(ASSISTANT_START) -> original + NO_THINKING
            else -> error("Unknown Qwen assistant generation boundary; cannot apply non-thinking suffix")
        }
        // Сохраняем tools для штатного SDK-parser-а, но не рендерим их повторно.
        val replayConfig = config.copy(systemInstruction = null)
        replay(engine, replayConfig).use { checker ->
            check(checker.renderMessageIntoString(Message.user(rendered)) == rendered)
        }
        return Prepared(replay(engine, replayConfig), rendered)
    }

    private fun replay(engine: Engine, config: ConversationConfig): Conversation {
        return create(engine, config, "{{ messages[-1].content }}")
    }
}
