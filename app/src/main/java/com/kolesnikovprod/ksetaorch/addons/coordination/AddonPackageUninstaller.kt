package com.kolesnikovprod.ksetaorch.addons.coordination

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import dev.openksenax.addons.contract.AddonId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Одноразовый запрос системного удаления конкретного addon APK.
 *
 * Запрос хранит identity, доказанную registry до открытия Android UI.
 * Presentation не вычисляет package name и не обращается к PackageManager.
 *
 * @since 0.3
 */
internal data class AddonUninstallRequest(
    val addonId: AddonId,
    val packageName: String,
    val confirmationIntent: Intent,
) {
    init {
        require(packageName.isNotBlank())
    }
}

/**
 * Android-framework граница полного удаления установленного addon APK.
 *
 * Контракт отдельно создаёт пользовательский uninstall request и проверяет
 * физическое наличие package. Исчезновение карточки registry не считается
 * доказательством удаления APK.
 *
 * @since 0.3
 */
internal interface AddonPackageUninstaller {

    fun createRequest(
        addonId: AddonId,
        packageName: String,
    ): AddonUninstallRequest

    suspend fun isPackageInstalled(packageName: String): Boolean
}

/**
 * Запрашивает Android uninstaller и проверяет результат через PackageManager.
 *
 * OKx пока не является installer-of-record: APK устанавливает системный
 * package installer. Поэтому `PackageInstaller.uninstall()` недоступен хосту,
 * а совместимый пользовательский flow использует системную Activity с
 * обязательным возвращением результата.
 *
 * @since 0.3
 */
internal class AndroidAddonPackageUninstaller(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonPackageUninstaller {

    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    override fun createRequest(
        addonId: AddonId,
        packageName: String,
    ): AddonUninstallRequest {
        val intent = Intent(
            Intent.ACTION_UNINSTALL_PACKAGE,
            "package:$packageName".toUri(),
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)

        return AddonUninstallRequest(
            addonId = addonId,
            packageName = packageName,
            confirmationIntent = intent,
        )
    }

    override suspend fun isPackageInstalled(
        packageName: String,
    ): Boolean = withContext(ioDispatcher) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
