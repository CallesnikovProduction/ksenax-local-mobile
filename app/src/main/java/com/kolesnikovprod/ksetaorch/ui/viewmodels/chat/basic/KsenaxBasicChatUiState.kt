package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic

import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxMessage

/**
 * Описывает всё состояние presentation-слоя для [KsenaxBasicChatViewModel]
 * (обычного текстового чата с локальной моделью).
 *
 * UI-состояние — то, что нужно передать наверх для экрана, чтобы правильно нарисовать
 * список чатов, активный чат, поле ввода, временные сообщения, частичный ответ,
 * ошибки и так далее.
 *
 * @property inputText текущий текст в поле воода
 * @property chats список уже промаппенных чатов
 * @property activeChatId ID активного чата
 * @property transientUserText временный текст пользовательского сообщения. Это нужно
 * для быстрого отображения пользовательского текста сообщения, не дожидаясь
 * сохранения в истории чата
 * @property streamingAssistantText временный текст ответа ассистента, который
 * собирается во время streaming-генерации
 * @property generationDurationMillis время генерации текущего или
 * только что завершённого ответа ассистента
 * @property isGenerating флаг активной генерации
 * @property modelGateState перед первым сообщением [androidx.lifecycle.ViewModel]
 * должна првоерить модель на существование, пройти integrity-проверку для
 * подготовки к инференсу
 * @property errorMessage текст ошибки для отображения в UI
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxBasicChatUiState(
    val inputText:                String                    = "",
    val chats:                    List<KsenaxChat>          = emptyList(),
    val activeChatId:             Long?                     = null,
    val transientUserText:        String?                   = null,
    val streamingAssistantText:   String                    = "",
    val generationDurationMillis: Long?                     = null,
    val isGenerating:             Boolean                   = false,
    val modelGateState:           KsenaxBasicModelGateState = KsenaxBasicModelGateState.Idle,
    val errorMessage:             String?                   = null,
) {
    /**
     * Склееный чат для отображения на экране. Берёт persisted-сообщения из
     * `storedChat`, добавляя временные UI-сообщения.
     */
    val activeChat: KsenaxChat?
        get() {
            val storedChat = chats.firstOrNull { chat ->
                chat.id == activeChatId
            }
            val transientMessages = buildList {
                addAll(storedChat?.messages.orEmpty())

                transientUserText?.takeIf(String::isNotBlank)?.let { text ->
                    if (lastOrNull()?.text != text || lastOrNull()?.isUser != true) {
                        add(KsenaxMessage(text = text))
                    }
                }

                if (streamingAssistantText.isNotEmpty()) {
                    add(
                        KsenaxMessage(
                            text                     = streamingAssistantText,
                            isUser                   = false,
                            generationDurationMillis = generationDurationMillis,
                            isStreaming              = isGenerating,
                        ),
                    )
                }
            }

            if (storedChat == null && transientMessages.isEmpty()) {
                return null
            }

            return storedChat?.copy(messages = transientMessages) ?: KsenaxChat(
                id       = PENDING_CHAT_ID,
                mode     = ChatMode.Basic,
                title    = transientUserText.orEmpty(),
                messages = transientMessages,
            )
        }

    /**
     * Экран считается заблокированным, если model-gate находится не в `Idle` и не в `Ready`.
     */
    val isScreenBlocked: Boolean
        get() = modelGateState != KsenaxBasicModelGateState.Ready &&
            modelGateState != KsenaxBasicModelGateState.Idle

    /**
     * Состояние “генерация уже началась, но первый текстовый кусок ещё не пришёл”.
     */
    val isAwaitingAssistantText: Boolean
        get() = isGenerating && streamingAssistantText.isEmpty()

    private companion object {
        const val PENDING_CHAT_ID = Long.MIN_VALUE
    }
}

/**
 * Описывает состояние проверки и подготовки локальной модели.
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
sealed interface KsenaxBasicModelGateState {
    /**
     * Начальное состояние, когда модель ещё не проверялась в рамках текущего экрана/сессии.
     */
    data object Idle              : KsenaxBasicModelGateState

    /**
     * Идёт проверка наличия файла модели (наличие файла `.litertlm`).
     */
    data object CheckingPresence  : KsenaxBasicModelGateState

    /**
     * Идёт проверка целостности модели (проверка SHA-256).
     */
    data object CheckingIntegrity : KsenaxBasicModelGateState

    /**
     * Модель прошла базовые проверки, теперь runtime/coordinator готовится к работе.
     */
    data object PreparingModel    : KsenaxBasicModelGateState

    /**
     * Промежуточное состояние:
     * модель уже подготовлена, но UI ещё короткое время показывает подтверждение готовности.
     */
    data object ModelPrepared     : KsenaxBasicModelGateState

    /**
     * Ошибка проверки или подготовки модели.
     */
    data class Failure(
        val message: String,
        val stage:   KsenaxBasicModelFailureStage,
    ) : KsenaxBasicModelGateState

    /**
     * Модель проверена, подготовлена и готова принимать сообщения.
     *
     * В этом состоянии пользователь может отправлять сообщения без повторной проверки.
     */
    data object Ready             : KsenaxBasicModelGateState
}

/**
 * Уточняет, на каком этапе model-gate сломался.
 *
 *
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
enum class KsenaxBasicModelFailureStage {

    /**
     * Файл не найден.
     */
    Presence,

    /**
     * Файл модели найден, но не прошёл проверку целостности.
     */
    Integrity,

    /**
     * Файл модели найден, целостность подтверждена, но модель не удалось подготовить к inference.
     */
    Preparation,
}

/**
 * KsenaxBasicChatEffect описывает одноразовые события, которые не должны храниться в UiState.
 *
 * Это не состояние экрана, а команды/сигналы для UI.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxBasicChatEffect {

    /**
     * Событие: начальное сообщение было успешно принято и сохранено/зафиксировано.
     */
    data class InitialMessageCommitted(val text: String) : KsenaxBasicChatEffect

    /**
     * Одноразовая команда удалить чат.
     */
    data class DeleteChat(
        val chatId:       Long,
        val returnToMain: Boolean,
    ) : KsenaxBasicChatEffect

    /**
     * Команда выйти из Basic Chat на главный экран.
     */
    data object ExitToMain : KsenaxBasicChatEffect
}
