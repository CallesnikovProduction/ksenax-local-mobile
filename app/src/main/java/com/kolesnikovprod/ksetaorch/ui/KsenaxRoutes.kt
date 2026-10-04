package com.kolesnikovprod.ksetaorch.ui


/**
 * Единый контракт маршрутов корневого Navigation Compose-графа.
 *
 * Объект хранит route-patterns, имена аргументов, sentinel-значения,
 * ключи временного navigation-state и функции построения destination route.
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
     *
     * @since 0.2
     */
    object Settings {

        /**
         * Имя аргумента внутри маршрута.
         *
         * @since 0.2
         */
        const val PAGE_ARGUMENT = "page"

        /**
         * Шаблон маршрута, который регистрируется в [androidx.navigation.compose.NavHost].
         *
         * @since 0.2
         */
        const val PATTERN = "settings/{$PAGE_ARGUMENT}"

        /**
         * Возвращает destination route для страницы с именем [pageName].
         *
         * @since 0.2
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
         * Ключи одноразового состояния запуска чата, которое передаётся через
         * [androidx.lifecycle.SavedStateHandle] главного back-stack entry.
         *
         * @since 0.4
         */
        object StateKey {
            const val BASIC_INITIAL_MESSAGE = "basic_chat_initial_message"
            const val AGENTIC_INITIAL_MESSAGE = "agentic_chat_initial_message"
            const val AGENTIC_WORKSPACE_URI = "agentic_chat_workspace_uri"
            const val AGENTIC_WORKSPACE_PATH = "agentic_chat_workspace_path"
        }

        /**
         * Шаблон для регистрации экрана, куда подставляется аргумент для чата.
         *
         * Исключение: [Pattern.TEMPORARIC_CONVERSATION] не имеет аргумента, потому что
         * он не должен индексироваться в принципе.
         *
         * @since 0.2
         */
        object Pattern {
            const val BASIC_CONVERSATION      = "chat/basic/{$CHAT_ID_ARGUMENT}"
            const val AGENTIC_CONVERSATION    = "chat/agentic/{$CHAT_ID_ARGUMENT}"
            const val TEMPORARIC_CONVERSATION = "chat/temporaric"
        }

        /**
         * Строит destination routes к сохраняемым чатам.
         *
         * @since 0.2
         */
        object RouteBuilder {
            /**
             * Возвращает route Basic-чата. Отсутствующий [chatId] означает
             * новый чат и кодируется через [NEW_CHAT_ID].
             *
             * @since 0.4
             */
            fun basic(chatId: Long? = null): String {
                return "chat/basic/${chatId ?: NEW_CHAT_ID}"
            }

            /**
             * Возвращает route Agentic-чата. Отсутствующий [chatId] означает
             * новый чат и кодируется через [NEW_CHAT_ID].
             *
             * @since 0.4
             */
            fun agentic(chatId: Long? = null): String {
                return "chat/agentic/${chatId ?: NEW_CHAT_ID}"
            }
        }
    }
}
