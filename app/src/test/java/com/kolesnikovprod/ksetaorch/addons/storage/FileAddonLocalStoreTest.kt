package com.kolesnikovprod.ksetaorch.addons.storage

import com.kolesnikovprod.ksetaorch.addons.registry.AddonBannerArtifact
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCatalogMetadata
import com.kolesnikovprod.ksetaorch.addons.registry.AddonCompatibility
import com.kolesnikovprod.ksetaorch.addons.registry.AddonInstallArtifact
import com.kolesnikovprod.ksetaorch.addons.registry.AddonInstallationState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementBlockReason
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceFailureKind
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonTrustState
import com.kolesnikovprod.ksetaorch.addons.registry.InstalledAddonMetadata
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilities
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileAddonLocalStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun promotesArtifactsOnlyAfterRegistryConfirmsInstallation() =
        runBlocking {
            val layout = AddonFileLayout(
                temporaryFolder.newFolder("addons"),
            )
            val store = FileAddonLocalStore(
                layout = layout,
                currentTimeMillis = { 999L },
                ioDispatcher = Dispatchers.Unconfined,
            )
            val addon = installedAddon()
            layout.temporaryApk(
                addon.addonId,
                versionCode = 2L,
            ).writeOwnedBytes("apk")
            layout.temporaryBanner(
                addon.addonId,
                sha256 = SHA_256,
            ).writeOwnedBytes("banner")

            val records = store.reconcile(
                installedRegistryState(addon),
            )

            val record = records.single()
            assertEquals(123L, record.installedAtEpochMillis)
            assertEquals(456L, record.lastUpdatedAtEpochMillis)
            assertEquals(
                "Full description",
                record.fullDescription,
            )
            assertTrue(
                layout.installedApk(PACKAGE_NAME).isFile,
            )
            assertTrue(
                layout.installedBanner(
                    PACKAGE_NAME,
                    SHA_256,
                ).isFile,
            )
            assertFalse(
                layout.temporaryApk(
                    addon.addonId,
                    2L,
                ).exists(),
            )
            assertEquals(
                PACKAGE_NAME,
                store.read(PACKAGE_NAME)?.packageName,
            )
        }

    @Test
    fun freshPackageManagerSnapshotRemovesUninstalledDirectory() =
        runBlocking {
            val layout = AddonFileLayout(
                temporaryFolder.newFolder("addons"),
            )
            val store = FileAddonLocalStore(
                layout = layout,
                ioDispatcher = Dispatchers.Unconfined,
            )
            val addon = installedAddon()
            store.reconcile(installedRegistryState(addon))
            assertTrue(
                layout.packageDirectory(PACKAGE_NAME).isDirectory,
            )

            store.reconcile(
                AddonRegistryState(
                    addons = emptyList(),
                    discoveryStatus =
                        AddonRegistrySourceStatus.Fresh,
                    catalogStatus =
                        AddonRegistrySourceStatus.Fresh,
                    isInitialized = true,
                ),
            )

            assertFalse(
                layout.packageDirectory(PACKAGE_NAME).exists(),
            )
        }

    @Test
    fun offlineReconcilePreservesInstalledMetadataAndBanner() =
        runBlocking {
            val layout = AddonFileLayout(
                temporaryFolder.newFolder("addons"),
            )
            val store = FileAddonLocalStore(
                layout = layout,
                ioDispatcher = Dispatchers.Unconfined,
            )
            val onlineAddon = installedAddon()
            layout.temporaryBanner(
                onlineAddon.addonId,
                sha256 = SHA_256,
            ).writeOwnedBytes("banner")
            val onlineRecord = store.reconcile(
                installedRegistryState(onlineAddon),
            ).single()
            val nextBannerSha256 = "B".repeat(64)
            val updatedCatalogAddon = onlineAddon.copy(
                catalogMetadata = requireNotNull(
                    onlineAddon.catalogMetadata,
                ).copy(
                    bannerArtifact = AddonBannerArtifact(
                        url = "https://example.invalid/new-banner.webp",
                        sha256 = nextBannerSha256,
                    ),
                ),
            )
            val recordBeforeNewBannerDownload = store.reconcile(
                installedRegistryState(updatedCatalogAddon),
            ).single()
            assertEquals(
                SHA_256,
                recordBeforeNewBannerDownload.bannerSha256,
            )
            assertTrue(
                layout.installedBanner(PACKAGE_NAME, SHA_256).isFile,
            )
            assertFalse(
                layout.installedBanner(
                    PACKAGE_NAME,
                    nextBannerSha256,
                ).exists(),
            )
            val offlineAddon = onlineAddon.copy(
                catalogMetadata = null,
                compatibility = AddonCompatibility.Unknown(
                    AddonCompatibility.UnknownReason.CATALOG_UNAVAILABLE,
                ),
                trust = AddonTrustState.CatalogUnavailable,
                grantedHostCapabilities = emptySet(),
                managementBlockReason =
                    AddonManagementBlockReason.SOURCE_UNAVAILABLE,
            )

            val offlineRecord = store.reconcile(
                AddonRegistryState(
                    addons = listOf(offlineAddon),
                    discoveryStatus = AddonRegistrySourceStatus.Fresh,
                    catalogStatus = AddonRegistrySourceStatus.Unavailable(
                        errorMessage = "offline",
                        failureKind =
                            AddonRegistrySourceFailureKind
                                .NETWORK_UNAVAILABLE,
                    ),
                    isInitialized = true,
                ),
            ).single()

            assertEquals(onlineRecord.displayName, offlineRecord.displayName)
            assertEquals(
                onlineRecord.shortDescription,
                offlineRecord.shortDescription,
            )
            assertEquals(
                onlineRecord.fullDescription,
                offlineRecord.fullDescription,
            )
            assertEquals(
                onlineRecord.repositoryUrl,
                offlineRecord.repositoryUrl,
            )
            assertEquals(SHA_256, offlineRecord.bannerSha256)
            assertTrue(
                layout.installedBanner(PACKAGE_NAME, SHA_256).isFile,
            )
        }

    @Test
    fun explicitRemovalDeletesOnlyTargetAddonArtifacts() =
        runBlocking {
            val layout = AddonFileLayout(
                temporaryFolder.newFolder("addons"),
            )
            val store = FileAddonLocalStore(
                layout = layout,
                ioDispatcher = Dispatchers.Unconfined,
            )
            val addon = installedAddon()
            val otherAddonId = AddonId(
                "dev.openksenax.addon.other",
            )
            store.reconcile(installedRegistryState(addon))
            val targetApk = layout.temporaryApk(
                addon.addonId,
                versionCode = 3L,
            ).apply { writeOwnedBytes("target") }
            val targetBanner = layout.temporaryBanner(
                addon.addonId,
                sha256 = SHA_256,
            ).apply { writeOwnedBytes("target-banner") }
            val legacyTargetApk = layout.rootDirectory.resolve(
                "${addon.addonId.value}-2.apk",
            ).apply { writeOwnedBytes("legacy-target") }
            val otherApk = layout.temporaryApk(
                otherAddonId,
                versionCode = 1L,
            ).apply { writeOwnedBytes("other") }

            store.remove(
                addonId = addon.addonId,
                packageName = PACKAGE_NAME,
            )

            assertFalse(
                layout.packageDirectory(PACKAGE_NAME).exists(),
            )
            assertFalse(targetApk.exists())
            assertFalse(targetBanner.exists())
            assertFalse(legacyTargetApk.exists())
            assertTrue(otherApk.isFile)
        }

    private fun installedRegistryState(
        addon: RegisteredAddon,
    ): AddonRegistryState {
        return AddonRegistryState(
            addons = listOf(addon),
            discoveryStatus = AddonRegistrySourceStatus.Fresh,
            catalogStatus = AddonRegistrySourceStatus.Fresh,
            isInitialized = true,
        )
    }

    private fun installedAddon(): RegisteredAddon {
        val addonId = AddonId(
            "dev.openksenax.addon.no-radar",
        )
        return RegisteredAddon(
            addonId = addonId,
            catalogMetadata = AddonCatalogMetadata(
                packageName = PACKAGE_NAME,
                displayName = "NO RADAR",
                shortDescription = "Short description",
                fullDescription = "Full description",
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
                    versionCode = 2L,
                    versionName = "1.0.1",
                    apkUrl = "https://example.invalid/addon.apk",
                    apkSha256 = SHA_256,
                    sizeBytes = 3L,
                    signingCertificateSha256 = SHA_256,
                ),
                iconUrl = null,
                repositoryUrl =
                    "https://example.invalid/repository",
                bannerArtifact = AddonBannerArtifact(
                    url =
                        "https://example.invalid/banner.webp",
                    sha256 = SHA_256,
                ),
            ),
            installedMetadata = InstalledAddonMetadata(
                uid = 10_001,
                packageName = PACKAGE_NAME,
                versionCode = 2L,
                versionName = "1.0.1",
                managementApiVersion = 1,
                signingCertificateSha256 = setOf(SHA_256),
                firstInstallTimeEpochMillis = 123L,
                lastUpdateTimeEpochMillis = 456L,
            ),
            managementEndpoint = null,
            installation = AddonInstallationState.Installed(
                versionCode = 2L,
                versionName = "1.0.1",
            ),
            compatibility = AddonCompatibility.Compatible,
            trust = AddonTrustState.TrustedOfficial,
            grantedHostCapabilities =
                setOf(HostCapabilities.TextGeneration),
            managementBlockReason = null,
        )
    }

    private fun File.writeOwnedBytes(value: String) {
        parentFile?.mkdirs()
        writeBytes(value.toByteArray())
    }

    private companion object {
        const val PACKAGE_NAME =
            "com.callesnikovprod.noradar"
        val SHA_256 = "A".repeat(64)
    }
}
