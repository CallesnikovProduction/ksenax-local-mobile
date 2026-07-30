package com.kolesnikovprod.ksetaorch.addons.registry

import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId

/**
 * Итоговое представление аддона внутри OKx.
 *
 * UI, coordination и model provider получают только этот registry-owned
 * результат, а не исходные модели catalog или discovery. Все разрешающие
 * решения уже приняты registry и должны использоваться без повторной
 * проверки в downstream-контурах.
 *
 * @since 0.3
 */
data class RegisteredAddon(
    val addonId: AddonId,
    val catalogMetadata: AddonCatalogMetadata?,
    val installedMetadata: InstalledAddonMetadata?,
    val managementEndpoint: AddonManagementEndpoint?,
    val installation: AddonInstallationState,
    val compatibility: AddonCompatibility,
    val trust: AddonTrustState,
    val grantedHostCapabilities: Set<HostCapabilityId>,
    val managementBlockReason: AddonManagementBlockReason?,
) {

    val displayName: String
        get() = catalogMetadata?.displayName ?: addonId.value

    val isInstalled: Boolean
        get() = installedMetadata != null

    /**
     * Можно ли открыть обычный launcher UI уже установленного APK.
     *
     * Запуск Activity не является доступом к management API или model
     * capabilities: он не ослабляет fail-closed verdict этих контуров.
     *
     * @since 0.3
     */
    val canOpenUi: Boolean
        get() = installedMetadata != null

    /**
     * Разрешено ли OKx подключаться к management service.
     *
     * @since 0.3
     */
    val canBeManaged: Boolean
        get() =
            managementEndpoint != null &&
                    managementBlockReason == null

    /**
     * Проверяет уже вычисленный registry capability verdict.
     *
     * Метод намеренно не принимает решений о trust или compatibility:
     * они учтены при формировании [managementBlockReason] и
     * [grantedHostCapabilities].
     *
     * @since 0.3
     */
    fun canUseHostCapability(
        capabilityId: HostCapabilityId,
    ): Boolean {
        return canBeManaged &&
                capabilityId in grantedHostCapabilities
    }
}

/**
 * Безопасная для UI проекция опубликованных данных аддона.
 *
 * Она не содержит trust-решений и download-команд. Отсутствие объекта
 * означает, что registry не располагает записью каталога.
 *
 * @since 0.3
 */
data class AddonCatalogMetadata(
    val packageName: String,
    val displayName: String,
    val shortDescription: String,
    val fullDescription: String?,
    val executionModel: AddonExecutionModel,
    val protocolVersion: Int,
    val managementApiVersion: Int,
    val minimumHostApi: Int,
    val minimumAndroidSdk: Int,
    val requiredHostCapabilities: Set<HostCapabilityId>,
    val official: Boolean,
    val installArtifact: AddonInstallArtifact,
    val iconUrl: String?,
    val repositoryUrl: String?,
    val bannerArtifact: AddonBannerArtifact? = null,
)

/**
 * Registry-owned параметры внешнего presentation-баннера.
 *
 * @since 0.3
 */
data class AddonBannerArtifact(
    val url: String,
    val sha256: String,
)

/**
 * Registry-owned ожидаемые параметры опубликованного APK.
 *
 * Downloader и verifier берут эти данные из registry,
 * не обходя его через raw catalog DTO.
 *
 * @since 0.3
 */
data class AddonInstallArtifact(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String,
    val sizeBytes: Long?,
    val signingCertificateSha256: String,
)

/**
 * Registry-owned сведения о реально установленном addon APK.
 *
 * @since 0.3
 */
data class InstalledAddonMetadata(
    val uid: Int,
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val managementApiVersion: Int,
    val signingCertificateSha256: Set<String>,
    val firstInstallTimeEpochMillis: Long = 0L,
    val lastUpdateTimeEpochMillis: Long = 0L,
)

/**
 * Точный Android service endpoint, полученный discovery из manifest APK.
 *
 * Имя компонента не задаётся OKx и не хардкодится: registry передаёт
 * downstream-контурам только обнаруженную пару package/service.
 *
 * @since 0.3
 */
data class AddonManagementEndpoint(
    val uid: Int,
    val packageName: String,
    val serviceClassName: String,
    val managementApiVersion: Int,
)

/**
 * Fail-closed причина, по которой management и host capabilities запрещены.
 *
 * `null` в [RegisteredAddon.managementBlockReason] является единственным
 * разрешающим verdict.
 *
 * @since 0.3
 */
enum class AddonManagementBlockReason {
    SOURCE_UNAVAILABLE,
    ADDON_NOT_INSTALLED,
    ADDON_NOT_TRUSTED,
    ADDON_INCOMPATIBLE,
}
