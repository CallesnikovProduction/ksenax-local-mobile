package com.kolesnikovprod.ksetaorch.addons.coordination

import com.kolesnikovprod.ksetaorch.addons.download.AddonArtifactPreparer
import com.kolesnikovprod.ksetaorch.addons.download.VerifiedAddonApk
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementEndpoint
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistry
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryRefreshMode
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceFailureKind
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import dev.openksenax.addons.contract.AddonId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAddonCoordinatorTest {

    @Test
    fun mapsOfflineRegistryToNetworkUnavailableInstallFailure() =
        runBlocking {
            val coordinator = coordinatorFor(
                AddonRegistrySourceStatus.Stale(
                    errorMessage = "offline",
                    failureKind =
                        AddonRegistrySourceFailureKind.NETWORK_UNAVAILABLE,
                ),
            )

            val result = coordinator.requestInstall(
                AddonId("dev.openksenax.addon.example"),
            )

            assertEquals(
                AddonActionFailure.NETWORK_UNAVAILABLE,
                (result as AddonActionResult.Failed).reason,
            )
        }

    @Test
    fun doesNotMislabelRejectedRegistryResponseAsOffline() =
        runBlocking {
            val coordinator = coordinatorFor(
                AddonRegistrySourceStatus.Stale(
                    errorMessage = "HTTP 404",
                    failureKind =
                        AddonRegistrySourceFailureKind
                            .REMOTE_RESPONSE_REJECTED,
                ),
            )

            val result = coordinator.requestInstall(
                AddonId("dev.openksenax.addon.example"),
            )

            assertEquals(
                AddonActionFailure.REGISTRY_SOURCE_UNAVAILABLE,
                (result as AddonActionResult.Failed).reason,
            )
        }

    private fun coordinatorFor(
        catalogStatus: AddonRegistrySourceStatus,
    ): DefaultAddonCoordinator {
        val registry = FakeRegistry(
            AddonRegistryState(
                catalogStatus = catalogStatus,
                discoveryStatus = AddonRegistrySourceStatus.Fresh,
                isInitialized = true,
            ),
        )
        return DefaultAddonCoordinator(
            registry = registry,
            serviceConnector = object : AddonServiceConnector {
                override suspend fun connect(
                    endpoint: AddonManagementEndpoint,
                ): AddonServiceSession = error("Must not be called")
            },
            artifactPreparer = AddonArtifactPreparer { _, _ ->
                error("Must not be called")
            },
            installer = object : AddonInstaller {
                override suspend fun requestInstall(
                    verifiedApk: VerifiedAddonApk,
                ): AddonInstallerResult = error("Must not be called")
            },
            packageUninstaller = object : AddonPackageUninstaller {
                override fun createRequest(
                    addonId: AddonId,
                    packageName: String,
                ): AddonUninstallRequest = error("Must not be called")

                override suspend fun isPackageInstalled(
                    packageName: String,
                ): Boolean = error("Must not be called")
            },
            uiLauncher = object : AddonUiLauncher {
                override suspend fun launch(
                    packageName: String,
                ): AddonUiLaunchResult = error("Must not be called")
            },
        )
    }

    private class FakeRegistry(
        initialState: AddonRegistryState,
    ) : AddonRegistry {

        private val mutableState = MutableStateFlow(initialState)

        override val state: StateFlow<AddonRegistryState> = mutableState

        override suspend fun refresh(
            mode: AddonRegistryRefreshMode,
        ): AddonRegistryState = state.value

        override suspend fun ensureInitialized(): AddonRegistryState =
            state.value

        override fun authorizationState(): AddonRegistryState =
            state.value
    }
}
