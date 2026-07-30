package com.kolesnikovprod.ksetaorch.addons.registry

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogEntry
import dev.openksenax.addons.contract.AddonId
import com.kolesnikovprod.ksetaorch.addons.discovery.DiscoveredAddon
import java.util.Locale

/**
 * Чистая policy-функция registry: объединяет source-модели и вычисляет
 * installation, trust, compatibility и capability verdict.
 *
 * Она намеренно internal: downstream-контуры получают только
 * [RegisteredAddon] и не могут повторно запускать policy-проверки.
 *
 * @since 0.3
 */
internal class AddonRegistrationResolver {

    fun resolve(
        catalogEntries: List<AddonCatalogEntry>,
        discoveredAddons: List<DiscoveredAddon>,
        catalogKnown: Boolean,
        discoveryKnown: Boolean,
        authorizationSourcesCurrent: Boolean,
        environment: AddonHostEnvironment,
    ): List<RegisteredAddon> {
        val catalogById = catalogEntries.associateBy(
            AddonCatalogEntry::addonId,
        )
        val discoveredById = discoveredAddons.associateBy(
            DiscoveredAddon::addonId,
        )
        val allIds: Set<AddonId> =
            catalogById.keys + discoveredById.keys

        return allIds
            .map { addonId ->
                resolveOne(
                    addonId = addonId,
                    catalogEntry = catalogById[addonId],
                    installedAddon = discoveredById[addonId],
                    catalogKnown = catalogKnown,
                    discoveryKnown = discoveryKnown,
                    authorizationSourcesCurrent =
                        authorizationSourcesCurrent,
                    environment = environment,
                )
            }
            .sortedBy(RegisteredAddon::displayName)
    }

    private fun resolveOne(
        addonId: AddonId,
        catalogEntry: AddonCatalogEntry?,
        installedAddon: DiscoveredAddon?,
        catalogKnown: Boolean,
        discoveryKnown: Boolean,
        authorizationSourcesCurrent: Boolean,
        environment: AddonHostEnvironment,
    ): RegisteredAddon {
        val installation = determineInstallation(
            catalogEntry = catalogEntry,
            installedAddon = installedAddon,
            discoveryKnown = discoveryKnown,
        )
        val trust = determineTrust(
            catalogEntry = catalogEntry,
            installedAddon = installedAddon,
            catalogKnown = catalogKnown,
        )
        val compatibility = determineCompatibility(
            catalogEntry = catalogEntry,
            installedAddon = installedAddon,
            catalogKnown = catalogKnown,
            environment = environment,
        )
        val endpoint = installedAddon?.let { addon ->
            AddonManagementEndpoint(
                uid = addon.uid,
                packageName = addon.packageName,
                serviceClassName = addon.serviceClassName,
                managementApiVersion = addon.managementApiVersion,
            )
        }
        val managementBlockReason =
            determineManagementBlockReason(
                installedAddon = installedAddon,
                authorizationSourcesCurrent =
                    authorizationSourcesCurrent,
                trust = trust,
                compatibility = compatibility,
            )

        return RegisteredAddon(
            addonId = addonId,
            catalogMetadata = catalogEntry?.toMetadata(),
            installedMetadata = installedAddon?.toMetadata(),
            managementEndpoint = endpoint,
            installation = installation,
            compatibility = compatibility,
            trust = trust,
            grantedHostCapabilities =
                determineGrantedCapabilities(
                    catalogEntry = catalogEntry,
                    installedAddon = installedAddon,
                    environment = environment,
                ),
            managementBlockReason = managementBlockReason,
        )
    }

    private fun determineInstallation(
        catalogEntry: AddonCatalogEntry?,
        installedAddon: DiscoveredAddon?,
        discoveryKnown: Boolean,
    ): AddonInstallationState {
        if (!discoveryKnown) {
            return AddonInstallationState.Unknown
        }
        if (installedAddon == null) {
            return AddonInstallationState.NotInstalled
        }
        if (catalogEntry == null) {
            return AddonInstallationState.Installed(
                versionCode = installedAddon.versionCode,
                versionName = installedAddon.versionName,
            )
        }

        val availableVersion = catalogEntry.release.versionCode
        return when {
            installedAddon.versionCode < availableVersion -> {
                AddonInstallationState.UpdateAvailable(
                    installedVersionCode = installedAddon.versionCode,
                    installedVersionName = installedAddon.versionName,
                    availableVersionCode = availableVersion,
                    availableVersionName =
                        catalogEntry.release.versionName,
                )
            }

            installedAddon.versionCode > availableVersion -> {
                AddonInstallationState.InstalledNewerThanCatalog(
                    installedVersionCode = installedAddon.versionCode,
                    installedVersionName = installedAddon.versionName,
                    catalogVersionCode = availableVersion,
                )
            }

            else -> AddonInstallationState.Installed(
                versionCode = installedAddon.versionCode,
                versionName = installedAddon.versionName,
            )
        }
    }

    private fun determineTrust(
        catalogEntry: AddonCatalogEntry?,
        installedAddon: DiscoveredAddon?,
        catalogKnown: Boolean,
    ): AddonTrustState {
        if (installedAddon == null) {
            return AddonTrustState.NotInstalled
        }
        if (!catalogKnown) {
            return AddonTrustState.CatalogUnavailable
        }
        if (catalogEntry == null) {
            return AddonTrustState.UnlistedInstalled
        }
        if (!catalogEntry.security.official) {
            return AddonTrustState.CataloguedButNotOfficial
        }

        val expected = normalizeFingerprint(
            catalogEntry.security.signingCertificateSha256,
        )
        val actual = installedAddon.signingCertificateSha256
            .map(::normalizeFingerprint)
            .toSet()

        return if (expected in actual) {
            AddonTrustState.TrustedOfficial
        } else {
            AddonTrustState.SignatureMismatch(
                expectedSha256 = expected,
                actualSha256 = actual,
            )
        }
    }

    private fun determineCompatibility(
        catalogEntry: AddonCatalogEntry?,
        installedAddon: DiscoveredAddon?,
        catalogKnown: Boolean,
        environment: AddonHostEnvironment,
    ): AddonCompatibility {
        if (!catalogKnown) {
            return AddonCompatibility.Unknown(
                AddonCompatibility.UnknownReason.CATALOG_UNAVAILABLE,
            )
        }
        if (catalogEntry == null) {
            return AddonCompatibility.Unknown(
                AddonCompatibility.UnknownReason.UNLISTED_ADDON,
            )
        }

        val reasons = linkedSetOf<AddonCompatibility.Reason>()
        val protocolVersions = buildSet {
            add(catalogEntry.compatibility.protocolVersion)
            installedAddon?.let { add(it.protocolVersion) }
        }
        protocolVersions.forEach { protocolVersion ->
            if (protocolVersion !in environment.supportedProtocolVersions) {
                reasons += AddonCompatibility.Reason.UnsupportedProtocol(
                    protocolVersion = protocolVersion,
                    supportedVersions =
                        environment.supportedProtocolVersions,
                )
            }
        }

        val managementApiVersions = buildSet {
            add(catalogEntry.compatibility.managementApiVersion)
            installedAddon?.let { add(it.managementApiVersion) }
        }
        managementApiVersions.forEach { managementApiVersion ->
            if (
                managementApiVersion !in
                environment.supportedManagementApiVersions
            ) {
                reasons +=
                    AddonCompatibility.Reason.UnsupportedManagementApi(
                        managementApiVersion = managementApiVersion,
                        supportedVersions =
                            environment.supportedManagementApiVersions,
                    )
            }
        }

        val requiredHostApi = maxOf(
            catalogEntry.compatibility.minimumHostApi,
            installedAddon?.minimumHostApi ?: 0,
        )
        if (requiredHostApi > environment.hostApiVersion) {
            reasons += AddonCompatibility.Reason.HostApiTooOld(
                currentHostApi = environment.hostApiVersion,
                requiredHostApi = requiredHostApi,
            )
        }
        if (
            catalogEntry.compatibility.minimumAndroidSdk >
            environment.androidSdkInt
        ) {
            reasons += AddonCompatibility.Reason.AndroidTooOld(
                currentSdk = environment.androidSdkInt,
                minimumSdk =
                    catalogEntry.compatibility.minimumAndroidSdk,
            )
        }

        val requiredCapabilities = buildSet {
            addAll(catalogEntry.requiredHostCapabilities)
            installedAddon?.let {
                addAll(it.requiredHostCapabilities)
            }
        }
        val missingCapabilities =
            requiredCapabilities - environment.availableCapabilities
        if (missingCapabilities.isNotEmpty()) {
            reasons += AddonCompatibility.Reason.MissingCapabilities(
                capabilities = missingCapabilities,
            )
        }

        val executionModels = buildSet {
            add(catalogEntry.executionModel)
            installedAddon?.let { add(it.executionModel) }
        }
        executionModels.forEach { executionModel ->
            if (executionModel !in environment.supportedExecutionModels) {
                reasons +=
                    AddonCompatibility.Reason.UnsupportedExecutionModel(
                        executionModel,
                    )
            }
        }

        installedAddon?.let { addon ->
            if (catalogEntry.packageName != addon.packageName) {
                reasons +=
                    AddonCompatibility.Reason.CatalogPackageMismatch(
                        catalogPackageName = catalogEntry.packageName,
                        installedPackageName = addon.packageName,
                    )
            }
            if (
                catalogEntry.compatibility.protocolVersion !=
                addon.protocolVersion
            ) {
                reasons +=
                    AddonCompatibility.Reason.CatalogProtocolMismatch(
                        catalogProtocolVersion =
                            catalogEntry.compatibility.protocolVersion,
                        installedProtocolVersion = addon.protocolVersion,
                    )
            }
            if (
                catalogEntry.compatibility.managementApiVersion !=
                addon.managementApiVersion
            ) {
                reasons +=
                    AddonCompatibility.Reason
                        .CatalogManagementApiMismatch(
                            catalogManagementApiVersion =
                                catalogEntry.compatibility
                                    .managementApiVersion,
                            installedManagementApiVersion =
                                addon.managementApiVersion,
                        )
            }
            if (catalogEntry.executionModel != addon.executionModel) {
                reasons +=
                    AddonCompatibility.Reason
                        .CatalogExecutionModelMismatch(
                            catalogExecutionModel =
                                catalogEntry.executionModel,
                            installedExecutionModel = addon.executionModel,
                        )
            }
            if (
                catalogEntry.compatibility.minimumHostApi !=
                addon.minimumHostApi
            ) {
                reasons +=
                    AddonCompatibility.Reason
                        .CatalogMinimumHostApiMismatch(
                            catalogMinimumHostApi =
                                catalogEntry.compatibility.minimumHostApi,
                            installedMinimumHostApi = addon.minimumHostApi,
                        )
            }
            if (
                catalogEntry.requiredHostCapabilities !=
                addon.requiredHostCapabilities
            ) {
                reasons +=
                    AddonCompatibility.Reason
                        .CatalogCapabilitiesMismatch(
                            catalogCapabilities =
                                catalogEntry.requiredHostCapabilities,
                            installedCapabilities =
                                addon.requiredHostCapabilities,
                        )
            }
        }

        return if (reasons.isEmpty()) {
            AddonCompatibility.Compatible
        } else {
            AddonCompatibility.Incompatible(reasons)
        }
    }

    private fun determineGrantedCapabilities(
        catalogEntry: AddonCatalogEntry?,
        installedAddon: DiscoveredAddon?,
        environment: AddonHostEnvironment,
    ) = if (catalogEntry != null && installedAddon != null) {
        catalogEntry.requiredHostCapabilities intersect
                installedAddon.requiredHostCapabilities intersect
                environment.availableCapabilities
    } else {
        emptySet()
    }

    private fun determineManagementBlockReason(
        installedAddon: DiscoveredAddon?,
        authorizationSourcesCurrent: Boolean,
        trust: AddonTrustState,
        compatibility: AddonCompatibility,
    ): AddonManagementBlockReason? {
        return when {
            !authorizationSourcesCurrent ->
                AddonManagementBlockReason.SOURCE_UNAVAILABLE

            installedAddon == null ->
                AddonManagementBlockReason.ADDON_NOT_INSTALLED

            trust !is AddonTrustState.TrustedOfficial ->
                AddonManagementBlockReason.ADDON_NOT_TRUSTED

            compatibility !is AddonCompatibility.Compatible ->
                AddonManagementBlockReason.ADDON_INCOMPATIBLE

            else -> null
        }
    }

    private fun AddonCatalogEntry.toMetadata() = AddonCatalogMetadata(
        packageName = packageName,
        displayName = displayName,
        shortDescription = shortDescription,
        fullDescription = fullDescription,
        executionModel = executionModel,
        protocolVersion = compatibility.protocolVersion,
        managementApiVersion = compatibility.managementApiVersion,
        minimumHostApi = compatibility.minimumHostApi,
        minimumAndroidSdk = compatibility.minimumAndroidSdk,
        requiredHostCapabilities = requiredHostCapabilities,
        official = security.official,
        installArtifact = AddonInstallArtifact(
            versionCode = release.versionCode,
            versionName = release.versionName,
            apkUrl = release.apkUrl,
            apkSha256 = release.apkSha256,
            sizeBytes = release.sizeBytes,
            signingCertificateSha256 =
                security.signingCertificateSha256,
        ),
        iconUrl = presentation.iconUrl,
        repositoryUrl = presentation.repositoryUrl,
        bannerArtifact = presentation.banner?.let { banner ->
            AddonBannerArtifact(
                url = banner.url,
                sha256 = banner.sha256,
            )
        },
    )

    private fun DiscoveredAddon.toMetadata() = InstalledAddonMetadata(
        uid = uid,
        packageName = packageName,
        versionCode = versionCode,
        versionName = versionName,
        managementApiVersion = managementApiVersion,
        signingCertificateSha256 = signingCertificateSha256,
        firstInstallTimeEpochMillis = firstInstallTimeEpochMillis,
        lastUpdateTimeEpochMillis = lastUpdateTimeEpochMillis,
    )

    private fun normalizeFingerprint(value: String): String {
        return value
            .replace(":", "")
            .trim()
            .uppercase(Locale.ROOT)
    }
}
