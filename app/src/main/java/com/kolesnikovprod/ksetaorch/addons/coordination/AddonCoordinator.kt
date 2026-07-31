package com.kolesnikovprod.ksetaorch.addons.coordination

import android.content.Intent
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.management.AddonRuntimeStatus
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallProgress

/**
 * Command plane addon-системы.
 *
 * @since 0.3
 */
internal interface AddonCoordinator {

    suspend fun requestInstall(
        addonId: AddonId,
        onProgress: (AddonInstallProgress) -> Unit = {},
    ): AddonActionResult

    suspend fun requestUninstall(
        addonId: AddonId,
    ): AddonActionResult

    suspend fun completeUninstall(
        request: AddonUninstallRequest,
    ): AddonActionResult

    suspend fun open(
        addonId: AddonId,
    ): AddonActionResult

    suspend fun setEnabled(
        addonId: AddonId,
        enabled: Boolean,
    ): AddonActionResult

    suspend fun getRuntimeStatus(
        addonId: AddonId,
    ): AddonActionResult
}

/**
 * Результат пользовательской команды над аддоном.
 *
 * @since 0.3
 */
internal sealed interface AddonActionResult {

    data object InstallationUiOpened :
        AddonActionResult

    data class UninstallationConfirmationRequired(
        val request: AddonUninstallRequest,
    ) : AddonActionResult

    data class AddonUninstalled(
        val addonId: AddonId,
        val packageName: String,
    ) : AddonActionResult

    data object AddonUiOpened :
        AddonActionResult

    data class RuntimeStatusReceived(
        val status: AddonRuntimeStatus,
    ) : AddonActionResult

    data class RuntimeStateChanged(
        val status: AddonRuntimeStatus,
    ) : AddonActionResult

    data class UnknownSourcesPermissionRequired(
        /**
         * Явный UI effect: presentation должен запустить этот Intent.
         *
         * @since 0.3
         */
        val settingsIntent: Intent,
    ) : AddonActionResult

    data class Failed(
        val reason: AddonActionFailure,
        val message: String? = null,
    ) : AddonActionResult
}

/**
 * Стабильная причина отказа coordination-команды.
 *
 * @since 0.3
 */
internal enum class AddonActionFailure {
    ADDON_NOT_FOUND,
    ADDON_NOT_INSTALLED,
    ADDON_NOT_TRUSTED,
    ADDON_INCOMPATIBLE,
    REGISTRY_SOURCE_UNAVAILABLE,
    CATALOG_ENTRY_MISSING,
    APK_FILE_MISSING,
    CONNECTION_FAILED,
    REMOTE_COMMAND_FAILED,
    UI_ENTRY_POINT_UNAVAILABLE,
    INSTALLER_FAILED,
    ADDON_STILL_INSTALLED,
    NETWORK_UNAVAILABLE,
    DOWNLOAD_FAILED,
    APK_VERIFICATION_FAILED,
    VERIFIED_APK_MISMATCH,
}
