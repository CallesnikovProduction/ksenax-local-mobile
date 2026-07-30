package com.kolesnikovprod.ksetaorch.addons.catalog

import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId

/**
 * Доменное описание аддона, опубликованного в каталоге OKx.
 *
 * Это не состояние установленного APK.
 *
 * Запись отвечает на вопросы:
 * - что это за аддон;
 * - где скачать его последнюю версию;
 * - какие возможности OKx ему требуются;
 * - какие версии Android и OKx поддерживаются;
 * - каким сертификатом должен быть подписан APK.
 *
 * @since 0.3
 */
data class AddonCatalogEntry(
    val addonId: AddonId,
    val packageName: String,
    val displayName: String,
    val shortDescription: String,
    val fullDescription: String?,
    val executionModel: AddonExecutionModel,
    val release: AddonReleaseDescriptor,
    val compatibility: AddonCompatibilityRequirements,
    val requiredHostCapabilities: Set<HostCapabilityId>,
    val presentation: AddonPresentationDescriptor,
    val security: AddonSecurityDescriptor,
)

/**
 * Конкретная опубликованная версия аддона.
 *
 * @since 0.3
 */
data class AddonReleaseDescriptor(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String,
    val sizeBytes: Long?,
)

/**
 * Требования аддона к окружению.
 *
 * @since 0.3
 */
data class AddonCompatibilityRequirements(
    val protocolVersion: Int,
    val managementApiVersion: Int,
    val minimumHostApi: Int,
    val minimumAndroidSdk: Int,
)

/**
 * Метаданные, используемые для отрисовки каталога.
 *
 * @since 0.3
 */
data class AddonPresentationDescriptor(
    val iconUrl: String?,
    val repositoryUrl: String?,
    val banner: AddonBannerDescriptor? = null,
)

/**
 * Проверяемое внешнее изображение шапки аддона.
 *
 * Размер изображения является host-контрактом и проверяется banner-контуром,
 * поэтому во внешнем registry публикуются только URL и SHA-256.
 *
 * @since 0.3
 */
data class AddonBannerDescriptor(
    val url: String,
    val sha256: String,
)

/**
 * Ожидаемая identity APK.
 *
 * Реальный установленный сертификат сверяет registry-контур при объединении
 * catalog-записи с discovery-снимком APK.
 *
 * @since 0.3
 */
data class AddonSecurityDescriptor(
    val official: Boolean,
    val signingCertificateSha256: String,
)
