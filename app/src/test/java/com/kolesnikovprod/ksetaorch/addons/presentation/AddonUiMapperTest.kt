package com.kolesnikovprod.ksetaorch.addons.presentation

import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.HostCapabilities
import dev.openksenax.addons.contract.management.AddonManagementContract
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCatalogMetadata
import com.kolesnikovprod.ksetaorch.addons.registry.AddonBannerArtifact
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCompatibility
import com.kolesnikovprod.ksetaorch.addons.registry.AddonInstallArtifact
import com.kolesnikovprod.ksetaorch.addons.registry.AddonInstallationState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementEndpoint
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementBlockReason
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonTrustState
import com.kolesnikovprod.ksetaorch.addons.registry.InstalledAddonMetadata
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonUiMapperTest {

    @Test
    fun mapsRegistryVerdictWithoutRecomputingTrust() {
        val addonId = AddonId("dev.openksenax.addon.example")
        val registered = RegisteredAddon(
            addonId = addonId,
            catalogMetadata = AddonCatalogMetadata(
                packageName = "dev.openksenax.example",
                displayName = "Example",
                shortDescription = "Example addon",
                fullDescription = null,
                executionModel =
                    AddonExecutionModel.AUTONOMOUS_APPLICATION,
                protocolVersion = 1,
                managementApiVersion = 1,
                minimumHostApi = 1,
                minimumAndroidSdk = 24,
                requiredHostCapabilities =
                    setOf(HostCapabilities.TextGeneration),
                official = true,
                installArtifact = AddonInstallArtifact(
                    versionCode = 2,
                    versionName = "0.2",
                    apkUrl = "https://example.invalid/addon.apk",
                    apkSha256 = "A".repeat(64),
                    sizeBytes = 1L,
                    signingCertificateSha256 = "B".repeat(64),
                ),
                iconUrl = null,
                repositoryUrl = null,
                bannerArtifact = AddonBannerArtifact(
                    url = "https://example.invalid/banner.png",
                    sha256 = "C".repeat(64),
                ),
            ),
            installedMetadata = InstalledAddonMetadata(
                uid = 10_001,
                packageName = "dev.openksenax.example",
                versionCode = 1,
                versionName = "0.1",
                managementApiVersion =
                    AddonManagementContract.CURRENT_API_VERSION,
                signingCertificateSha256 = setOf("B".repeat(64)),
            ),
            managementEndpoint = AddonManagementEndpoint(
                uid = 10_001,
                packageName = "dev.openksenax.example",
                serviceClassName = "dev.openksenax.example.AddonService",
                managementApiVersion =
                    AddonManagementContract.CURRENT_API_VERSION,
            ),
            installation = AddonInstallationState.UpdateAvailable(
                installedVersionCode = 1,
                installedVersionName = "0.1",
                availableVersionCode = 2,
                availableVersionName = "0.2",
            ),
            compatibility = AddonCompatibility.Compatible,
            trust = AddonTrustState.TrustedOfficial,
            grantedHostCapabilities = setOf(HostCapabilities.TextGeneration),
            managementBlockReason = null,
        )

        val state = AddonUiMapper.map(
            registryState = AddonRegistryState(
                addons = listOf(registered),
                catalogStatus = AddonRegistrySourceStatus.Fresh,
                discoveryStatus = AddonRegistrySourceStatus.Fresh,
                isInitialized = true,
            ),
            selectedAddonId = addonId,
            actionMessage = null,
        )

        val card = state.selectedCard!!
        assertEquals("Example", card.title)
        assertEquals("dev.openksenax.example", card.packageName)
        assertTrue(card.canOpen)
        assertTrue(card.banner is AddonBannerUiState.Loading)
        assertTrue(card.installationLabel.contains("0.2"))
        assertEquals(
            listOf(HostCapabilities.TextGeneration.value),
            card.capabilityLabels,
        )

        val managementBlockedState = AddonUiMapper.map(
            registryState = AddonRegistryState(
                addons = listOf(
                    registered.copy(
                        managementEndpoint = null,
                        managementBlockReason =
                            AddonManagementBlockReason
                                .SOURCE_UNAVAILABLE,
                    ),
                ),
                catalogStatus = AddonRegistrySourceStatus.Stale(null),
                discoveryStatus = AddonRegistrySourceStatus.Fresh,
                isInitialized = true,
            ),
            selectedAddonId = addonId,
            actionMessage = null,
        )

        assertTrue(managementBlockedState.cards.single().canOpen)
        assertFalse(
            managementBlockedState.cards.single().canManageRuntime,
        )
    }

    @Test
    fun hidesRemovedAddonUntilExplicitCatalogRefresh() {
        val addonId = AddonId("dev.openksenax.addon.available")
        val available = RegisteredAddon(
            addonId = addonId,
            catalogMetadata = AddonCatalogMetadata(
                packageName = "dev.openksenax.available",
                displayName = "Available",
                shortDescription = "Available addon",
                fullDescription = null,
                executionModel =
                    AddonExecutionModel.AUTONOMOUS_APPLICATION,
                protocolVersion = 1,
                managementApiVersion = 1,
                minimumHostApi = 1,
                minimumAndroidSdk = 24,
                requiredHostCapabilities = emptySet(),
                official = true,
                installArtifact = AddonInstallArtifact(
                    versionCode = 1,
                    versionName = "0.1",
                    apkUrl = "https://example.invalid/addon.apk",
                    apkSha256 = "A".repeat(64),
                    sizeBytes = 1L,
                    signingCertificateSha256 = "B".repeat(64),
                ),
                iconUrl = null,
                repositoryUrl = null,
            ),
            installedMetadata = null,
            managementEndpoint = null,
            installation = AddonInstallationState.NotInstalled,
            compatibility = AddonCompatibility.Compatible,
            trust = AddonTrustState.NotInstalled,
            grantedHostCapabilities = emptySet(),
            managementBlockReason =
                AddonManagementBlockReason.ADDON_NOT_INSTALLED,
        )
        val registryState = AddonRegistryState(
            addons = listOf(available),
            catalogStatus = AddonRegistrySourceStatus.Fresh,
            discoveryStatus = AddonRegistrySourceStatus.Fresh,
            isInitialized = true,
        )

        val hidden = AddonUiMapper.map(
            registryState = registryState,
            selectedAddonId = null,
            actionMessage = null,
            hiddenAvailableAddonIds = setOf(addonId),
        )
        val visible = AddonUiMapper.map(
            registryState = registryState,
            selectedAddonId = null,
            actionMessage = null,
        )

        assertTrue(hidden.cards.isEmpty())
        assertEquals(listOf(addonId), visible.cards.map { it.addonId })
    }
}
