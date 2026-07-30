package com.kolesnikovprod.ksetaorch.addons.registry

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalog
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogEntry
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogException
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSnapshot
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSource
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonChannel
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCompatibilityRequirements
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonPresentationDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonReleaseDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonSecurityDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.CatalogRefreshPolicy
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilities
import dev.openksenax.addons.contract.management.AddonManagementContract
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonDiscovery
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonDiscoverySnapshot
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonPackageIdentityVerifier
import com.kolesnikovprod.ksetaorch.addons.discovery.DiscoveredAddon
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAddonRegistryTest {

    @Test
    fun cacheAtTrustBoundaryCanAuthorizeInstalledAddon() = runBlocking {
        val registry = createRegistry(
            cacheLoadedAt = 1L,
            currentTime = 1_001L,
            maximumCacheAge = 1_000L,
        )

        val state = registry.refresh()
        val addon = state.addons.single()

        assertEquals(AddonRegistrySourceStatus.Fresh, state.catalogStatus)
        assertNull(addon.managementBlockReason)
        assertTrue(addon.canUseHostCapability(HostCapabilities.TextGeneration))
    }

    @Test
    fun expiredCacheRemainsVisibleButCannotAuthorizeAddon() = runBlocking {
        val registry = createRegistry(
            cacheLoadedAt = 1L,
            currentTime = 1_002L,
            maximumCacheAge = 1_000L,
        )

        val state = registry.refresh()
        val addon = state.addons.single()

        assertTrue(state.catalogStatus is AddonRegistrySourceStatus.Stale)
        assertEquals(
            AddonManagementBlockReason.SOURCE_UNAVAILABLE,
            addon.managementBlockReason,
        )
        assertFalse(
            addon.canUseHostCapability(HostCapabilities.TextGeneration),
        )
    }

    @Test
    fun networkAuthorizationExpiresWithoutAnotherRefresh() = runBlocking {
        var currentTime = 1_000L
        val registry = createRegistry(
            cacheLoadedAt = 1_000L,
            currentTime = currentTime,
            maximumCacheAge = 1_000L,
            source = AddonCatalogSource.NETWORK,
            currentTimeProvider = { currentTime },
        )

        registry.refresh()
        assertNull(
            registry.authorizationState()
                .addons.single().managementBlockReason,
        )

        currentTime = 2_001L
        val expiredAddon = registry.authorizationState().addons.single()

        assertEquals(
            AddonManagementBlockReason.SOURCE_UNAVAILABLE,
            expiredAddon.managementBlockReason,
        )
        assertFalse(
            expiredAddon.canUseHostCapability(
                HostCapabilities.TextGeneration,
            ),
        )
    }

    @Test
    fun livePackageIdentityMismatchRevokesAuthorization() = runBlocking {
        val registry = createRegistry(
            cacheLoadedAt = 1L,
            currentTime = 1_001L,
            maximumCacheAge = 1_000L,
            identityMatches = false,
        )

        val refreshed = registry.refresh().addons.single()
        val authorized = registry.authorizationState().addons.single()

        assertNull(refreshed.managementBlockReason)
        assertEquals(
            AddonManagementBlockReason.ADDON_NOT_TRUSTED,
            authorized.managementBlockReason,
        )
        assertTrue(authorized.grantedHostCapabilities.isEmpty())
    }

    @Test
    fun failedRemoteRefreshKeepsTrustedInstalledAddonVisible() = runBlocking {
        val trustedSnapshot = catalogSnapshot(
            loadedAt = 1_000L,
            source = AddonCatalogSource.NETWORK,
        )
        var requestCount = 0
        var lastRefreshPolicy: CatalogRefreshPolicy? = null
        val catalog = object : AddonCatalog {
            override suspend fun getSnapshot(
                refreshPolicy: CatalogRefreshPolicy,
            ): AddonCatalogSnapshot {
                requestCount += 1
                lastRefreshPolicy = refreshPolicy
                if (requestCount == 1) return trustedSnapshot

                throw AddonCatalogException.NetworkUnavailable(
                    cause = IOException("offline"),
                )
            }
        }
        val registry = createRegistry(
            cacheLoadedAt = 1_000L,
            currentTime = 1_100L,
            maximumCacheAge = 1_000L,
            catalogOverride = catalog,
        )

        registry.refresh()
        val refreshed = registry.refresh(
            AddonRegistryRefreshMode.FORCE_REMOTE,
        )

        assertEquals(
            CatalogRefreshPolicy.FORCE_NETWORK,
            lastRefreshPolicy,
        )
        val catalogStatus =
            refreshed.catalogStatus as AddonRegistrySourceStatus.Stale
        assertEquals(
            AddonRegistrySourceFailureKind.NETWORK_UNAVAILABLE,
            catalogStatus.failureKind,
        )
        val addon = refreshed.addons.single()
        assertTrue(addon.isInstalled)
        assertNull(addon.managementBlockReason)
        assertTrue(
            addon.canUseHostCapability(
                HostCapabilities.TextGeneration,
            ),
        )
    }

    private fun createRegistry(
        cacheLoadedAt: Long,
        currentTime: Long,
        maximumCacheAge: Long,
        source: AddonCatalogSource = AddonCatalogSource.CACHE,
        identityMatches: Boolean = true,
        currentTimeProvider: () -> Long = { currentTime },
        catalogOverride: AddonCatalog? = null,
    ): DefaultAddonRegistry {
        val catalogSnapshot = catalogSnapshot(cacheLoadedAt, source)
        return DefaultAddonRegistry(
            catalog = catalogOverride ?: object : AddonCatalog {
                override suspend fun getSnapshot(
                    refreshPolicy: CatalogRefreshPolicy,
                ): AddonCatalogSnapshot = catalogSnapshot
            },
            discovery = object : AddonDiscovery {
                override suspend fun discover() = AddonDiscoverySnapshot(
                    addons = listOf(discoveredAddon()),
                    rejectedCandidates = emptyList(),
                    scannedAtEpochMillis = currentTime,
                )
            },
            identityVerifier = AddonPackageIdentityVerifier { _, _, _ ->
                identityMatches
            },
            hostEnvironmentProvider = {
                AddonHostEnvironment(
                    hostApiVersion = 1,
                    supportedProtocolVersions = setOf(1),
                    supportedManagementApiVersions = setOf(
                        AddonManagementContract.CURRENT_API_VERSION,
                    ),
                    availableCapabilities =
                        setOf(HostCapabilities.TextGeneration),
                    supportedExecutionModels = setOf(
                        AddonExecutionModel.AUTONOMOUS_APPLICATION,
                    ),
                    androidSdkInt = 35,
                )
            },
            currentTimeMillis = currentTimeProvider,
            computationDispatcher = Dispatchers.Unconfined,
            maximumTrustedCatalogCacheAgeMillis = maximumCacheAge,
        )
    }

    private fun catalogSnapshot(
        loadedAt: Long,
        source: AddonCatalogSource,
    ) = AddonCatalogSnapshot(
        schemaVersion = 1,
        channel = AddonChannel.STABLE,
        generatedAtEpochMillis = 1L,
        loadedAtEpochMillis = loadedAt,
        entries = listOf(catalogEntry()),
        source = source,
    )

    private fun catalogEntry() = AddonCatalogEntry(
        addonId = ADDON_ID,
        packageName = PACKAGE_NAME,
        displayName = "Test addon",
        shortDescription = "Test",
        fullDescription = null,
        executionModel = AddonExecutionModel.AUTONOMOUS_APPLICATION,
        release = AddonReleaseDescriptor(
            versionCode = 1L,
            versionName = "1.0",
            apkUrl = "https://example.invalid/addon.apk",
            apkSha256 = SHA_256,
            sizeBytes = 1L,
        ),
        compatibility = AddonCompatibilityRequirements(
            protocolVersion = 1,
            managementApiVersion =
                AddonManagementContract.CURRENT_API_VERSION,
            minimumHostApi = 1,
            minimumAndroidSdk = 28,
        ),
        requiredHostCapabilities =
            setOf(HostCapabilities.TextGeneration),
        presentation = AddonPresentationDescriptor(
            iconUrl = null,
            repositoryUrl = null,
        ),
        security = AddonSecurityDescriptor(
            official = true,
            signingCertificateSha256 = SHA_256,
        ),
    )

    private fun discoveredAddon() = DiscoveredAddon(
        addonId = ADDON_ID,
        uid = 10_001,
        packageName = PACKAGE_NAME,
        serviceClassName = "$PACKAGE_NAME.AddonService",
        versionCode = 1L,
        versionName = "1.0",
        protocolVersion = 1,
        managementApiVersion =
            AddonManagementContract.CURRENT_API_VERSION,
        minimumHostApi = 1,
        executionModel = AddonExecutionModel.AUTONOMOUS_APPLICATION,
        requiredHostCapabilities =
            setOf(HostCapabilities.TextGeneration),
        signingCertificateSha256 = setOf(SHA_256),
    )

    private companion object {
        val ADDON_ID = AddonId("dev.openksenax.addon.test")
        const val PACKAGE_NAME = "dev.openksenax.addon.test"
        const val SHA_256 =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
