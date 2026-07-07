package com.kolesnikovprod.ksetaorch.clean

import android.content.Context
import android.os.Process
import androidx.core.content.edit
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxGemma4E2BInstallUseCase
import java.io.File
import kotlin.system.exitProcess

/**
 * Инфраструктурный класс уровня приложения, который чистит только `runtime-cache` директорию
 * модели, а не скачанную модель и не пользовательские данные.
 *
 * LiteRT-LM / локальная модель могут создавать runtime-мусор, который пока что не нужен
 * в глобальном смысле. Задача класса — не дать этому мусору накапливаться между сессиями,
 * особенно если процесс упал внезапно.
 *
 * Файлы с моделью и пользовательские файлы он не трогает!
 *
 * @property context контекст на уровне приложения, а не Activity
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxRuntimeCacheCleanupManager(
    context: Context,
    private val installUsecase: KsenaxGemma4E2BInstallUseCase =
        KsenaxGemma4E2BInstallUseCase(context.applicationContext),
) {
    private val appContext = context.applicationContext

    /**
     * Настройки, в которых лежит флаг о том, является ли сессия грязной или нет.
     *
     * **Если говорить языком ментальной модели**, то:
     * ```
     * dirty = true
     * ```
     * - означает, что предыдущая сессия могла умереть нечисто
     *
     * ```
     * dirty = false
     * ```
     * - предыдущая сессия завершилась, кэш успешно очищен
     *
     * @since 0.2
     */
    private val preferences = appContext.getSharedPreferences(
        CLEANUP_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * Вызывается один раз в [com.kolesnikovprod.ksetaorch.KsenaxAndroidApplication.onCreate].
     * Внутри происходит логика уровня:
     * 1. Проверка, была ли прошлая сессия грязной?
     * 2. Если была, то чистится кэш
     * 3. Текущую сессию помечает как грязную
     * (потому что Android-процесс может умереть в любой момент)
     *
     * @since 0.2
     */
    fun prepareProcessSession() {
        val previousSessionWasDirty = preferences.getBoolean(
            DIRTY_SESSION_KEY,
            false,
        )

        if (previousSessionWasDirty) {
            clearRuntimeCache(reason = "previous_process_did_not_close_cleanly")
        }

        markSessionDirty()
    }

    /**
     * Вызывается, когда приложение завершает работу "нормально".
     *
     * Чистится runtime-кэш: если успешно — пометка о том, что сессия чистая;
     * в противном случае сессия остаётся грязной.
     *
     * @since 0.2
     */
    fun finishProcessSessionAndClearCache() {
        val cleanupSucceeded = clearRuntimeCache(reason = "process_session_finished")

        if (cleanupSucceeded) {
            markSessionClean()
        }
    }

    /**
     * Installs a last-chance cleanup hook for Java/Kotlin crashes.
     *
     * This does not cover all native crashes, but it helps for ordinary uncaught
     * exceptions. Native hard-crashes are handled by the dirty-session cleanup on
     * the next application start.
     *
     * @since 0.2
     */
    fun installUncaughtExceptionCleanupHook() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        // Ловит все Java / Kotlin исключения, но не ловит нативные падения
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                finishProcessSessionAndClearCache()
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(UNCAUGHT_EXCEPTION_EXIT_CODE)
            }
        }
    }

    /**
     * Метод, который реально удаляет папку по следующему сценарию:
     *
     * 1. Получаем путь к `runtime-cache`
     * 2. Проверяем, что путь безопасный
     * 3. Если папка существует — удалять рекурсивно
     * 4. Создать папку заново
     *
     * @return была ли создана папка успешно
     * @since 0.2
     */
    private fun clearRuntimeCache(reason: String): Boolean {
        val runtimeCacheDirectory = File(installUsecase.getGemma4E2BCacheDirPath())

        if (!isSafeRuntimeCacheDirectory(runtimeCacheDirectory)) {
            return false
        }

        return runCatching {
            val beenDeleted = !runtimeCacheDirectory.exists() ||
                    runtimeCacheDirectory.deleteRecursively()

            if (!beenDeleted) return@runCatching false

            // LiteRT-LM expects this directory to exist or be creatable later.
            runtimeCacheDirectory.mkdirs() || runtimeCacheDirectory.isDirectory
        }.getOrDefault(false)
    }

    /**
     * Проверка:
     * - папка называется как runtime-cache?
     * - папка лежит внутри app-specific external files directory?
     *
     * @since 0.2
     */
    private fun isSafeRuntimeCacheDirectory(directory: File): Boolean {
        val canonicalDirectory = runCatching { directory.canonicalFile }
            .getOrElse { return false }

        val canonicalExternalFiles = runCatching {
            appContext.getExternalFilesDir(null)?.canonicalFile
        }.getOrNull() ?: return false

        return canonicalDirectory.name == KsenaxDownloadGateway.RUNTIME_CACHE_DIRECTORY_NAME &&
            canonicalDirectory.startsWith(canonicalExternalFiles)
    }

    private fun File.startsWith(parent: File): Boolean {
        var current: File? = this

        while (current != null) {
            if (current == parent) {
                return true
            }
            current = current.parentFile
        }

        return false
    }

    // Поставить клеймо грязноты с включенным коммитом, чтобы точно было записано значение.
    private fun markSessionDirty() {
        preferences.edit(commit = true) {
            putBoolean(DIRTY_SESSION_KEY, true)
        }
    }

    // Поставить клеймо очистки с включенным коммитом, чтобы точно было записано значение.
    private fun markSessionClean() {
        preferences.edit(commit = true) {
            putBoolean(DIRTY_SESSION_KEY, false)
        }
    }

    private companion object {
        const val CLEANUP_PREFERENCES_NAME = "ksenax_runtime_cache_cleanup"
        const val DIRTY_SESSION_KEY = "is_process_session_dirty"
        const val UNCAUGHT_EXCEPTION_EXIT_CODE = 10
    }
}
