package com.kolesnikovprod.ksetaorch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

import com.kolesnikovprod.ksetaorch.ui.KsenaxAppRoute

const val CURRENT_APPLICATION_VERSION = 0.2f

/**
 * Стандартная Android-точка входа (entry-point Activity),
 * которая запускает Compose-экран приложения.
 *
 * Она не занимается бизнес-логикой, ничего не ставит и ничем не рулит.
 * Стартует специальный маршрутизатор по [androidx.lifecycle.ViewModel]
 *
 * @since 0.1
 * @author Stephan Kolesnikov
 */
class MainActivity : ComponentActivity() {

    /**
     * Метод, который вызывается при создании Activity.
     *
     * Когда Android создаёт MainActivity, то вызывается этот метод, включается
     * режим [enableEdgeToEdge], происходит установка Compose UI, а уже внутри него
     * запускается [KsenaxAppRoute].
     *
     * @since 0.1
     */
    override fun onCreate(savedInstanceState: Bundle?) {

        // Обязательный вызов родительской реализации, ведь без него
        // Android-lifecycle будет сломан
        super.onCreate(savedInstanceState)

        // Приложение может рисоваться под status/navigation-bar-ами.
        // Короче, правильно учитывает insets, чтобы не залезть под камеру.
        enableEdgeToEdge()

        // На смену старому XML приходит современный Compose UI.
        setContent {
            // TODO: сделать собственную тему, потому что пиксели != Material Theme
            MaterialTheme {

                // Запуск самого маршрутизатора по ViewModels.
                KsenaxAppRoute(CURRENT_APPLICATION_VERSION)
            }
        }
    }

    /**
     * Функция, которая вызывается в момент, когда Activity уничтожается.
     * Из способов уничтожения существуют:
     *
     * 1. Реальный пользовательский выход, в случае которого чистится runtime-cache
     * 2. Изменение конфигурации (смена темы, поворот экрана)
     *
     * @since 0.2
     */
    override fun onDestroy() {
        // из-за пункта 2 документации к функции тут стоит вот это:
        if (!isChangingConfigurations) {
            (application as? KsenaxAndroidApplication) // safe-cast
                ?.runtimeCacheCleanupManager
                ?.finishProcessSessionAndClearCache()
        }

        // Очищаем остальную хрень родительского окна
        super.onDestroy()
    }
}
