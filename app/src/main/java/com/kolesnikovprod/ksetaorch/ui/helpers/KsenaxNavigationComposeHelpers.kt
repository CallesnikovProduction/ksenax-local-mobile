package com.kolesnikovprod.ksetaorch.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.ui.KsenaxRoutes
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel

/**
 * Преобразует route-аргумент в страницу настроек. Неизвестное или отсутствующее
 * имя ведёт на корневую страницу вместо ошибки навигации.
 *
 * @since 0.4
 */
internal fun resolveSettingsPage(name: String?): KsenaxSettingsPage {
    return KsenaxSettingsPage.entries
        .firstOrNull { page -> page.name == name }
        ?: KsenaxSettingsPage.Main
}

/**
 * Удаляет одноразовый prompt, предназначенный для запуска нового Basic-чата.
 *
 * @since 0.4
 */
internal fun SavedStateHandle.clearBasicChatLaunchState() {
    remove<String>(KsenaxRoutes.Chat.StateKey.BASIC_INITIAL_MESSAGE)
}

/**
 * Удаляет одноразовые prompt и workspace-параметры запуска Agentic-чата.
 *
 * @since 0.4
 */
internal fun SavedStateHandle.clearAgenticChatLaunchState() {
    remove<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_INITIAL_MESSAGE)
    remove<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_WORKSPACE_URI)
    remove<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_WORKSPACE_PATH)
}

/**
 * Возвращает process-level composition root из текущего Compose-контекста.
 *
 * Функция допустима только в navigation/composition boundary: presentation и
 * ViewModel не должны самостоятельно извлекать зависимости через [LocalContext].
 *
 * @since 0.2
 */
@Composable internal fun rememberKsenaxApplication(): KsenaxAndroidApplication {
    val context = LocalContext.current
    return remember(context) {
        context.applicationContext as KsenaxAndroidApplication
    }
}

/**
 * Возвращает back-stack entry главного destination, который владеет общей
 * [KsenaxMainViewModel] и одноразовым состоянием запуска дочерних экранов.
 *
 * [currentBackStackEntry] участвует в ключе [remember], чтобы ссылка была
 * пересчитана при смене destination.
 *
 * @since 0.4
 */
@Composable internal fun rememberMainBackStackEntry(
    navController: NavHostController,
    currentBackStackEntry: NavBackStackEntry,
): NavBackStackEntry {
    return remember(navController, currentBackStackEntry) {
        navController.getBackStackEntry(KsenaxRoutes.GENERAL)
    }
}
