package com.kolesnikovprod.ksetaorch.addons.registry

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalog
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogException
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSnapshot
import com.kolesnikovprod.ksetaorch.addons.catalog.CatalogRefreshPolicy
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonDiscovery
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonPackageIdentityVerifier
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonDiscoveryRejectionReason
import com.kolesnikovprod.ksetaorch.addons.discovery.AddonDiscoverySnapshot
import com.kolesnikovprod.ksetaorch.addons.discovery.RejectedAddonCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Обновляет catalog и discovery, после чего публикует единый registry state.
 *
 * Merge, trust, compatibility и capability policy изолированы в чистом
 * [AddonRegistrationResolver]. Этот класс отвечает только за orchestration,
 * кеш последнего snapshot и конкурентно-безопасное обновление StateFlow.
 *
 * @since 0.3
 */
class DefaultAddonRegistry(
    private val catalog: AddonCatalog,
    private val discovery: AddonDiscovery,
    private val identityVerifier: AddonPackageIdentityVerifier,
    private val hostEnvironmentProvider: () -> AddonHostEnvironment,
    private val currentTimeMillis: () -> Long =
        System::currentTimeMillis,
    private val computationDispatcher: CoroutineDispatcher =
        Dispatchers.Default,
    private val maximumTrustedCatalogCacheAgeMillis: Long,
) : AddonRegistry {

    init {
        require(maximumTrustedCatalogCacheAgeMillis > 0L) {
            "maximumTrustedCatalogCacheAgeMillis must be greater than zero"
        }
    }

    private val refreshMutex = Mutex()
    private val initializationMutex = Mutex()
    private val resolver = AddonRegistrationResolver()
    private val mutableState = MutableStateFlow(AddonRegistryState())

    override val state: StateFlow<AddonRegistryState> =
        mutableState.asStateFlow()

    private var lastCatalogSnapshot: AddonCatalogSnapshot? = null
    private var lastDiscoverySnapshot: AddonDiscoverySnapshot? = null

    override suspend fun refresh(
        mode: AddonRegistryRefreshMode,
    ): AddonRegistryState {
        return refreshMutex.withLock {
            mutableState.update { previous ->
                previous.copy(isRefreshing = true)
            }

            try {
                val (catalogResult, discoveryResult) = coroutineScope {
                    val catalogDeferred = async {
                        resultOfSuspend {
                            catalog.getSnapshot(mode.toCatalogPolicy())
                        }
                    }
                    val discoveryDeferred = async {
                        resultOfSuspend(discovery::discover)
                    }

                    catalogDeferred.await() to
                            discoveryDeferred.await()
                }

                catalogResult.onSuccess { snapshot ->
                    lastCatalogSnapshot = snapshot
                }
                discoveryResult.onSuccess { snapshot ->
                    lastDiscoverySnapshot = snapshot
                }

                val refreshedCatalogSnapshot = catalogResult.getOrNull()
                val catalogAuthorizationCurrent =
                    (refreshedCatalogSnapshot ?: lastCatalogSnapshot)
                        ?.isCurrentForAuthorization()
                        ?: false
                val catalogStatus = sourceStatus(
                    result = catalogResult,
                    hasPreviousValue = lastCatalogSnapshot != null,
                    successfulValueIsCurrent =
                        refreshedCatalogSnapshot
                            ?.isCurrentForAuthorization()
                            ?: false,
                )
                val discoveryStatus = sourceStatus(
                    result = discoveryResult,
                    hasPreviousValue = lastDiscoverySnapshot != null,
                    successfulValueIsCurrent = true,
                )
                val registeredAddons =
                    withContext(computationDispatcher) {
                        resolver.resolve(
                            catalogEntries =
                                lastCatalogSnapshot?.entries.orEmpty(),
                            discoveredAddons =
                                lastDiscoverySnapshot?.addons.orEmpty(),
                            catalogKnown = lastCatalogSnapshot != null,
                            discoveryKnown = lastDiscoverySnapshot != null,
                            authorizationSourcesCurrent =
                                catalogAuthorizationCurrent &&
                                    discoveryResult.isSuccess,
                            environment = hostEnvironmentProvider(),
                        )
                    }

                AddonRegistryState(
                    addons = registeredAddons,
                    discoveryIssues =
                        lastDiscoverySnapshot
                            ?.rejectedCandidates
                            ?.map { candidate ->
                                candidate.toRegistryIssue()
                            }
                            .orEmpty(),
                    isRefreshing = false,
                    catalogStatus = catalogStatus,
                    discoveryStatus = discoveryStatus,
                    refreshedAtEpochMillis = currentTimeMillis(),
                    authorizationValidUntilEpochMillis =
                        lastCatalogSnapshot
                            ?.takeIf {
                                catalogAuthorizationCurrent &&
                                    discoveryResult.isSuccess
                            }
                            ?.let { snapshot ->
                                snapshot.loadedAtEpochMillis +
                                    maximumTrustedCatalogCacheAgeMillis
                            },
                    isInitialized = true,
                ).also { newState ->
                    mutableState.value = newState
                }
            } finally {
                mutableState.update { current ->
                    if (current.isRefreshing) {
                        current.copy(isRefreshing = false)
                    } else {
                        current
                    }
                }
            }
        }
    }

    override suspend fun ensureInitialized(): AddonRegistryState {
        state.value.takeIf(AddonRegistryState::isInitialized)
            ?.let { current -> return current }

        return initializationMutex.withLock {
            val current = state.value
            if (current.isInitialized) {
                current
            } else {
                refresh(AddonRegistryRefreshMode.DEFAULT)
            }
        }
    }

    override fun authorizationState(): AddonRegistryState {
        val current = state.value
        if (!current.isInitialized) return current

        val validUntil = current.authorizationValidUntilEpochMillis
        if (validUntil == null || currentTimeMillis() > validUntil) {
            return current.withRevokedAuthorization(
                AddonManagementBlockReason.SOURCE_UNAVAILABLE,
            )
        }

        val verifiedAddons = current.addons.map { addon ->
            val installed = addon.installedMetadata
            if (
                addon.canBeManaged &&
                installed != null &&
                !identityVerifier.matches(
                    packageName = installed.packageName,
                    expectedUid = installed.uid,
                    expectedSigningCertificateSha256 =
                        installed.signingCertificateSha256,
                )
            ) {
                addon.copy(
                    grantedHostCapabilities = emptySet(),
                    managementBlockReason =
                        AddonManagementBlockReason.ADDON_NOT_TRUSTED,
                )
            } else {
                addon
            }
        }

        return if (verifiedAddons == current.addons) {
            current
        } else {
            current.copy(addons = verifiedAddons)
        }
    }

    private fun AddonRegistryRefreshMode.toCatalogPolicy():
            CatalogRefreshPolicy {
        return when (this) {
            AddonRegistryRefreshMode.DEFAULT ->
                CatalogRefreshPolicy.NETWORK_FIRST

            AddonRegistryRefreshMode.CACHE_ONLY ->
                CatalogRefreshPolicy.CACHE_ONLY

            AddonRegistryRefreshMode.FORCE_REMOTE ->
                CatalogRefreshPolicy.FORCE_NETWORK
        }
    }

    private fun RejectedAddonCandidate.toRegistryIssue():
            AddonRegistryDiscoveryIssue {
        val (kind, detail) = when (val rejection = reason) {
            AddonDiscoveryRejectionReason.MissingServiceInfo ->
                AddonRegistryDiscoveryIssueKind
                    .MISSING_SERVICE_INFO to null

            AddonDiscoveryRejectionReason.ServiceNotExported ->
                AddonRegistryDiscoveryIssueKind
                    .SERVICE_NOT_EXPORTED to null

            is AddonDiscoveryRejectionReason
                .InvalidManagementPermission ->
                AddonRegistryDiscoveryIssueKind
                    .INVALID_MANAGEMENT_PERMISSION to
                        rejection.actualPermission

            is AddonDiscoveryRejectionReason.MissingMetadata ->
                AddonRegistryDiscoveryIssueKind
                    .MISSING_METADATA to rejection.key

            is AddonDiscoveryRejectionReason.InvalidMetadata ->
                AddonRegistryDiscoveryIssueKind
                    .INVALID_METADATA to rejection.key

            AddonDiscoveryRejectionReason
                .SigningCertificateUnavailable ->
                AddonRegistryDiscoveryIssueKind
                    .SIGNING_CERTIFICATE_UNAVAILABLE to null

            is AddonDiscoveryRejectionReason
                .MultipleAddonServicesInPackage ->
                AddonRegistryDiscoveryIssueKind
                    .MULTIPLE_SERVICES_IN_PACKAGE to
                        rejection.packageName

            is AddonDiscoveryRejectionReason.DuplicateAddonId ->
                AddonRegistryDiscoveryIssueKind
                    .DUPLICATE_ADDON_ID to
                        rejection.addonId.value

            is AddonDiscoveryRejectionReason.UnexpectedFailure ->
                AddonRegistryDiscoveryIssueKind
                    .UNEXPECTED_FAILURE to rejection.message
        }

        return AddonRegistryDiscoveryIssue(
            packageName = packageName,
            serviceClassName = serviceClassName,
            kind = kind,
            detail = detail,
        )
    }

    private fun sourceStatus(
        result: Result<*>,
        hasPreviousValue: Boolean,
        successfulValueIsCurrent: Boolean,
    ): AddonRegistrySourceStatus {
        if (result.isSuccess && !successfulValueIsCurrent) {
            return AddonRegistrySourceStatus.Stale(
                errorMessage =
                    "Validated catalog cache exceeded trust TTL",
            )
        }

        return result.fold(
            onSuccess = { AddonRegistrySourceStatus.Fresh },
            onFailure = { error ->
                if (hasPreviousValue) {
                    AddonRegistrySourceStatus.Stale(
                        errorMessage = error.message,
                        failureKind = error.toRegistryFailureKind(),
                    )
                } else {
                    AddonRegistrySourceStatus.Unavailable(
                        errorMessage = error.message,
                        failureKind = error.toRegistryFailureKind(),
                    )
                }
            },
        )
    }

    private fun Throwable.toRegistryFailureKind():
            AddonRegistrySourceFailureKind {
        return when (this) {
            is AddonCatalogException.NetworkUnavailable ->
                AddonRegistrySourceFailureKind.NETWORK_UNAVAILABLE

            is AddonCatalogException.RemoteResponseRejected ->
                AddonRegistrySourceFailureKind.REMOTE_RESPONSE_REJECTED

            is AddonCatalogException.RemoteSourceNotConfigured ->
                AddonRegistrySourceFailureKind.REMOTE_NOT_CONFIGURED

            is AddonCatalogException.CacheUnavailable ->
                AddonRegistrySourceFailureKind.CACHE_UNAVAILABLE

            is AddonCatalogException.DecodeFailed,
            is AddonCatalogException.InvalidDocument,
            -> AddonRegistrySourceFailureKind.INVALID_DOCUMENT

            else -> AddonRegistrySourceFailureKind.UNKNOWN
        }
    }

    private fun AddonCatalogSnapshot.isCurrentForAuthorization(): Boolean {
        val now = currentTimeMillis()
        if (loadedAtEpochMillis <= 0L || loadedAtEpochMillis > now) {
            return false
        }

        val ageMillis = now - loadedAtEpochMillis
        return ageMillis in 0L..maximumTrustedCatalogCacheAgeMillis
    }

    private fun AddonRegistryState.withRevokedAuthorization(
        reason: AddonManagementBlockReason,
    ): AddonRegistryState {
        return copy(
            addons = addons.map { addon ->
                addon.copy(
                    grantedHostCapabilities = emptySet(),
                    managementBlockReason = reason,
                )
            },
        )
    }

    private suspend fun <T> resultOfSuspend(
        block: suspend () -> T,
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
