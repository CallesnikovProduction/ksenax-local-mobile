package com.kolesnikovprod.ksetaorch.addons.registry

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogEntry
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCompatibilityRequirements
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonPresentationDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonReleaseDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonSecurityDescriptor
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId
import dev.openksenax.addons.contract.management.AddonManagementContract
import com.kolesnikovprod.ksetaorch.addons.discovery.DiscoveredAddon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonRegistrationResolverTest {

    private val addonId = AddonId("dev.openksenax.addon.test")
    private val modelGeneration =
        HostCapabilityId("model.text-generation")
    private val embeddings =
        HostCapabilityId("model.embeddings")
    private val fingerprint = "A".repeat(64)
    private val resolver = AddonRegistrationResolver()

    @Test
    fun matchingOfficialAddonGetsEndpointAndDeclaredCapability() {
        val addon = resolveOne(
            catalogEntry = catalogEntry(
                capabilities = setOf(modelGeneration),
            ),
            installedAddon = installedAddon(
                capabilities = setOf(modelGeneration),
            ),
            availableCapabilities =
                setOf(modelGeneration, embeddings),
        )

        assertEquals(AddonTrustState.TrustedOfficial, addon.trust)
        assertEquals(AddonCompatibility.Compatible, addon.compatibility)
        assertNull(addon.managementBlockReason)
        assertEquals(
            setOf(modelGeneration),
            addon.grantedHostCapabilities,
        )
        assertTrue(addon.canUseHostCapability(modelGeneration))
        assertFalse(addon.canUseHostCapability(embeddings))
        assertEquals(
            "dev.openksenax.addon.test",
            addon.managementEndpoint?.packageName,
        )
        assertEquals(10_001, addon.installedMetadata?.uid)
        assertEquals(
            AddonManagementContract.CURRENT_API_VERSION,
            addon.managementEndpoint?.managementApiVersion,
        )
        with(requireNotNull(addon.catalogMetadata?.installArtifact)) {
            assertEquals(1L, versionCode)
            assertEquals("https://example.invalid/addon.apk", apkUrl)
            assertEquals("B".repeat(64), apkSha256)
            assertEquals(42L, sizeBytes)
            assertEquals(fingerprint, signingCertificateSha256)
        }
    }

    @Test
    fun catalogAndManifestCapabilityMismatchFailsClosed() {
        val addon = resolveOne(
            catalogEntry = catalogEntry(
                capabilities =
                    setOf(modelGeneration, embeddings),
            ),
            installedAddon = installedAddon(
                capabilities = setOf(modelGeneration),
            ),
            availableCapabilities =
                setOf(modelGeneration, embeddings),
        )

        val compatibility =
            addon.compatibility as AddonCompatibility.Incompatible

        assertTrue(
            compatibility.reasons.any {
                it is AddonCompatibility.Reason
                    .CatalogCapabilitiesMismatch
            },
        )
        assertEquals(
            setOf(modelGeneration),
            addon.grantedHostCapabilities,
        )
        assertEquals(
            AddonManagementBlockReason.ADDON_INCOMPATIBLE,
            addon.managementBlockReason,
        )
        assertFalse(addon.canUseHostCapability(modelGeneration))
    }

    @Test
    fun unavailableCurrentSourceBlocksPreviouslyResolvedAddon() {
        val addon = resolveOne(
            catalogEntry = catalogEntry(),
            installedAddon = installedAddon(),
            authorizationSourcesCurrent = false,
        )

        assertEquals(AddonTrustState.TrustedOfficial, addon.trust)
        assertEquals(AddonCompatibility.Compatible, addon.compatibility)
        assertEquals(
            AddonManagementBlockReason.SOURCE_UNAVAILABLE,
            addon.managementBlockReason,
        )
        assertFalse(addon.canBeManaged)
    }

    @Test
    fun missingCatalogCannotBeReportedAsUnlisted() {
        val addon = resolver.resolve(
            catalogEntries = emptyList(),
            discoveredAddons = listOf(installedAddon()),
            catalogKnown = false,
            discoveryKnown = true,
            authorizationSourcesCurrent = false,
            environment = hostEnvironment(),
        ).single()

        assertEquals(AddonTrustState.CatalogUnavailable, addon.trust)
        assertEquals(
            AddonCompatibility.UnknownReason.CATALOG_UNAVAILABLE,
            (addon.compatibility as AddonCompatibility.Unknown).reason,
        )
        assertEquals(
            AddonManagementBlockReason.SOURCE_UNAVAILABLE,
            addon.managementBlockReason,
        )
    }

    @Test
    fun unsupportedManagementApiFailsClosedBeforeBinding() {
        val addon = resolveOne(
            catalogEntry = catalogEntry(),
            installedAddon = installedAddon(
                managementApiVersion =
                    AddonManagementContract.CURRENT_API_VERSION + 1,
            ),
        )

        val compatibility =
            addon.compatibility as AddonCompatibility.Incompatible
        assertTrue(
            compatibility.reasons.any {
                it is AddonCompatibility.Reason.UnsupportedManagementApi
            },
        )
        assertEquals(
            AddonManagementBlockReason.ADDON_INCOMPATIBLE,
            addon.managementBlockReason,
        )
        assertFalse(addon.canBeManaged)
    }

    private fun resolveOne(
        catalogEntry: AddonCatalogEntry,
        installedAddon: DiscoveredAddon,
        availableCapabilities: Set<HostCapabilityId> =
            setOf(modelGeneration),
        authorizationSourcesCurrent: Boolean = true,
    ): RegisteredAddon {
        return resolver.resolve(
            catalogEntries = listOf(catalogEntry),
            discoveredAddons = listOf(installedAddon),
            catalogKnown = true,
            discoveryKnown = true,
            authorizationSourcesCurrent =
                authorizationSourcesCurrent,
            environment = hostEnvironment(availableCapabilities),
        ).single()
    }

    private fun catalogEntry(
        capabilities: Set<HostCapabilityId> =
            setOf(modelGeneration),
    ) = AddonCatalogEntry(
        addonId = addonId,
        packageName = "dev.openksenax.addon.test",
        displayName = "Test addon",
        shortDescription = "Test",
        fullDescription = null,
        executionModel =
            AddonExecutionModel.AUTONOMOUS_APPLICATION,
        release = AddonReleaseDescriptor(
            versionCode = 1L,
            versionName = "1.0",
            apkUrl = "https://example.invalid/addon.apk",
            apkSha256 = "B".repeat(64),
            sizeBytes = 42L,
        ),
        compatibility = AddonCompatibilityRequirements(
            protocolVersion = 1,
            managementApiVersion =
                AddonManagementContract.CURRENT_API_VERSION,
            minimumHostApi = 1,
            minimumAndroidSdk = 26,
        ),
        requiredHostCapabilities = capabilities,
        presentation = AddonPresentationDescriptor(
            iconUrl = null,
            repositoryUrl = null,
        ),
        security = AddonSecurityDescriptor(
            official = true,
            signingCertificateSha256 = fingerprint,
        ),
    )

    private fun installedAddon(
        capabilities: Set<HostCapabilityId> =
            setOf(modelGeneration),
        managementApiVersion: Int =
            AddonManagementContract.CURRENT_API_VERSION,
    ) = DiscoveredAddon(
        addonId = addonId,
        uid = 10_001,
        packageName = "dev.openksenax.addon.test",
        serviceClassName = ".AddonService",
        versionCode = 1L,
        versionName = "1.0",
        protocolVersion = 1,
        managementApiVersion = managementApiVersion,
        minimumHostApi = 1,
        executionModel =
            AddonExecutionModel.AUTONOMOUS_APPLICATION,
        requiredHostCapabilities = capabilities,
        signingCertificateSha256 = setOf(fingerprint),
    )

    private fun hostEnvironment(
        capabilities: Set<HostCapabilityId> =
            setOf(modelGeneration),
    ) = AddonHostEnvironment(
        hostApiVersion = 1,
        supportedProtocolVersions = setOf(1),
        supportedManagementApiVersions =
            setOf(AddonManagementContract.CURRENT_API_VERSION),
        availableCapabilities = capabilities,
        supportedExecutionModels =
            setOf(AddonExecutionModel.AUTONOMOUS_APPLICATION),
        androidSdkInt = 35,
    )
}
