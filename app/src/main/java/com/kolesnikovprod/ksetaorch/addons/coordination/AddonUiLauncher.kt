package com.kolesnikovprod.ksetaorch.addons.coordination

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-framework граница запуска рабочего UI автономного addon APK.
 *
 * Реализация получает только package name из registry и не знает имени
 * Activity аддона. Отсутствие launcher Activity остаётся явным результатом:
 * coordinator тогда может запросить принадлежащий аддону `PendingIntent`.
 *
 * @since 0.3
 */
internal interface AddonUiLauncher {

    suspend fun launch(packageName: String): AddonUiLaunchResult
}

/**
 * Результат попытки открыть launcher Activity установленного APK.
 *
 * @since 0.3
 */
internal sealed interface AddonUiLaunchResult {

    data object Launched : AddonUiLaunchResult

    data object LauncherActivityUnavailable : AddonUiLaunchResult

    data class Failed(
        val message: String?,
    ) : AddonUiLaunchResult
}

/**
 * Открывает стандартную launcher Activity пакета без знания её class name.
 *
 * @since 0.3
 */
internal class AndroidAddonUiLauncher(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonUiLauncher {

    private val applicationContext = context.applicationContext

    override suspend fun launch(
        packageName: String,
    ): AddonUiLaunchResult = withContext(ioDispatcher) {
        try {
            val launchIntent = applicationContext.packageManager
                .getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                return@withContext AddonUiLaunchResult
                    .LauncherActivityUnavailable
            }

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            applicationContext.startActivity(launchIntent)
            AddonUiLaunchResult.Launched
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AddonUiLaunchResult.Failed(error.message)
        }
    }
}
