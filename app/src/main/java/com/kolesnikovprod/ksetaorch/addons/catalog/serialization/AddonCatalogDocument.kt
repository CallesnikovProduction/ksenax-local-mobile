package com.kolesnikovprod.ksetaorch.addons.catalog.serialization

import kotlinx.serialization.Serializable

/**
 * DTO внешнего registry JSON.
 *
 * Эти модели не должны уходить в registry, UI или domain.
 * Они описывают только wire-format внешнего документа.
 *
 * @since 0.3
 */
@Serializable internal data class AddonCatalogDocument(
    val schemaVersion: Int,
    val channel: String,
    val generatedAtEpochMillis: Long,
    val addons: List<AddonCatalogEntryDocument>,
)

@Serializable internal data class AddonCatalogEntryDocument(
    val addonId: String,
    val packageName: String,
    val displayName: String,
    val shortDescription: String,
    val fullDescription: String? = null,
    val executionModel: String,
    val release: AddonReleaseDocument,
    val compatibility: AddonCompatibilityDocument,
    val requiredHostCapabilities: List<String>,
    val presentation: AddonPresentationDocument,
    val security: AddonSecurityDocument,
)

@Serializable internal data class AddonReleaseDocument(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String,
    val sizeBytes: Long? = null,
)

@Serializable internal data class AddonCompatibilityDocument(
    val protocolVersion: Int,
    val managementApiVersion: Int,
    val minimumHostApi: Int,
    val minimumAndroidSdk: Int,
)

@Serializable internal data class AddonPresentationDocument(
    val iconUrl: String? = null,
    val repositoryUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerSha256: String? = null,
)

@Serializable internal data class AddonSecurityDocument(
    val official: Boolean,
    val signingCertificateSha256: String,
)
