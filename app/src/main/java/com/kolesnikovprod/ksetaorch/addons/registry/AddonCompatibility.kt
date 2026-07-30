package com.kolesnikovprod.ksetaorch.addons.registry

import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.HostCapabilityId

/**
 * Возможности текущей установки OKx.
 *
 * Registry сравнивает с ними требования каталога
 * и manifest установленного addon APK.
 *
 * @since 0.3
 */
data class AddonHostEnvironment(
    val hostApiVersion: Int,
    val supportedProtocolVersions: Set<Int>,
    val supportedManagementApiVersions: Set<Int>,
    val availableCapabilities: Set<HostCapabilityId>,
    val supportedExecutionModels: Set<AddonExecutionModel>,
    val androidSdkInt: Int,
)

/**
 * Результат единой registry-проверки совместимости аддона с OKx.
 *
 * @since 0.3
 */
sealed interface AddonCompatibility {

    data object Compatible : AddonCompatibility

    /**
     * Совместимость нельзя доказать.
     *
     * Например, APK установлен вручную,
     * но отсутствует в официальном каталоге.
     *
     * @since 0.3
     */
    data class Unknown(
        val reason: UnknownReason,
    ) : AddonCompatibility

    data class Incompatible(
        val reasons: Set<Reason>,
    ) : AddonCompatibility

    enum class UnknownReason {
        UNLISTED_ADDON,
        CATALOG_UNAVAILABLE,
    }

    sealed interface Reason {

        data class UnsupportedProtocol(
            val protocolVersion: Int,
            val supportedVersions: Set<Int>,
        ) : Reason

        data class UnsupportedManagementApi(
            val managementApiVersion: Int,
            val supportedVersions: Set<Int>,
        ) : Reason

        data class HostApiTooOld(
            val currentHostApi: Int,
            val requiredHostApi: Int,
        ) : Reason

        data class AndroidTooOld(
            val currentSdk: Int,
            val minimumSdk: Int,
        ) : Reason

        data class MissingCapabilities(
            val capabilities: Set<HostCapabilityId>,
        ) : Reason

        data class UnsupportedExecutionModel(
            val executionModel: AddonExecutionModel,
        ) : Reason

        data class CatalogPackageMismatch(
            val catalogPackageName: String,
            val installedPackageName: String,
        ) : Reason

        data class CatalogProtocolMismatch(
            val catalogProtocolVersion: Int,
            val installedProtocolVersion: Int,
        ) : Reason

        data class CatalogExecutionModelMismatch(
            val catalogExecutionModel: AddonExecutionModel,
            val installedExecutionModel: AddonExecutionModel,
        ) : Reason

        data class CatalogManagementApiMismatch(
            val catalogManagementApiVersion: Int,
            val installedManagementApiVersion: Int,
        ) : Reason

        data class CatalogMinimumHostApiMismatch(
            val catalogMinimumHostApi: Int,
            val installedMinimumHostApi: Int,
        ) : Reason

        data class CatalogCapabilitiesMismatch(
            val catalogCapabilities: Set<HostCapabilityId>,
            val installedCapabilities: Set<HostCapabilityId>,
        ) : Reason
    }
}
