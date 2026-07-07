package com.kolesnikovprod.ksetaorch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.ui.helpers.currentResponseModel
import com.kolesnikovprod.ksetaorch.ui.helpers.rememberGeneralBackStackEntry
import com.kolesnikovprod.ksetaorch.ui.helpers.rememberKsenaxApplication
import com.kolesnikovprod.ksetaorch.ui.helpers.settingsStringPageNameToEnum
import com.kolesnikovprod.ksetaorch.ui.main.chat.KsenaxBasicChatScreen
import com.kolesnikovprod.ksetaorch.ui.main.chat.KsenaxAgenticChatScreen
import com.kolesnikovprod.ksetaorch.ui.main.chat.KsenaxTemporaricChatScreen
import com.kolesnikovprod.ksetaorch.ui.main.KsenaxMainScreen
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxAppSettingsRoute
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.agentic.KsenaxAgenticChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.temporaric.KsenaxTemporaricChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel

/**
 * Корневой верхнеуровневый маршрутный компонент приложения Ksenax.
 *
 * Эта функция является navigation composition root для основного UI-графа:
 * главного экрана, настроек и трёх режимов чата — basic, agentic и temporaric.
 *
 * Здесь намеренно сосредоточена "грязная" glue-логика верхнего уровня:
 *
 * - создание [androidx.navigation.NavController];
 * - описание [NavHost];
 * - привязка shared [KsenaxMainViewModel] к back stack entry главного экрана;
 * - создание chat-specific ViewModel через factory;
 * - передача одноразовых initial-message параметров через [androidx.lifecycle.SavedStateHandle];
 * - маршрутизация между режимами чата и настройками.
 *
 * Важно: этот компонент не должен содержать бизнес-логику агента, модельного
 * inference, исполнения tools или работы с хранилищем. Его зона ответственности —
 * только связывание экранов, ViewModel и route-параметров.
 *
 * @param ksenaxVersion версия приложения, отображаемая на главном экране.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxAppRoute(ksenaxVersion: Float) {

    /**
     * Создаёт контроллер навигации и сохраняет его между рекомпозициями.
     *
     * @since 0.2
     */
    val navController = rememberNavController()

    /**
     * Вспомогательный callback, который нужен, чтобы разные экраны могли открыть настройки,
     * при этом не зная маршрутную-строку напрямую.
     *
     * @since 0.2
     */
    val openSettings: (KsenaxSettingsPage) -> Unit = { page ->
        navController.navigate(KsenaxRoutes.Settings.page(page.name)) {
            popUpTo(KsenaxRoutes.GENERAL)
            launchSingleTop = true // защита от дублирования одного и того же экрана сверху стека.
        }
    }

    NavHost(
        navController    = navController,
        startDestination = KsenaxRoutes.GENERAL
    ) {
        /*
         * ╔═════════════════════════════════════
         * ║  MAIN SCREEN ROUTE (MainViewModel)
         * ╚═════════════════════════════════════
         */
        composable(KsenaxRoutes.GENERAL) {

            /**
             * В Android у каждого приложения есть объект Application.
             *
             * Базово Android знает только:
             * ```kotlin
             * android.app.Application
             * ```
             *
             * Но из-за того, что приложение работает с ручным DI, то контекст необходимо
             * закастить до [KsenaxAndroidApplication].
             * А внутри уже будут доступны зависимости приложения.
             *
             * Вот так сделать без каста не получится:
             * ```kotlin
             * val application = context.applicationContext
             * application.chatRepository // Unresolved reference
             * ```
             */
            val application = rememberKsenaxApplication()

            // Здесь ViewModel создаётся обычным способом
            // и привязывается к текущему NavBackStackEntry.
            val mainViewModel: KsenaxMainViewModel = viewModel<KsenaxMainViewModel>()

            val responseModel = mainViewModel.currentResponseModel()

            // Для временного чата уже создалась модель.
            // Нам не нужна индексация чата в репозитории, поэтому уже имеется «сырой диалог»
            val temporaricChatViewModel: KsenaxTemporaricChatViewModel =
                viewModel(
                    key     = "temporaric-${responseModel.name}",
                    factory = KsenaxTemporaricChatViewModel.Factory(application, responseModel)
                )

            KsenaxMainScreen(
                viewModel            = mainViewModel,
                appVersion           = ksenaxVersion,

                /*
                 * Mental SELECTED callback-scope (пользователь выбрал уже существующий чат):
                 *
                 * - Пользователь нажал на чат
                 * - KsenaxMainScreen ловит клик
                 * - вызывает соответствующий callback
                 * - срабатывает лямбда ОТСЮДА (AppRoute)
                 * - происходит навигация к соответствующему чату
                 *
                 * Это правильное разделение ответственности, потому что:
                 *   Screen    = показывает UI и сообщает события
                 *   Route     = знает навигацию
                 *   ViewModel = знает состояние
                 */

                onBasicChatSelected    = { chatId ->
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.basic(chatId))
                },
                onAgenticChatSelected = { chatId ->
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.agentic(chatId))
                },

                /*
                 * Mental REQUESTED callback-scope (пользователь хочет начать новый чат,
                 *                                  возможно с начальным сообщением):
                 *
                 * initialMessage - произвольный пользовательский текст,
                 * который ОПАСНО ПИХАТЬ ПРЯМО в маршрутную строку:
                 *   URL кодировка
                 *   специсимволика
                 *   и так далее...
                 *
                 * Именно в этот момент выручает SavedStateHandle (key-value vault,
                 * привязанное к конкретному NavBackStackEntry / ViewModel)
                 */
                onBasicChatRequested = { initialMessage ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle // бери текущую запись -> savedStateHandle
                        ?.set(             // положи туда initMsg по соответствующему ключу
                            KsenaxRoutes.Chat.BASIC_INITIAL_MESSAGE_STATE_KEY,
                            initialMessage,
                        )
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.basic())
                },
                onAgenticChatRequested = { initialMessage, workspaceUri, workspacePath ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.apply {
                            set(
                                KsenaxRoutes.Chat.AGENTIC_INITIAL_MESSAGE_STATE_KEY,
                                initialMessage,
                            )
                            // Защита от старого URI, который мог бы протечь случайно,
                            // когда создавался предыдущий агентный чат.
                            if (workspaceUri == null) {
                                remove<String>(
                                    KsenaxRoutes.Chat.AGENTIC_WORKSPACE_URI_STATE_KEY
                                )
                            } else {
                                set(
                                    KsenaxRoutes.Chat.AGENTIC_WORKSPACE_URI_STATE_KEY,
                                    workspaceUri,
                                )
                            }
                            set(
                                KsenaxRoutes.Chat.AGENTIC_WORKSPACE_PATH_STATE_KEY,
                                workspacePath,
                            )
                        }
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.agentic())
                },

                onTemporaricChatRequested = { initialMessage ->
                    mainViewModel.onModeSelected(ChatMode.Temporaric)
                    initialMessage
                        .takeIf(String::isNotBlank)
                        ?.let(temporaricChatViewModel::onMessageFromMain)
                    navController.navigate(KsenaxRoutes.Chat.TEMPORARIC_PATTERN) {
                        // защита, чтобы не плодить экраны поверх
                        launchSingleTop = true
                    }
                },
                onSettingsRequested = openSettings,
            )
        }

        /*
         * ╔═════════════════════════════════════════
         * ║  SETTINGS SCREEN ROUTE (MainViewModel)
         * ╚═════════════════════════════════════════
         */
        composable(
            // Подставляется общая схема рутинга
            route = KsenaxRoutes.Settings.PATTERN,
            // Аргументы, которые передаются для маршрута
            arguments = listOf(
                navArgument(KsenaxRoutes.Settings.PAGE_ARGUMENT) {
                    type         = NavType.StringType
                    defaultValue = KsenaxSettingsPage.Main.name
                },
            ),
        ) { backStackEntry ->

            /*
            * backStackEntry - карточка именно Settings Route.
            *
            * У неё есть:
            *   route = settings/...
            *   arguments = Bundle с PAGE_ARGUMENT
            *   savedStateHandle
            *   lifecycle
            *   viewModelStore
            * */

            // Получаем предыдущий entry (MainViewModel)
            val generalBackStackEntry = rememberGeneralBackStackEntry(navController)

            // Вот этот мув примерно упрощённо говорит о том, чтобы
            // ViewModel создавалась* ИЗ УЖЕ СУЩЕСТВУЮЩЕЙ
            //
            // (* - слово "создавалась" используется в косвенном смысле)
            val mainViewModel: KsenaxMainViewModel = viewModel(
                viewModelStoreOwner = generalBackStackEntry,
            )

            // достаётся имя стартовой страницы
            val initialPageName = backStackEntry.arguments
                ?.getString(KsenaxRoutes.Settings.PAGE_ARGUMENT)

            val enumedInitialPage = settingsStringPageNameToEnum(initialPageName)

            KsenaxAppSettingsRoute(
                viewModel    = mainViewModel,      // общий MainViewModel
                initialPage  = enumedInitialPage,  // какую страницу настроек открыть первой
                onExitToMain = {                   // что делать при выходе?
                    navController.popBackStack(
                        route     = KsenaxRoutes.GENERAL,
                        inclusive = false,

                        /*
                        * inclusive не удаляет GENERAL.
                        *
                        * Во-первых, в ситуации допустим:
                        *  GENERAL -> SETTINGS при удалении SETTINGS:
                        *
                        * - inclusive == true: удаляется SETTINGS, GENERAL
                        * - inclusive == false: удаляется только SETTINGS
                        *
                        * Во-вторых, если навигацию пустить на GENERAL, то можно получить:
                        * GENERAL -> SETTINGS -> GENERAL. А это перебор цепочки.
                        * */
                    )
                },
            )
        }

        /*
         * ╔═══════════════════════════════════════════════
         * ║  TEMPORARIC SCREEN ROUTE (TemporaricViewModel)
         * ╚═══════════════════════════════════════════════
         */
        composable(KsenaxRoutes.Chat.TEMPORARIC_PATTERN) {
            // ручной DI
            val application = rememberKsenaxApplication()

            // возможность вернуться обратно
            val generalBackStackEntry = rememberGeneralBackStackEntry(navController)

            val mainViewModel: KsenaxMainViewModel = viewModel(
                viewModelStoreOwner = generalBackStackEntry,
            )

            val responseModel = mainViewModel.currentResponseModel()
            val temporaricChatViewModel: KsenaxTemporaricChatViewModel =
                viewModel(
                    viewModelStoreOwner = generalBackStackEntry,
                    key                 = "temporaric-${responseModel.name}",
                    factory             = KsenaxTemporaricChatViewModel.Factory(
                        application   = application,
                        responseModel = responseModel,
                    ),
                )

            KsenaxTemporaricChatScreen(
                viewModel                = temporaricChatViewModel,
                mainViewModel            = mainViewModel,
                onInitialMessageAccepted = mainViewModel::onTemporaricMessageAccepted,
                onModeRequested          = mainViewModel::onModeSelected,
                onBasicChatSelected      = { chatId ->
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.basic(chatId)) {
                        popUpTo(KsenaxRoutes.GENERAL)
                    }
                },
                onAgenticChatSelected    = { chatId ->
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.agentic(chatId)) {
                        popUpTo(KsenaxRoutes.GENERAL)
                    }
                },
                onSettingsRequested      = openSettings,
                onExitToMain             = {
                    navController.popBackStack(
                        route     = KsenaxRoutes.GENERAL,
                        inclusive = false,
                    )
                },
            )
        }

        /*
         * ╔═══════════════════════════════════════════════
         * ║  BASIC SCREEN ROUTE (BasicViewModel)
         * ╚═══════════════════════════════════════════════
         */
        composable(
            route = KsenaxRoutes.Chat.BASIC_PATTERN,
            arguments = listOf(
                navArgument(KsenaxRoutes.Chat.CHAT_ID_ARGUMENT) {
                    type         = NavType.LongType
                    defaultValue = KsenaxRoutes.Chat.NEW_CHAT_ID
                },
            ),
        ) { backStackEntry ->
            val application = rememberKsenaxApplication()

            val chatIdArgument = backStackEntry.arguments
                ?.getLong(KsenaxRoutes.Chat.CHAT_ID_ARGUMENT)
                ?: KsenaxRoutes.Chat.NEW_CHAT_ID
            val initialChatId = chatIdArgument
                .takeUnless { chatId -> chatId == KsenaxRoutes.Chat.NEW_CHAT_ID }

            val generalBackStackEntry = rememberGeneralBackStackEntry(navController)

            val mainViewModel: KsenaxMainViewModel = viewModel(
                viewModelStoreOwner = generalBackStackEntry,
            )
            val responseModel = mainViewModel.currentResponseModel()

            val basicChatViewModel: KsenaxBasicChatViewModel = viewModel(
                factory = KsenaxBasicChatViewModel.Factory(
                    application = application,
                    initialChatId = initialChatId,
                    responseModel = responseModel,
                ),
            )

            // забирается первое сообщение, которое уже будет
            // передаваться конкретно на экран стандартного чата.
            val initialMessage = generalBackStackEntry.savedStateHandle
                .get<String>(KsenaxRoutes.Chat.BASIC_INITIAL_MESSAGE_STATE_KEY)

            KsenaxBasicChatScreen(
                viewModel = basicChatViewModel,
                mainViewModel = mainViewModel,
                initialMessage = initialMessage,
                onInitialMessageCommitted = { committedMessage ->
                    mainViewModel.onBasicMessageCommitted(committedMessage)
                    generalBackStackEntry.savedStateHandle
                        .remove<String>(KsenaxRoutes.Chat.BASIC_INITIAL_MESSAGE_STATE_KEY)
                },
                onAgenticChatSelected = { chatId ->
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.agentic(chatId)) {
                        popUpTo(KsenaxRoutes.GENERAL)
                    }
                },
                onAgenticModeRequested = {
                    mainViewModel.onModeSelected(ChatMode.Agentic)
                },
                onTemporaricModeRequested = {
                    mainViewModel.onModeSelected(ChatMode.Temporaric)
                },
                onSettingsRequested = openSettings,
                onExitToMain = {
                    generalBackStackEntry.savedStateHandle
                        .remove<String>(KsenaxRoutes.Chat.BASIC_INITIAL_MESSAGE_STATE_KEY)
                    navController.popBackStack(
                        route = KsenaxRoutes.GENERAL,
                        inclusive = false,
                    )
                },
            )
        }

        /*
         * ╔═══════════════════════════════════════════════
         * ║  AGENTIC SCREEN ROUTE (AgenticViewModel)
         * ╚═══════════════════════════════════════════════
         */
        composable(
            route = KsenaxRoutes.Chat.AGENTIC_PATTERN,
            arguments = listOf(
                navArgument(KsenaxRoutes.Chat.CHAT_ID_ARGUMENT) {
                    type = NavType.LongType
                    defaultValue = KsenaxRoutes.Chat.NEW_CHAT_ID
                },
            ),
        ) { backStackEntry ->
            val application = rememberKsenaxApplication()
            val generalBackStackEntry = rememberGeneralBackStackEntry(navController)

            val mainViewModel: KsenaxMainViewModel = viewModel(
                viewModelStoreOwner = generalBackStackEntry,
            )
            val chatIdArgument = backStackEntry.arguments
                ?.getLong(KsenaxRoutes.Chat.CHAT_ID_ARGUMENT)
                ?: KsenaxRoutes.Chat.NEW_CHAT_ID
            val initialChatId = chatIdArgument
                .takeUnless { it == KsenaxRoutes.Chat.NEW_CHAT_ID }

            val savedState = generalBackStackEntry.savedStateHandle
            val agenticResponseModel = KsenaxSupportedTextModel.Gemma
            val agenticChatViewModel: KsenaxAgenticChatViewModel = viewModel(
                key = "agentic-work-$chatIdArgument",
                factory = KsenaxAgenticChatViewModel.Factory(
                    application = application,
                    initialChatId = initialChatId,
                    initialWorkspaceTreeUri = savedState.get<String>(
                        KsenaxRoutes.Chat.AGENTIC_WORKSPACE_URI_STATE_KEY,
                    ),
                    initialWorkspaceDisplayPath = savedState.get<String>(
                        KsenaxRoutes.Chat.AGENTIC_WORKSPACE_PATH_STATE_KEY,
                    ).orEmpty(),
                    // Новый agentic work pipeline: G4 планирует, FunctionGemma
                    // компилирует атомарные действия.
                    responseModel = agenticResponseModel,
                ),
            )
            val initialMessage = savedState.get<String>(
                KsenaxRoutes.Chat.AGENTIC_INITIAL_MESSAGE_STATE_KEY,
            )

            KsenaxAgenticChatScreen(
                viewModel = agenticChatViewModel,
                mainViewModel = mainViewModel,
                initialMessage = initialMessage,
                onInitialMessageCommitted = { committedMessage ->
                    mainViewModel.onAgenticMessageCommitted(committedMessage)
                    savedState.remove<String>(
                        KsenaxRoutes.Chat.AGENTIC_INITIAL_MESSAGE_STATE_KEY,
                    )
                },
                onBasicModeRequested = {
                    mainViewModel.onModeSelected(ChatMode.Basic)
                },
                onTemporaricModeRequested = {
                    mainViewModel.onModeSelected(ChatMode.Temporaric)
                },
                onBasicChatSelected = { chatId ->
                    navController.navigate(KsenaxRoutes.Chat.RouteBuilder.basic(chatId)) {
                        popUpTo(KsenaxRoutes.GENERAL)
                    }
                },
                onSettingsRequested = openSettings,
                onExitToMain = {
                    savedState.remove<String>(
                        KsenaxRoutes.Chat.AGENTIC_INITIAL_MESSAGE_STATE_KEY,
                    )
                    savedState.remove<String>(
                        KsenaxRoutes.Chat.AGENTIC_WORKSPACE_URI_STATE_KEY,
                    )
                    savedState.remove<String>(
                        KsenaxRoutes.Chat.AGENTIC_WORKSPACE_PATH_STATE_KEY,
                    )
                    navController.popBackStack(
                        route = KsenaxRoutes.GENERAL,
                        inclusive = false,
                    )
                },
            )
        }
    }
}
