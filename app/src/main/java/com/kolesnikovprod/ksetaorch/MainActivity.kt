package com.kolesnikovprod.ksetaorch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.kolesnikovprod.ksetaorch.download.KsenaxModelInstallService
import com.kolesnikovprod.ksetaorch.ui.KsenaxHomeScreen

/**
 * Стандартная Android-точка входа, которая запускает Compose-экран приложения.
 *
 * Activity не содержит низкоуровневую установку модели напрямую: установка вынесена
 * в [KsenaxModelInstallService], а визуальные элементы экрана собраны из composable
 * компонентов `ui.generalscreen`.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class MainActivity : ComponentActivity() {

    /**
     * Инициализирует edge-to-edge режим и подключает главный Compose-экран.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                KsenaxHomeScreen()
            }
        }
    }
}
