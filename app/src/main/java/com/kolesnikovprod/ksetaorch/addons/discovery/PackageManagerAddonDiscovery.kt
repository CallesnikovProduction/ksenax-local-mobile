package com.kolesnikovprod.ksetaorch.addons.discovery

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.AddonManifestContract
import dev.openksenax.addons.contract.HostCapabilityId
import dev.openksenax.addons.contract.management.AddonManagementContract
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-реализация discovery через **PackageManager**.
 *
 * Алгоритм:
 * 1. ищет services с ADDON_SERVICE_ACTION;
 * 2. читает service meta-data;
 * 3. получает package version и signing certificates;
 * 4. создаёт DiscoveredAddon;
 * 5. отклоняет повреждённые и конфликтующие записи.
 *
 * Не проверяет каталог, trust policy и совместимость с OKx.
 *
 * @since 0.3
 */
class PackageManagerAddonDiscovery(
    private val packageManager   : PackageManager,
    private val ioDispatcher     : CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long          = System::currentTimeMillis,
) : AddonDiscovery {

    override suspend fun discover(): AddonDiscoverySnapshot {
        return withContext(ioDispatcher) {
            val resolveInfos = try {
                queryAddonServices()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                throw AddonDiscoveryException.ScanFailed(cause = error)
            }

            val accepted = mutableListOf<DiscoveredAddon>()
            val rejected = mutableListOf<RejectedAddonCandidate>()

            resolveInfos.forEach { resolveInfo ->
                when (val result = parseCandidate(resolveInfo)) {
                    is CandidateParseResult.Accepted ->
                        accepted += result.addon

                    is CandidateParseResult.Rejected ->
                        rejected += result.candidate
                }
            }

            // разрешение конфликтов package
            val deduplicatedByPackage =
                rejectPackagesWithMultipleServices(
                    accepted = accepted,
                    rejected = rejected,
                )

            // разрешение конфликтов по id
            val deduplicatedByAddonId =
                rejectDuplicateAddonIds(
                    accepted = deduplicatedByPackage,
                    rejected = rejected,
                )

            AddonDiscoverySnapshot(
                addons               = deduplicatedByAddonId
                    .sortedBy { addon -> addon.addonId.value },
                rejectedCandidates   = rejected,
                scannedAtEpochMillis = currentTimeMillis(),
            )
        }
    }

    private fun queryAddonServices(): List<ResolveInfo> {
        val intent = Intent(AddonManifestContract.ADDON_SERVICE_ACTION)

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(
                    PackageManager.GET_META_DATA.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(
                intent,
                PackageManager.GET_META_DATA,
            )
        }
    }

    private fun parseCandidate(resolveInfo: ResolveInfo): CandidateParseResult {
        val serviceInfo = resolveInfo.serviceInfo
            ?: return CandidateParseResult.Rejected(RejectedAddonCandidate(
                packageName      = null,
                serviceClassName = null,
                reason           = AddonDiscoveryRejectionReason.MissingServiceInfo
                ))

        val packageName = serviceInfo.packageName
        val serviceClassName = serviceInfo.name

        return try {
            if (!serviceInfo.exported) {
                reject(
                    AddonDiscoveryRejectionReason
                        .ServiceNotExported,
                )
            }

            if (
                serviceInfo.permission !=
                AddonManagementContract.BIND_PERMISSION
            ) {
                reject(
                    AddonDiscoveryRejectionReason
                        .InvalidManagementPermission(
                            actualPermission = serviceInfo.permission,
                        ),
                )
            }

            val metadata = serviceInfo.metaData
                ?: reject(
                    AddonDiscoveryRejectionReason
                        .MissingMetadata(
                            key = "service.metaData",
                        ),
                )

            val addonId = AddonId(
                metadata.requireString(
                    AddonManifestContract.META_ADDON_ID,
                ),
            )

            val protocolVersion =
                metadata.requirePositiveInt(
                    AddonManifestContract
                        .META_PROTOCOL_VERSION,
                )

            val managementApiVersion =
                metadata.requirePositiveInt(
                    AddonManifestContract
                        .META_MANAGEMENT_API_VERSION,
                )

            val minimumHostApi =
                metadata.requirePositiveInt(
                    AddonManifestContract
                        .META_MINIMUM_HOST_API,
                )

            val executionModel =
                metadata.requireExecutionModel(
                    AddonManifestContract
                        .META_EXECUTION_MODEL,
                )

            val requiredCapabilities =
                metadata.readCapabilities(
                    AddonManifestContract
                        .META_REQUIRED_CAPABILITIES,
                )

            val packageInfo = packageManager.readAddonPackageInfo(packageName)

            val fingerprints = packageInfo.addonSigningCertificateSha256()

            if (fingerprints.isEmpty()) {
                reject(
                    AddonDiscoveryRejectionReason
                        .SigningCertificateUnavailable,
                )
            }

            CandidateParseResult.Accepted(
                addon = DiscoveredAddon(
                    addonId                     = addonId,
                    uid                         = serviceInfo.applicationInfo.uid,
                    packageName                 = packageName,
                    serviceClassName            = serviceClassName,
                    versionCode                 = PackageInfoCompat
                        .getLongVersionCode(packageInfo),
                    versionName                 = packageInfo.versionName,
                    protocolVersion             = protocolVersion,
                    managementApiVersion        = managementApiVersion,
                    minimumHostApi              = minimumHostApi,
                    executionModel              = executionModel,
                    requiredHostCapabilities    = requiredCapabilities,
                    signingCertificateSha256    = fingerprints,
                    firstInstallTimeEpochMillis = packageInfo.firstInstallTime,
                    lastUpdateTimeEpochMillis   = packageInfo.lastUpdateTime,
                ),
            )
        } catch (rejection: CandidateRejected) {
            CandidateParseResult.Rejected(
                candidate = RejectedAddonCandidate(
                    packageName      = packageName,
                    serviceClassName = serviceClassName,
                    reason           = rejection.reason,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            CandidateParseResult.Rejected(
                candidate = RejectedAddonCandidate(
                    packageName      = packageName,
                    serviceClassName = serviceClassName,
                    reason           =
                        AddonDiscoveryRejectionReason
                            .UnexpectedFailure(
                                message = error.message,
                            ),
                ),
            )
        }
    }

    private fun rejectPackagesWithMultipleServices(
        accepted: List<DiscoveredAddon>,
        rejected: MutableList<RejectedAddonCandidate>,
    ): List<DiscoveredAddon> {
        val conflictedPackages = accepted
            .groupBy { addon -> addon.packageName }
            .filterValues { addons -> addons.size > 1 }
            .keys

        if (conflictedPackages.isEmpty()) {
            return accepted
        }

        accepted
            .filter { addon ->
                addon.packageName in conflictedPackages
            }
            .forEach { addon ->
                rejected += RejectedAddonCandidate(
                    packageName = addon.packageName,
                    serviceClassName =
                        addon.serviceClassName,
                    reason =
                        AddonDiscoveryRejectionReason
                            .MultipleAddonServicesInPackage(
                                packageName =
                                    addon.packageName,
                            ),
                )
            }

        return accepted.filterNot { addon ->
            addon.packageName in conflictedPackages
        }
    }

    private fun rejectDuplicateAddonIds(
        accepted: List<DiscoveredAddon>,
        rejected: MutableList<RejectedAddonCandidate>,
    ): List<DiscoveredAddon> {
        val duplicateIds = accepted
            .groupBy { addon -> addon.addonId }
            .filterValues { addons ->
                addons.size > 1
            }
            .keys

        if (duplicateIds.isEmpty()) {
            return accepted
        }

        accepted
            .filter { addon ->
                addon.addonId in duplicateIds
            }
            .forEach { addon ->
                rejected += RejectedAddonCandidate(
                    packageName = addon.packageName,
                    serviceClassName =
                        addon.serviceClassName,
                    reason =
                        AddonDiscoveryRejectionReason
                            .DuplicateAddonId(
                                addonId =
                                    addon.addonId,
                            ),
                )
            }

        return accepted.filterNot { addon ->
            addon.addonId in duplicateIds
        }
    }

    private fun Bundle.requireString(key: String): String {
        val value = metadataValue(key)?.toString()?.trim()

        if (value.isNullOrEmpty()) {
            reject(
                AddonDiscoveryRejectionReason
                    .MissingMetadata(key),
            )
        }

        return value
    }

    private fun Bundle.requirePositiveInt(key: String): Int {
        val rawValue = metadataValue(key)

        val value = when (rawValue) {
            is Int    -> rawValue
            is Long   -> rawValue
                .takeIf { candidate ->
                    candidate in 1L..Int.MAX_VALUE.toLong()
                }
                ?.toInt()
            is String -> rawValue.toIntOrNull()
            else      -> null
        }

        if (value == null || value <= 0) {
            reject(
                AddonDiscoveryRejectionReason
                    .InvalidMetadata(
                        key = key,
                        value = rawValue?.toString(),
                    ),
            )
        }

        return value
    }

    private fun Bundle.requireExecutionModel(key: String): AddonExecutionModel {
        val rawValue = requireString(key)

        return try {
            AddonExecutionModel.valueOf(
                rawValue.uppercase(Locale.ROOT),
            )
        } catch (_: IllegalArgumentException) {
            reject(
                AddonDiscoveryRejectionReason
                    .InvalidMetadata(
                        key = key,
                        value = rawValue,
                    ),
            )
        }
    }

    private fun Bundle.readCapabilities(key: String): Set<HostCapabilityId> {
        val rawValue = metadataValue(key)?.toString()?.trim().orEmpty()

        if (rawValue.isEmpty()) return emptySet()

        return try {
            rawValue
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map(::HostCapabilityId)
                .toSet()
        } catch (_: IllegalArgumentException) {
            reject(
                AddonDiscoveryRejectionReason
                    .InvalidMetadata(
                        key = key,
                        value = rawValue,
                    ),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.metadataValue(key: String): Any? = get(key)

    private fun reject(
        reason: AddonDiscoveryRejectionReason,
    ): Nothing {
        throw CandidateRejected(reason)
    }

    private sealed interface CandidateParseResult {

        data class Accepted(
            val addon: DiscoveredAddon,
        ) : CandidateParseResult

        data class Rejected(
            val candidate: RejectedAddonCandidate,
        ) : CandidateParseResult
    }

    private class CandidateRejected(
        val reason: AddonDiscoveryRejectionReason,
    ) : RuntimeException()
}
