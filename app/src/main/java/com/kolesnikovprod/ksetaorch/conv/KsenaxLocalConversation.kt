package com.kolesnikovprod.ksetaorch.conv

import com.google.ai.edge.litertlm.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Обертка над LiteRT-LM conversation для локального общения с моделью.
 *
 * Класс управляет жизненным циклом Engine и Conversation: создает runtime,
 * инициализирует модель, отправляет пользовательские сообщения и закрывает
 * нативные ресурсы при уходе со сценария.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class KsenaxLocalConversation(
    private val modelPath: String,
    private val cacheDirPath: String,
    private val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /**
     * Инициализирует LiteRT-LM engine и conversation.
     *
     * Повторный вызов безопасен: если conversation уже создана, метод ничего
     * не пересоздает. Тяжелая работа выполняется на [Dispatchers.Default].
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    suspend fun initialize() {
        if (conversation != null) return

        withContext(Dispatchers.Default) {
            File(cacheDirPath).mkdirs()

            val createdEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDirPath,
                )
            )

            createdEngine.initialize()

            engine = createdEngine
            conversation = createdEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction)
                )
            )
        }
    }

    /**
     * Отправляет prompt в уже инициализированную conversation.
     *
     * Метод требует предварительного вызова [initialize], иначе выбрасывает
     * ошибку с понятным текстом для вызывающего слоя.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    suspend fun ask(prompt: String): String {
        val activeConversation = requireNotNull(conversation) {
            "Conversation is not initialized"
        }

        return withContext(Dispatchers.Default) {
            activeConversation.sendMessage(prompt).toString()
        }
    }

    /**
     * Закрывает LiteRT-LM engine и очищает ссылку на conversation.
     *
     * Метод вызывается при уходе экрана, чтобы не держать нативные ресурсы модели
     * дольше, чем нужно.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun close() {
        conversation = null
        engine?.close()
        engine = null
    }

    private companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "Ты Ksenax, локальный Android-агент. Не выдумывай системные действия."
    }
}
