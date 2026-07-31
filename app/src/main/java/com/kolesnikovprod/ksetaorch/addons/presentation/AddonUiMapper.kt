package com.kolesnikovprod.ksetaorch.addons.presentation

import dev.openksenax.addons.contract.AddonId
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCompatibility
import com.kolesnikovprod.ksetaorch.addons.registry.AddonInstallationState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonTrustState
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon

/**
 * Единственное преобразование registry domain в текстовое UI-состояние.
 *
 * @since 0.3
 */
internal object AddonUiMapper {

    fun map(
        registryState: AddonRegistryState,
        selectedAddonId: AddonId?,
        pendingInstallConfirmationId: AddonId? = null,
        actionMessage: String?,
        installStates: Map<AddonId, AddonInstallUiState> = emptyMap(),
        bannerStates: Map<AddonId, AddonBannerUiState> = emptyMap(),
        checkingUpdateIds: Set<AddonId> = emptySet(),
        updateMessages: Map<AddonId, String> = emptyMap(),
        infoOverlay: AddonInfoUiModel? = null,
        hiddenAvailableAddonIds: Set<AddonId> = emptySet(),
    ): AddonCatalogUiState {
        return AddonCatalogUiState(
            cards = registryState.addons
                .filterNot { addon ->
                    !addon.isInstalled &&
                        addon.addonId in hiddenAvailableAddonIds
                }
                .map { addon ->
                    addon.toCard(
                        installState = installStates[addon.addonId]
                            ?: AddonInstallUiState.Idle,
                        isInstallConfirmationVisible =
                            addon.addonId ==
                                pendingInstallConfirmationId,
                        bannerState = bannerStates[addon.addonId],
                        isCheckingUpdate =
                            addon.addonId in checkingUpdateIds,
                        updateMessage = updateMessages[addon.addonId],
                    )
                },
            selectedAddonId = selectedAddonId,
            pendingInstallConfirmationId =
                pendingInstallConfirmationId,
            isRefreshing = registryState.isRefreshing,
            actionMessage = actionMessage,
            infoOverlay = infoOverlay,
        )
    }

    private fun RegisteredAddon.toCard(
        installState: AddonInstallUiState,
        isInstallConfirmationVisible: Boolean,
        bannerState: AddonBannerUiState?,
        isCheckingUpdate: Boolean,
        updateMessage: String?,
    ): AddonCardUiModel {
        val published = catalogMetadata
        val installed = installedMetadata
        val bannerArtifact = published?.bannerArtifact
        return AddonCardUiModel(
            addonId = addonId,
            packageName = published?.packageName
                ?: installed?.packageName
                ?: addonId.value,
            title = displayName,
            description = published?.shortDescription
                ?: "Установленный addon APK без доступного описания каталога.",
            versionLabel = when {
                installed?.versionName != null -> "v${installed.versionName}"
                published != null ->
                    "v${published.installArtifact.versionName}"
                else -> "версия неизвестна"
            },
            installationLabel = installation.toText(),
            trustLabel = trust.toText(),
            compatibilityLabel = compatibility.toText(),
            capabilityLabels = grantedHostCapabilities
                .map { capability -> capability.value }
                .sorted(),
            isInstalled = isInstalled,
            canOpen = canOpenUi,
            canInstall =
                catalogMetadata?.official == true &&
                    compatibility is AddonCompatibility.Compatible &&
                    (
                        installation is AddonInstallationState.NotInstalled ||
                            installation is
                            AddonInstallationState.UpdateAvailable
                    ) &&
                    installState is AddonInstallUiState.Idle,
            canManageRuntime = canBeManaged,
            canUninstall = isInstalled,
            installState = installState,
            isInstallConfirmationVisible =
                isInstallConfirmationVisible,
            isCheckingUpdate = isCheckingUpdate,
            updateMessage = updateMessage,
            banner = bannerState
                ?.takeIf { state ->
                    state.sha256 == bannerArtifact?.sha256
                }
                ?: bannerArtifact?.let { artifact ->
                    AddonBannerUiState.Loading(artifact.sha256)
                }
                ?: AddonBannerUiState.Unavailable(sha256 = null),
        )
    }

    private fun AddonInstallationState.toText(): String = when (this) {
        AddonInstallationState.Unknown -> "Установка не проверена"
        AddonInstallationState.NotInstalled -> "Не установлен"
        is AddonInstallationState.Installed -> "Установлен"
        is AddonInstallationState.UpdateAvailable ->
            "Доступно обновление ${availableVersionName}"
        is AddonInstallationState.InstalledNewerThanCatalog ->
            "Установлена dev/beta-версия"
    }

    private fun AddonTrustState.toText(): String = when (this) {
        AddonTrustState.NotInstalled -> "Trust не применяется"
        AddonTrustState.CatalogUnavailable -> "Trust не доказан: нет каталога"
        AddonTrustState.TrustedOfficial -> "Официальная подпись подтверждена"
        AddonTrustState.CataloguedButNotOfficial -> "Неофициальная запись"
        AddonTrustState.UnlistedInstalled -> "APK отсутствует в каталоге"
        is AddonTrustState.SignatureMismatch -> "Подпись APK не совпадает"
    }

    private fun AddonCompatibility.toText(): String = when (this) {
        AddonCompatibility.Compatible -> "Совместим с этим OKx"
        is AddonCompatibility.Unknown ->
            "Совместимость не доказана: ${reason.name.lowercase()}"
        is AddonCompatibility.Incompatible ->
            "Несовместим: ${reasons.size} проверок не пройдено"
    }

}
