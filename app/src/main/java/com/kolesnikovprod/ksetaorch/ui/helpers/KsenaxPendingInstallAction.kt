package com.kolesnikovprod.ksetaorch.ui.helpers

import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxTranscribingModel
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel

/**
 * Описывает действие, которое нужно продолжить после успешной установки модели.
 *
 * Например, пользователь мог отправить сообщение, но Gemma ещё не была установлена.
 * Тогда приложение сначала предлагает установить модель, а после успешной установки
 * продолжает исходное действие — отправляет сохранённый текст в чат.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxPendingInstallAction {

    /**
     * После установки модели нужно отправить сохранённое сообщение в чат.
     *
     * @property messageText текст сообщения, которое пользователь пытался отправить.
     *
     * @since 0.2
     */
    data class SendChatMessage(
        val messageText: String
    ) : KsenaxPendingInstallAction

    /**
     * После установки модели нужно выбрать её как модель транскрипции.
     *
     * @property model модель транскрипции, которую пользователь хотел выбрать.
     *
     * @since 0.2
     */
    data class SelectTranscribingModel(
        val model: KsenaxTranscribingModel,
        val forSettingsDraft: Boolean = false,
    ) : KsenaxPendingInstallAction

    /**
     * После установки нужно выбрать модель как основной текстовый runtime.
     */
    data class SelectSupportedTextModel(
        val model: KsenaxSupportedTextModel,
        val forSettingsDraft: Boolean = false,
    ) : KsenaxPendingInstallAction
}
