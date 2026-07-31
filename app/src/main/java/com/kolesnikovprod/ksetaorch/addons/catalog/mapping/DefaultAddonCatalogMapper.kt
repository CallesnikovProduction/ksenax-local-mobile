package com.kolesnikovprod.ksetaorch.addons.catalog.mapping

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogEntry
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSnapshot
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSource
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonBannerDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonChannel
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCompatibilityRequirements
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonPresentationDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonReleaseDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonSecurityDescriptor
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDocument
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId
import java.util.Locale

internal class DefaultAddonCatalogMapper :
    AddonCatalogMapper {

    override fun map(
        document: AddonCatalogDocument,
        source: AddonCatalogSource,
        loadedAtEpochMillis: Long,
    ): AddonCatalogSnapshot {
        return AddonCatalogSnapshot(
            schemaVersion = document.schemaVersion,
            channel = AddonChannel.valueOf(
                document.channel.uppercase(Locale.ROOT),
            ),
            generatedAtEpochMillis =
                document.generatedAtEpochMillis,
            loadedAtEpochMillis = loadedAtEpochMillis,
            source = source,
            entries = document.addons.map { entry ->
                AddonCatalogEntry(
                    addonId = AddonId(entry.addonId),
                    packageName = entry.packageName,
                    displayName = entry.displayName,
                    shortDescription = entry.shortDescription,
                    fullDescription = entry.fullDescription,
                    executionModel =
                        AddonExecutionModel.valueOf(
                            entry.executionModel.uppercase(
                                Locale.ROOT,
                            ),
                        ),
                    release = AddonReleaseDescriptor(
                        versionCode =
                            entry.release.versionCode,
                        versionName =
                            entry.release.versionName,
                        apkUrl =
                            entry.release.apkUrl,
                        apkSha256 =
                            entry.release.apkSha256.uppercase(),
                        sizeBytes =
                            entry.release.sizeBytes,
                    ),
                    compatibility =
                        AddonCompatibilityRequirements(
                            protocolVersion =
                                entry.compatibility.protocolVersion,
                            managementApiVersion =
                                entry.compatibility.managementApiVersion,
                            minimumHostApi =
                                entry.compatibility.minimumHostApi,
                            minimumAndroidSdk =
                                entry.compatibility.minimumAndroidSdk,
                        ),
                    requiredHostCapabilities =
                        entry.requiredHostCapabilities
                            .map(::HostCapabilityId)
                            .toSet(),
                    presentation =
                        AddonPresentationDescriptor(
                            iconUrl =
                                entry.presentation.iconUrl,
                            repositoryUrl =
                                entry.presentation.repositoryUrl,
                            banner = entry.presentation.bannerUrl
                                ?.let { bannerUrl ->
                                    entry.presentation.bannerSha256
                                        ?.let { bannerSha256 ->
                                            AddonBannerDescriptor(
                                                url = bannerUrl,
                                                sha256 =
                                                    bannerSha256.uppercase(
                                                        Locale.ROOT,
                                                    ),
                                            )
                                        }
                                },
                        ),
                    security =
                        AddonSecurityDescriptor(
                            official = entry.security.official,
                            signingCertificateSha256 =
                                entry.security
                                    .signingCertificateSha256
                                    .uppercase(),
                        ),
                )
            },
        )
    }
}
