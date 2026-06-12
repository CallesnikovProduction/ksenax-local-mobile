package com.kolesnikovprod.ksetaorch.ui

/**
 * Связка состояний в классе [KsenaxHomeScreen], фактически предполагает создание одного.
 *
 * Смысл создания заключается в удержании всех состояний в одном месте. А при изменении:
 * ```kotlin
 * uiState= uiState.copy(
 *     // ...
 * )
 * ```
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
data class HomeUiState(

    /**
     * Есть ли у приложения готовая и валидная модель?
     *
     * В коде влияет на отображение кнопки скачивания и доступность работы с агентом.
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isDownloadRemembered: Boolean = false,

    /**
     * Идёт ли загрузка модели?
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isDownloading: Boolean = false,

    /**
     * Прервана ли была загрузка или файл оказался невалидным (из-за интернета, например)?
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isDownloadInterrupted: Boolean = false,

    /**
     * Пользователь отменил загрузку?
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isDownloadCancelled: Boolean = false,

    /**
     * Идёт ли индексация и сверка хешей у модели и хеша с HuggingFace?
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isModelValidating: Boolean = false,

    /**
     * Локальная модель сейчас занята обработкой команды (допустим, включением фонарика)?
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isRoutingPrompt: Boolean = false,

    /**
     * Была ли отправлена первая команда для отображения ОДНОРАЗОВОЙ переписки?
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val isConversationOpen: Boolean = false,

    /**
     * Процесс загрузки модели (для преобразования в проценты следует домножить на 100 это поле)
     * @since 0.1
     * @author Stephan Kolesnikov
     */
    val downloadProgress: Float = 0f
)
