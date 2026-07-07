package com.kolesnikovprod.ksetaorch.ui


/**
 * Одиночка, который централизует **названия** маршрутов, **маршрутные паттерны**
 * для Navigation Compose, **названия маршрутных аргументов**, **значения-заглушки**,
 * **ключи [androidx.lifecycle.SavedStateHandle]**, **builder-функции** для реальных
 * маршрутных строк.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 * @see KsenaxAppRoute
 */
object KsenaxRoutes {

    /**
     * Главный маршрут приложения, главная навигационная база приложения.
     *
     * @since 0.2
     */
    const val GENERAL = "general"

    /**
     * Контракт меню настроек.
     */
    object Settings {

        /**
         * Имя аргумента внутри маршрута.
         */
        const val PAGE_ARGUMENT = "page"

        /**
         * Шаблон маршрута, который регистрируется в [androidx.navigation.compose.NavHost].
         */
        const val PATTERN = "settings/{$PAGE_ARGUMENT}"

        /**
         * Реальный рабочий маршрут по странице.
         */
        fun page(pageName: String): String = "settings/$pageName"
    }

    /**
     * Контракт всех чатовых экранов.
     *
     * Присутствует поддержка трёх режимов чатов:
     * - **basic**: обычный разговорный чат с моделью под определёнными системными промптами;
     * - **agentic**: агентный чат, умеющий выполнять действия;
     * - **temporaric** (*от temporary*): временный чат, не индексируется в базе данных.
     *
     * @since 0.2
     */
    object Chat {

        /**
         * Имя аргумента маршрута. По нему Navigation будет парсить:
         *
         * ```
         * chat/basic/42
         * ```
         */
        const val CHAT_ID_ARGUMENT = "chatId"

        /**
         * Специальный ID, sentinel value, которое означает, что чата нет,
         * нужно создать новый ИЛИ стартовать пустую сессию:
         *
         * ```
         * chat/basic/-1
         * ```
         */
        const val NEW_CHAT_ID = -1L

        /**
         * Контракт ключей временного состояния, которое передаётся между
         * экранами через [androidx.lifecycle.SavedStateHandle].
         *
         * Нужно для удобства парсинга и устранения проблем со спецсимволами.
         * @since 0.2
         */
        object StateKey {
            const val BASIC_INITIAL_MESSAGE            = "bic_chat_initial_message"
            const val AGENTIC_INITIAL_MESSAGE          = "aic_chat_initial_message"
            const val AGENTIC_WORKSPACE_URI_STATE_KEY  = "aic_chat_wkspace_uri"
            const val AGENTIC_WORKSPACE_PATH_STATE_KEY = "aic_chat_wkspace_path"

        }
        const val BASIC_INITIAL_MESSAGE_STATE_KEY = "basic_chat_initial_message"
        const val AGENTIC_INITIAL_MESSAGE_STATE_KEY = "agentic_chat_initial_message"
        const val AGENTIC_WORKSPACE_URI_STATE_KEY = "agentic_chat_workspace_uri"
        const val AGENTIC_WORKSPACE_PATH_STATE_KEY = "agentic_chat_workspace_path"

        /**
         * Шаблон для регистрации экрана, куда подставляется аргумент для чата.
         *
         * Исключение: [TEMPORARIC_CONVERSATION] не имеет аргумента, потому что
         * он не должен индексироваться в принципе.
         *
         * @since 0.2
         */
        object Pattern {
            const val BASIC_CONVERSATION      = "chat/basic/{$CHAT_ID_ARGUMENT}"
            const val AGENTIC_CONVERSATION    = "chat/agentic/{$CHAT_ID_ARGUMENT}"
            const val TEMPORARIC_CONVERSATION = "chat/temporaric"
        }

        const val BASIC_PATTERN = "chat/basic/{$CHAT_ID_ARGUMENT}"
        const val AGENTIC_PATTERN = "chat/agentic/{$CHAT_ID_ARGUMENT}"
        const val TEMPORARIC_PATTERN = "chat/temporaric"

        /**
         * Помощник в построении маршрутов к классическому и агентному чату
         */
        object RouteBuilder {
            fun basic(chatId: Long? = null): String {
                return "chat/basic/${chatId ?: NEW_CHAT_ID}"
            }

            fun agentic(chatId: Long? = null): String {
                return "chat/agentic/${chatId ?: NEW_CHAT_ID}"
            }
        }
    }
}
