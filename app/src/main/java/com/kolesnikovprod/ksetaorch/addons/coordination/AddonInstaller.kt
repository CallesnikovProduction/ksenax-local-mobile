package com.kolesnikovprod.ksetaorch.addons.coordination

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.kolesnikovprod.ksetaorch.addons.download.VerifiedAddonApk
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Передаёт уже подготовленный APK системному Android installer.
 *
 * Этот порт не скачивает файл и не вычисляет trust. Он принимает только
 * непрозрачный [VerifiedAddonApk], созданный download/verifier-контуром.
 *
 * @since 0.3
 */
internal interface AddonInstaller {

    suspend fun requestInstall(
        verifiedApk: VerifiedAddonApk,
    ): AddonInstallerResult
}

/**
 * Результат передачи команды Android package installer.
 *
 * @since 0.3
 */
internal sealed interface AddonInstallerResult {

    data object SystemUiOpened : AddonInstallerResult

    data object ApkFileUnavailable : AddonInstallerResult

    data class UnknownSourcesPermissionRequired(
        val settingsIntent: Intent,
    ) : AddonInstallerResult

    data class Failed(
        val message: String?,
    ) : AddonInstallerResult
}

/**
 * Android-реализация installer-порта через FileProvider и system UI.
 *
 * Все операции чтения [File] и построения FileProvider URI выполняются на
 * IO dispatcher. Activity запускается после возвращения в контекст caller.
 *
 * @since 0.3
 */
internal class AndroidAddonInstaller(
    context: Context,
    private val fileProviderAuthority: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonInstaller {

    private val applicationContext = context.applicationContext

    override suspend fun requestInstall(
        verifiedApk: VerifiedAddonApk,
    ): AddonInstallerResult {
        val apkUri = try {
            withContext(ioDispatcher) {
                val apkFile = verifiedApk.file
                if (!apkFile.exists() || !apkFile.isFile) {
                    return@withContext null
                }

                FileProvider.getUriForFile(
                    applicationContext,
                    fileProviderAuthority,
                    apkFile,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            return AddonInstallerResult.Failed(error.message)
        } ?: return AddonInstallerResult.ApkFileUnavailable

        if (!applicationContext.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${applicationContext.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            return AddonInstallerResult
                .UnknownSourcesPermissionRequired(settingsIntent)
        }

        return launchSystemUi(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive",
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun launchSystemUi(
        intent: Intent,
    ): AddonInstallerResult {
        return try {
            applicationContext.startActivity(intent)
            AddonInstallerResult.SystemUiOpened
        } catch (error: Exception) {
            AddonInstallerResult.Failed(error.message)
        }
    }
}
