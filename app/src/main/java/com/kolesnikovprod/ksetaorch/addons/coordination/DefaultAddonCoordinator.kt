package com.kolesnikovprod.ksetaorch.addons.coordination

import android.app.PendingIntent
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.management.AddonCommandStatus
import com.kolesnikovprod.ksetaorch.addons.download.AddonArtifactFailure
import com.kolesnikovprod.ksetaorch.addons.download.AddonArtifactPreparationResult
import com.kolesnikovprod.ksetaorch.addons.download.AddonArtifactPreparer
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallProgress
import com.kolesnikovprod.ksetaorch.addons.download.VerifiedAddonApk
import com.kolesnikovprod.ksetaorch.addons.download.normalizeSha256
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCompatibility
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementBlockReason
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementEndpoint
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistry
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryRefreshMode
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Выполняет пользовательские команды, используя готовые registry verdicts.
 *
 * Coordinator не импортирует source-модели catalog/discovery и не повторяет
 * trust, compatibility или capability policy.
 *
 * @since 0.3
 */
internal class DefaultAddonCoordinator(
    private val registry: AddonRegistry,
    private val serviceConnector: AddonServiceConnector,
    private val artifactPreparer: AddonArtifactPreparer,
    private val installer: AddonInstaller,
    private val packageUninstaller: AddonPackageUninstaller,
    private val uiLauncher: AddonUiLauncher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonCoordinator {

    override suspend fun requestInstall(
        addonId: AddonId,
        onProgress: (AddonInstallProgress) -> Unit,
    ): AddonActionResult {
        val registryState = registry.state.value
        if (
            registryState.catalogStatus !=
            AddonRegistrySourceStatus.Fresh
        ) {
            return failed(
                AddonActionFailure.REGISTRY_SOURCE_UNAVAILABLE,
                "A current registry snapshot is required for installation",
            )
        }
        val addon = registryState.addons
            .firstOrNull { it.addonId == addonId }
            ?: return failed(AddonActionFailure.ADDON_NOT_FOUND)

        val published = addon.catalogMetadata
        val artifact = published?.installArtifact
        if (artifact == null) {
            return failed(AddonActionFailure.CATALOG_ENTRY_MISSING)
        }
        if (!published.official) {
            return failed(
                AddonActionFailure.ADDON_NOT_TRUSTED,
                "Only official signed registry entries can be installed",
            )
        }
        if (addon.compatibility !is AddonCompatibility.Compatible) {
            return failed(AddonActionFailure.ADDON_INCOMPATIBLE)
        }

        val prepared = artifactPreparer.prepare(addon, onProgress)
        if (prepared is AddonArtifactPreparationResult.Failed) {
            return failed(
                reason = prepared.reason.toActionFailure(),
                message = prepared.message,
            )
        }
        val verifiedApk =
            (prepared as AddonArtifactPreparationResult.Ready).apk

        val artifactMatches =
            published.packageName == verifiedApk.packageName &&
            artifact.versionCode == verifiedApk.versionCode &&
                artifact.apkSha256.normalizeSha256() == verifiedApk.sha256 &&
                artifact.signingCertificateSha256.normalizeSha256() ==
                    verifiedApk.signingCertificateSha256 &&
                (artifact.sizeBytes == null ||
                    artifact.sizeBytes == verifiedApk.sizeBytes)
        if (!artifactMatches) {
            return failed(AddonActionFailure.VERIFIED_APK_MISMATCH)
        }

        return when (val result = installer.requestInstall(verifiedApk)) {
            AddonInstallerResult.SystemUiOpened ->
                AddonActionResult.InstallationUiOpened

            AddonInstallerResult.ApkFileUnavailable ->
                failed(AddonActionFailure.APK_FILE_MISSING)

            is AddonInstallerResult
            .UnknownSourcesPermissionRequired -> {
                AddonActionResult.UnknownSourcesPermissionRequired(
                    settingsIntent = result.settingsIntent,
                )
            }

            is AddonInstallerResult.Failed -> failed(
                reason = AddonActionFailure.INSTALLER_FAILED,
                message = result.message,
            )
        }
    }

    override suspend fun requestUninstall(
        addonId: AddonId,
    ): AddonActionResult {
        val addon = registry.find(addonId)
            ?: return failed(AddonActionFailure.ADDON_NOT_FOUND)
        val packageName = addon.installedMetadata?.packageName
            ?: return failed(AddonActionFailure.ADDON_NOT_INSTALLED)

        return try {
            AddonActionResult.UninstallationConfirmationRequired(
                packageUninstaller.createRequest(
                    addonId = addonId,
                    packageName = packageName,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            failed(
                AddonActionFailure.INSTALLER_FAILED,
                error.message,
            )
        }
    }

    override suspend fun completeUninstall(
        request: AddonUninstallRequest,
    ): AddonActionResult {
        try {
            repeat(UNINSTALL_VERIFICATION_ATTEMPTS) { attempt ->
                if (
                    !packageUninstaller.isPackageInstalled(
                        request.packageName,
                    )
                ) {
                    registry.refresh(
                        AddonRegistryRefreshMode.CACHE_ONLY,
                    )
                    return AddonActionResult.AddonUninstalled(
                        addonId = request.addonId,
                        packageName = request.packageName,
                    )
                }
                if (attempt < UNINSTALL_VERIFICATION_ATTEMPTS - 1) {
                    delay(UNINSTALL_VERIFICATION_DELAY_MILLIS)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return failed(
                AddonActionFailure.INSTALLER_FAILED,
                error.message,
            )
        }

        return failed(
            AddonActionFailure.ADDON_STILL_INSTALLED,
            "Android returned from uninstaller, but package " +
                "${request.packageName} is still installed",
        )
    }

    override suspend fun open(
        addonId: AddonId,
    ): AddonActionResult {
        val addon = withContext(ioDispatcher) {
            registry.authorizationState().addons
                .firstOrNull { candidate ->
                    candidate.addonId == addonId
                }
        } ?: return failed(AddonActionFailure.ADDON_NOT_FOUND)

        val packageName = addon.installedMetadata?.packageName
            ?: return failed(AddonActionFailure.ADDON_NOT_INSTALLED)

        val launcherFailure = when (
            val launchResult = uiLauncher.launch(packageName)
        ) {
            AddonUiLaunchResult.Launched ->
                return AddonActionResult.AddonUiOpened

            AddonUiLaunchResult.LauncherActivityUnavailable ->
                "Launcher Activity is unavailable"

            is AddonUiLaunchResult.Failed ->
                launchResult.message ?: "Launcher Activity failed"
        }

        val endpoint = addon.managementEndpoint
            ?.takeIf { addon.canBeManaged }
            ?: return failed(
                AddonActionFailure.UI_ENTRY_POINT_UNAVAILABLE,
                launcherFailure,
            )

        return executeWithSession(
            endpoint = endpoint,
            requestFailure =
                AddonActionFailure.UI_ENTRY_POINT_UNAVAILABLE,
        ) { session ->
            sendEntryPoint(
                entryPoint = session.requestUiEntryPoint(),
                launcherFailure = launcherFailure,
            )
        }
    }

    override suspend fun setEnabled(
        addonId: AddonId,
        enabled: Boolean,
    ): AddonActionResult {
        return executeManagementCommand(
            addonId = addonId,
            requestFailure = AddonActionFailure.REMOTE_COMMAND_FAILED,
        ) { session ->
            val response = session.requestSetEnabled(enabled)
            val commandResult = response.commandResult

            when (commandResult.status) {
                AddonCommandStatus.SUCCESS,
                AddonCommandStatus.ALREADY_IN_REQUESTED_STATE -> {
                    AddonActionResult.RuntimeStateChanged(
                        requireNotNull(response.runtimeStatus),
                    )
                }

                AddonCommandStatus.REJECTED,
                AddonCommandStatus.FAILED,
                AddonCommandStatus.UNKNOWN -> failed(
                    AddonActionFailure.REMOTE_COMMAND_FAILED,
                    commandResult.message,
                )
            }
        }
    }

    override suspend fun getRuntimeStatus(
        addonId: AddonId,
    ): AddonActionResult {
        return executeManagementCommand(
            addonId = addonId,
            requestFailure = AddonActionFailure.REMOTE_COMMAND_FAILED,
        ) { session ->
            AddonActionResult.RuntimeStatusReceived(
                session.requestRuntimeStatus(),
            )
        }
    }

    private suspend fun executeManagementCommand(
        addonId: AddonId,
        requestFailure: AddonActionFailure,
        block: suspend (AddonServiceSession) -> AddonActionResult,
    ): AddonActionResult {
        val addon = withContext(ioDispatcher) {
            registry.authorizationState().addons
                .firstOrNull { candidate ->
                    candidate.addonId == addonId
                }
        } ?: return failed(AddonActionFailure.ADDON_NOT_FOUND)

        val endpoint = addon.managementEndpoint
            ?.takeIf { addon.managementBlockReason == null }
            ?: return failed(
                addon.managementBlockReason.toActionFailure(),
            )

        return executeWithSession(
            endpoint = endpoint,
            requestFailure = requestFailure,
            block = block,
        )
    }

    private suspend fun executeWithSession(
        endpoint: AddonManagementEndpoint,
        requestFailure: AddonActionFailure,
        block: suspend (AddonServiceSession) -> AddonActionResult,
    ): AddonActionResult {
        return withContext(ioDispatcher) {
            try {
                serviceConnector.connect(endpoint).use { session ->
                    block(session)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: AddonManagementRequestException) {
                failed(
                    requestFailure,
                    error.message,
                )
            } catch (error: Throwable) {
                failed(
                    AddonActionFailure.CONNECTION_FAILED,
                    error.message,
                )
            }
        }
    }

    private fun sendEntryPoint(
        entryPoint: PendingIntent,
        launcherFailure: String,
    ): AddonActionResult {
        return try {
            entryPoint.send()
            AddonActionResult.AddonUiOpened
        } catch (error: PendingIntent.CanceledException) {
            failed(
                AddonActionFailure.UI_ENTRY_POINT_UNAVAILABLE,
                listOfNotNull(
                    launcherFailure,
                    error.message,
                ).joinToString(separator = "; "),
            )
        }
    }

    private fun AddonManagementBlockReason?.toActionFailure():
            AddonActionFailure {
        return when (this) {
            AddonManagementBlockReason.SOURCE_UNAVAILABLE ->
                AddonActionFailure.REGISTRY_SOURCE_UNAVAILABLE

            AddonManagementBlockReason.ADDON_NOT_INSTALLED ->
                AddonActionFailure.ADDON_NOT_INSTALLED

            AddonManagementBlockReason.ADDON_NOT_TRUSTED ->
                AddonActionFailure.ADDON_NOT_TRUSTED

            AddonManagementBlockReason.ADDON_INCOMPATIBLE ->
                AddonActionFailure.ADDON_INCOMPATIBLE

            null -> AddonActionFailure.CONNECTION_FAILED
        }
    }

    private fun failed(
        reason: AddonActionFailure,
        message: String? = null,
    ) = AddonActionResult.Failed(
        reason = reason,
        message = message,
    )

    private fun AddonArtifactFailure.toActionFailure():
            AddonActionFailure {
        return when (this) {
            AddonArtifactFailure.UNSAFE_DOWNLOAD_URL,
            AddonArtifactFailure.DOWNLOAD_FAILED ->
                AddonActionFailure.DOWNLOAD_FAILED

            AddonArtifactFailure.FILE_SIZE_MISMATCH,
            AddonArtifactFailure.SHA256_MISMATCH,
            AddonArtifactFailure.APK_UNREADABLE,
            AddonArtifactFailure.PACKAGE_MISMATCH,
            AddonArtifactFailure.VERSION_MISMATCH,
            AddonArtifactFailure.SIGNATURE_MISMATCH,
            AddonArtifactFailure.MANIFEST_MISMATCH ->
                AddonActionFailure.APK_VERIFICATION_FAILED
        }
    }

    private companion object {
        const val UNINSTALL_VERIFICATION_ATTEMPTS = 8
        const val UNINSTALL_VERIFICATION_DELAY_MILLIS = 250L
    }
}
