package com.kolesnikovprod.ksetaorch.addons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalog
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogConfiguration
import com.kolesnikovprod.ksetaorch.addons.catalog.DefaultAddonCatalog
import com.kolesnikovprod.ksetaorch.addons.catalog.cache.FileAddonCatalogCache
import com.kolesnikovprod.ksetaorch.addons.catalog.mapping.DefaultAddonCatalogMapper
import com.kolesnikovprod.ksetaorch.addons.catalog.remote.KtorAddonCatalogRemoteSource
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDecoder
import com.kolesnikovprod.ksetaorch.addons.catalog.validation.DefaultAddonCatalogValidator
import com.kolesnikovprod.ksetaorch.addons.banner.AddonBannerRepository
import com.kolesnikovprod.ksetaorch.addons.banner.KtorVerifiedAddonBannerRepository
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonProtocol
import dev.openksenax.addons.contract.management.AddonManagementContract
import com.kolesnikovprod.ksetaorch.addons.coordination.AddonCoordinator
import com.kolesnikovprod.ksetaorch.addons.coordination.AndroidAddonInstaller
import com.kolesnikovprod.ksetaorch.addons.coordination.AndroidAddonPackageUninstaller
import com.kolesnikovprod.ksetaorch.addons.coordination.AndroidAddonServiceConnector
import com.kolesnikovprod.ksetaorch.addons.coordination.AndroidAddonUiLauncher
import com.kolesnikovprod.ksetaorch.addons.coordination.DefaultAddonCoordinator
import com.kolesnikovprod.ksetaorch.addons.discovery.PackageManagerAddonDiscovery
import com.kolesnikovprod.ksetaorch.addons.discovery.PackageManagerAddonIdentityVerifier
import com.kolesnikovprod.ksetaorch.addons.download.AndroidAddonApkVerifier
import com.kolesnikovprod.ksetaorch.addons.download.DefaultAddonArtifactPreparer
import com.kolesnikovprod.ksetaorch.addons.download.KtorAddonApkDownloader
import com.kolesnikovprod.ksetaorch.addons.modelprovider.KsenaxModelGenerationEngine
import com.kolesnikovprod.ksetaorch.addons.modelprovider.ModelGenerationEngine
import com.kolesnikovprod.ksetaorch.addons.registry.AddonHostEnvironment
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistry
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryRefreshMode
import com.kolesnikovprod.ksetaorch.addons.registry.DefaultAddonRegistry
import com.kolesnikovprod.ksetaorch.addons.remote.KtorAddonRemoteFileDownloader
import com.kolesnikovprod.ksetaorch.addons.storage.AddonFileLayout
import com.kolesnikovprod.ksetaorch.addons.storage.AddonLocalStore
import com.kolesnikovprod.ksetaorch.addons.storage.FileAddonLocalStore
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Единственный дочерний dependency graph addon-платформы.
 *
 * Он принадлежит `KsenaxAndroidApplication`, создаёт один Ktor client и
 * соединяет независимые contours без глобального mutable singleton state.
 *
 * @since 0.3
 */
internal class OpenKsenaxAddonGraph(
    context: Context,
    modelSession: KsenaxModelSession,
    isTextGenerationReady: () -> Boolean,
    configuration: AddonCatalogConfiguration =
        AddonCatalogConfiguration.offline(),
) {

    private val applicationContext = context.applicationContext
    private val started = AtomicBoolean(false)
    private val processScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default,
    )

    private val httpClient = HttpClient(Android) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 20_000L
        }
    }

    private val remoteFileDownloader =
        KtorAddonRemoteFileDownloader(
            httpClient = httpClient,
            allowedHosts = setOf(
                "github.com",
                "raw.githubusercontent.com",
                "objects.githubusercontent.com",
                "release-assets.githubusercontent.com",
                "github-releases.githubusercontent.com",
            ),
        )

    private val fileLayout = AddonFileLayout(
        rootDirectory = applicationContext.filesDir.resolve("addons"),
    )

    internal val localStore: AddonLocalStore =
        FileAddonLocalStore(fileLayout)

    internal val bannerRepository: AddonBannerRepository =
        KtorVerifiedAddonBannerRepository(
            fileDownloader = remoteFileDownloader,
            fileLayout = fileLayout,
        )

    internal val modelGenerationEngine: ModelGenerationEngine =
        KsenaxModelGenerationEngine(
            modelSession = modelSession,
            modelId = "openksenax-default-text-model",
            isTextGenerationReady = isTextGenerationReady,
        )

    private val catalog: AddonCatalog = DefaultAddonCatalog(
        remoteSource = configuration.registryUrl?.let { registryUrl ->
            KtorAddonCatalogRemoteSource(
                httpClient = httpClient,
                registryUrl = registryUrl,
            )
        },
        cache = FileAddonCatalogCache(
            context = applicationContext,
            fileName = configuration.cacheFileName,
        ),
        decoder = AddonCatalogDecoder(),
        validator = DefaultAddonCatalogValidator(
            supportedSchemaVersion = configuration.supportedSchemaVersion,
            expectedChannel = configuration.expectedChannel,
        ),
        mapper = DefaultAddonCatalogMapper(),
    )

    internal val registry: AddonRegistry = DefaultAddonRegistry(
        catalog = catalog,
        discovery = PackageManagerAddonDiscovery(
            packageManager = applicationContext.packageManager,
        ),
        identityVerifier = PackageManagerAddonIdentityVerifier(
            packageManager = applicationContext.packageManager,
        ),
        hostEnvironmentProvider = {
            AddonHostEnvironment(
                hostApiVersion = AddonProtocol.CURRENT_HOST_API,
                supportedProtocolVersions =
                    setOf(AddonProtocol.CURRENT_PROTOCOL_VERSION),
                supportedManagementApiVersions =
                    setOf(AddonManagementContract.CURRENT_API_VERSION),
                availableCapabilities =
                    modelGenerationEngine.supportedCapabilities,
                supportedExecutionModels =
                    setOf(AddonExecutionModel.AUTONOMOUS_APPLICATION),
                androidSdkInt = Build.VERSION.SDK_INT,
            )
        },
        maximumTrustedCatalogCacheAgeMillis =
            configuration.maximumTrustedCacheAgeMillis,
    )

    internal val coordinator: AddonCoordinator = DefaultAddonCoordinator(
        registry = registry,
        serviceConnector = AndroidAddonServiceConnector(applicationContext),
        artifactPreparer = DefaultAddonArtifactPreparer(
            downloader = KtorAddonApkDownloader(
                fileDownloader = remoteFileDownloader,
                fileLayout = fileLayout,
            ),
            verifier = AndroidAddonApkVerifier(
                packageManager = applicationContext.packageManager,
            ),
        ),
        installer = AndroidAddonInstaller(
            context = applicationContext,
            fileProviderAuthority =
                "${applicationContext.packageName}.addon.files",
        ),
        packageUninstaller =
            AndroidAddonPackageUninstaller(applicationContext),
        uiLauncher = AndroidAddonUiLauncher(applicationContext),
    )

    /**
     * Запускает первую catalog/discovery попытку до обращения UI или Binder.
     *
     * @since 0.3
     */
    internal fun start() {
        if (!started.compareAndSet(false, true)) return

        registerPackageChangeReceiver()

        processScope.launch {
            try {
                val state = registry.ensureInitialized()
                localStore.reconcile(state)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(
                    LOG_TAG,
                    "Initial addon registry refresh failed",
                    error,
                )
            }
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(
                    Intent.EXTRA_REPLACING,
                    false,
                )
            ) {
                return
            }
            val pendingResult = goAsync()
            processScope.launch {
                try {
                    val state = registry.refresh(
                        AddonRegistryRefreshMode.CACHE_ONLY,
                    )
                    localStore.reconcile(state)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.e(
                        LOG_TAG,
                        "Addon registry refresh after package change failed",
                        error,
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.registerReceiver(
                packageChangeReceiver,
                filter,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.registerReceiver(
                packageChangeReceiver,
                filter,
            )
        }
    }

    private companion object {
        const val LOG_TAG = "OpenKsenaxAddons"
    }
}
