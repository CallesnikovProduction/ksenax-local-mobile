package com.kolesnikovprod.ksetaorch.ui.main.settings

/**
 * Объект представляет настройки в конкретный момент времени.
 *
 * @property transcribingModel модель для расшифровки голоса.
 * `null` означает, что модель не выбрана / не установлена.
 * @property responseModel модель для текстового ответа.
 * `null` означает, что модель не выбрана / не установлена.
 * @property contextWindow выбранный размер контекстного окна.
 * По умолчанию — **4096 токенов**.
 * @property launchAnimationEnabled запускает приветственную анимацию глюка.
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 * @see KsenaxContextWindow
 */
data class KsenaxAppSettingsSnapshot(
    val transcribingModel:      KsenaxTranscribingModel?  = null,
    val responseModel:          KsenaxSupportedTextModel? = null,
    val contextWindow:          KsenaxContextWindow       = KsenaxContextWindow.Tokens4K,
    val launchAnimationEnabled: Boolean                   = true,
)

/**
 * Состояние интерфейса вокруг настроек.
 *
 * @property savedSnapshot последняя сохранённая версия настроек.
 * @property draftSnapshot версия, которую пользователь сейчас редактирует.
 * По умолчанию равен сохранённому, пока пользователь не решит изменить его.
 * @property isExitConfirmationVisible визуальный флаг горящей галочки со смыслом:
 * *"У тебя есть несохранённые изменения. Выйти без сохранения? Сохранить?"*
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
data class KsenaxSettingsUiState(
    val savedSnapshot:             KsenaxAppSettingsSnapshot = KsenaxAppSettingsSnapshot(),
    val draftSnapshot:             KsenaxAppSettingsSnapshot = savedSnapshot,
    val isExitConfirmationVisible: Boolean                   = false,
) {
    val hasUnsavedChanges: Boolean
        get() = draftSnapshot != savedSnapshot
}

/**
 * Перечислимый класс доступных размеров контекста.
 *
 * **ВАЖНО: пока приложение находится на стадии разработки, то при выборе большого
 * количества токенов модель может отказаться работать из-за ограничений LiteRT-LM API,
 * либо же ограничений самих моделей.**
 *
 * @property tokenCount техническое значение для константы количества токенов
 * @property label UI-визуал
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 */
enum class KsenaxContextWindow(
    val tokenCount: Int,
    val label:      String,
) {
    Tokens4K(4_096, "4K"),
    Tokens8K(8_192, "8K"),
    Tokens16K(16_384, "16K"),
    Tokens32K(32_768, "32K"),
}

/**
 * Страницы настроек. По ним навигация может безболезненно открыть нужный маршрут.
 */
enum class KsenaxSettingsPage {
    Main,
    VoiceModel,
    ResponseModel,
}
