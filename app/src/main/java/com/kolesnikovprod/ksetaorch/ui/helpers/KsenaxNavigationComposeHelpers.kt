package com.kolesnikovprod.ksetaorch.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication
import com.kolesnikovprod.ksetaorch.ui.KsenaxRoutes
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSupportedTextModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainViewModel

internal fun KsenaxMainViewModel.currentResponseModel(): KsenaxSupportedTextModel =
    uiState.selectedSupportedModel ?: KsenaxSupportedTextModel.Gemma

internal fun settingsStringPageNameToEnum(name: String?): KsenaxSettingsPage {
    return KsenaxSettingsPage.entries
        .firstOrNull { page -> page.name == name }
        ?: KsenaxSettingsPage.Main
}

/**
 * Довольно опасная функция для использования извне.
 *
 * @since 0.2
 */
@Composable internal fun rememberKsenaxApplication(): KsenaxAndroidApplication {
    val context = LocalContext.current
    return remember(context) {
        context.applicationContext as KsenaxAndroidApplication
    }
}

@Composable internal fun rememberGeneralBackStackEntry(
    navController: NavHostController
): NavBackStackEntry {
    return remember(navController) {
        navController.getBackStackEntry(KsenaxRoutes.GENERAL)
    }
}