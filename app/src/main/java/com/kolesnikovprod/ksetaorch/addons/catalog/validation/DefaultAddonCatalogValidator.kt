package com.kolesnikovprod.ksetaorch.addons.catalog.validation

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonChannel
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDocument
import com.kolesnikovprod.ksetaorch.addons.identity.AddonIdentityPolicy
import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId
import java.net.URI
import java.util.Locale

/**
 * Полная семантическая проверка внешнего registry документа до его
 * преобразования во внутренние типы.
 *
 * Валидатор накапливает все найденные нарушения, чтобы отклонённый документ
 * можно было диагностировать одним проходом и при этом не допустить частично
 * принятого каталога.
 *
 * @since 0.4
 */
internal class DefaultAddonCatalogValidator(
    private val supportedSchemaVersion: Int,
    private val expectedChannel: AddonChannel,
) : AddonCatalogValidator {

    init {
        require(supportedSchemaVersion > 0) {
            "supportedSchemaVersion must be greater than zero"
        }
    }

    override fun validate(
        document: AddonCatalogDocument,
    ): List<CatalogViolation> {
        val violations = mutableListOf<CatalogViolation>()

        if (document.schemaVersion != supportedSchemaVersion) {
            violations += CatalogViolation(
                path = "schemaVersion",
                message =
                    "Expected $supportedSchemaVersion, " +
                            "received ${document.schemaVersion}",
            )
        }

        if (
            document.channel.uppercase(Locale.ROOT) !=
            expectedChannel.name
        ) {
            violations += CatalogViolation(
                path = "channel",
                message =
                    "Expected ${expectedChannel.name}, " +
                            "received ${document.channel}",
            )
        }

        if (document.generatedAtEpochMillis <= 0L) {
            violations += CatalogViolation(
                path = "generatedAtEpochMillis",
                message = "Must be greater than zero",
            )
        }

        val duplicateAddonIds = document.addons
            .groupingBy { entry -> entry.addonId }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        duplicateAddonIds.forEach { addonId ->
            violations += CatalogViolation(
                path = "addons",
                message = "Duplicate addonId: $addonId",
            )
        }

        val duplicatePackages = document.addons
            .groupingBy { entry -> entry.packageName }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        duplicatePackages.forEach { packageName ->
            violations += CatalogViolation(
                path = "addons",
                message = "Duplicate packageName: $packageName",
            )
        }

        val duplicateStorageLeaves = document.addons
            .groupingBy { entry ->
                entry.packageName.substringAfterLast('.')
            }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        duplicateStorageLeaves.forEach { packageLeaf ->
            violations += CatalogViolation(
                path = "addons",
                message =
                    "Duplicate addon storage directory: $packageLeaf",
            )
        }

        document.addons.forEachIndexed { index, entry ->
            val basePath = "addons[$index]"

            if (entry.addonId.isBlank()) {
                violations += CatalogViolation(
                    path = "$basePath.addonId",
                    message = "Must not be blank",
                )
            } else if (!canCreateAddonId(entry.addonId)) {
                violations += CatalogViolation(
                    path = "$basePath.addonId",
                    message = "Must be a valid lowercase AddonId",
                )
            } else if (
                !AddonIdentityPolicy.isSupportedAddonId(
                    AddonId(entry.addonId),
                )
            ) {
                violations += CatalogViolation(
                    path = "$basePath.addonId",
                    message = "Exceeds the host identity length limit",
                )
            }

            if (!AddonIdentityPolicy.isSupportedPackageName(entry.packageName)) {
                violations += CatalogViolation(
                    path = "$basePath.packageName",
                    message = "Invalid or unsupported Android package name",
                )
            }

            if (entry.displayName.isBlank()) {
                violations += CatalogViolation(
                    path = "$basePath.displayName",
                    message = "Must not be blank",
                )
            }

            if (entry.shortDescription.isBlank()) {
                violations += CatalogViolation(
                    path = "$basePath.shortDescription",
                    message = "Must not be blank",
                )
            }

            if (!isKnownExecutionModel(entry.executionModel)) {
                violations += CatalogViolation(
                    path = "$basePath.executionModel",
                    message = "Unsupported addon execution model",
                )
            }

            if (entry.release.versionCode <= 0L) {
                violations += CatalogViolation(
                    path = "$basePath.release.versionCode",
                    message = "Must be greater than zero",
                )
            }

            if (entry.release.versionName.isBlank()) {
                violations += CatalogViolation(
                    path = "$basePath.release.versionName",
                    message = "Must not be blank",
                )
            }

            if (!isHttpsUrl(entry.release.apkUrl)) {
                violations += CatalogViolation(
                    path = "$basePath.release.apkUrl",
                    message = "Must be a valid HTTPS URL",
                )
            }

            if (!SHA_256_REGEX.matches(entry.release.apkSha256)) {
                violations += CatalogViolation(
                    path = "$basePath.release.apkSha256",
                    message = "Must contain 64 hexadecimal characters",
                )
            }

            entry.release.sizeBytes?.let { sizeBytes ->
                if (sizeBytes <= 0L) {
                    violations += CatalogViolation(
                        path = "$basePath.release.sizeBytes",
                        message = "Must be greater than zero",
                    )
                }
            }

            if (!SHA_256_REGEX.matches(
                    entry.security.signingCertificateSha256,
                )) {
                violations += CatalogViolation(
                    path =
                        "$basePath.security." +
                                "signingCertificateSha256",
                    message =
                        "Must contain 64 hexadecimal characters",
                )
            }

            if (entry.compatibility.protocolVersion <= 0) {
                violations += CatalogViolation(
                    path =
                        "$basePath.compatibility.protocolVersion",
                    message = "Must be greater than zero",
                )
            }

            if (entry.compatibility.minimumHostApi <= 0) {
                violations += CatalogViolation(
                    path =
                        "$basePath.compatibility.minimumHostApi",
                    message = "Must be greater than zero",
                )
            }

            if (entry.compatibility.managementApiVersion <= 0) {
                violations += CatalogViolation(
                    path =
                        "$basePath.compatibility.managementApiVersion",
                    message = "Must be greater than zero",
                )
            }

            if (entry.compatibility.minimumAndroidSdk <= 0) {
                violations += CatalogViolation(
                    path =
                        "$basePath.compatibility.minimumAndroidSdk",
                    message = "Must be greater than zero",
                )
            }

            val duplicateCapabilities =
                entry.requiredHostCapabilities
                    .groupingBy { capability -> capability }
                    .eachCount()
                    .filterValues { count -> count > 1 }
                    .keys

            duplicateCapabilities.forEach { capability ->
                violations += CatalogViolation(
                    path =
                        "$basePath.requiredHostCapabilities",
                    message = "Duplicate capability: $capability",
                )
            }

            entry.requiredHostCapabilities.forEachIndexed { capabilityIndex, capability ->
                if (!canCreateCapabilityId(capability)) {
                    violations += CatalogViolation(
                        path =
                            "$basePath.requiredHostCapabilities" +
                                    "[$capabilityIndex]",
                        message =
                            "Must be a valid lowercase " +
                                    "HostCapabilityId",
                    )
                }
            }

            entry.presentation.iconUrl
                ?.takeUnless(::isHttpsUrl)
                ?.let {
                    violations += CatalogViolation(
                        path = "$basePath.presentation.iconUrl",
                        message = "Must be a valid HTTPS URL",
                    )
                }

            entry.presentation.repositoryUrl
                ?.takeUnless(::isHttpsUrl)
                ?.let {
                    violations += CatalogViolation(
                        path =
                            "$basePath.presentation.repositoryUrl",
                        message = "Must be a valid HTTPS URL",
                    )
                }

            val bannerUrl = entry.presentation.bannerUrl
            val bannerSha256 = entry.presentation.bannerSha256
            if ((bannerUrl == null) != (bannerSha256 == null)) {
                violations += CatalogViolation(
                    path = "$basePath.presentation",
                    message =
                        "bannerUrl and bannerSha256 must be " +
                            "declared together",
                )
            }
            bannerUrl
                ?.takeUnless(::isHttpsUrl)
                ?.let {
                    violations += CatalogViolation(
                        path = "$basePath.presentation.bannerUrl",
                        message = "Must be a valid HTTPS URL",
                    )
                }
            bannerSha256
                ?.takeUnless(SHA_256_REGEX::matches)
                ?.let {
                    violations += CatalogViolation(
                        path =
                            "$basePath.presentation.bannerSha256",
                        message =
                            "Must contain 64 hexadecimal characters",
                    )
                }
        }

        return violations
    }

    private fun isHttpsUrl(value: String): Boolean {
        return try {
            val uri = URI(value)

            uri.scheme.equals(
                other = "https",
                ignoreCase = true,
            ) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.fragment == null
        } catch (_: Exception) {
            false
        }
    }

    private fun canCreateAddonId(value: String): Boolean {
        return try {
            AddonId(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun canCreateCapabilityId(value: String): Boolean {
        return try {
            HostCapabilityId(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun isKnownExecutionModel(value: String): Boolean {
        return AddonExecutionModel.entries.any { executionModel ->
            executionModel.name.equals(
                other = value,
                ignoreCase = true,
            )
        }
    }

    private companion object {

        val SHA_256_REGEX = Regex(
            pattern = "^[A-Fa-f0-9]{64}$",
        )

    }
}
