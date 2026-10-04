package com.kolesnikovprod.ksetaorch.addons.modelprovider

import android.content.pm.PackageManager
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId
import dev.openksenax.addons.contract.model.ModelFailureCode
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementBlockReason
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistry

/**
 * Сопоставляет Binder UID с registry-owned addon identity.
 *
 * Класс не считает trust, certificate и compatibility: он только
 * читает [com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon.managementBlockReason]
 * и granted capabilities из [AddonRegistry].
 *
 * @since 0.3
 */
internal class ModelProviderCallerAuthorizer(
    private val packageManager: PackageManager,
    private val registry: AddonRegistry,
    private val availableCapabilities: () -> Set<HostCapabilityId>,
) {

    fun authorize(
        callingUid: Int,
        claimedAddonId: AddonId,
        requestedCapability: HostCapabilityId,
    ): ModelProviderAuthorization {
        val state = registry.authorizationState()
        if (!state.isInitialized) {
            return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.PROVIDER_NOT_READY,
                message = "Addon registry is not ready",
                retryable = true,
            )
        }

        val addon = state.addons
            .firstOrNull { candidate ->
                candidate.addonId == claimedAddonId
            }
            ?: return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.UNAUTHORIZED_CALLER,
                message = "Unknown addonId",
            )

        val addonPackage = addon.managementEndpoint
            ?.packageName
            ?: return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.UNAUTHORIZED_CALLER,
                message = "Addon management endpoint is unavailable",
            )

        if (
            addon.installedMetadata?.uid != callingUid ||
            addonPackage !in packagesForUid(callingUid)
        ) {
            return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.UNAUTHORIZED_CALLER,
                message =
                    "Calling UID does not own claimed addonId",
            )
        }

        addon.managementBlockReason
            ?.toAuthorizationRejection()
            ?.let { rejection -> return rejection }

        if (requestedCapability !in availableCapabilities()) {
            return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.CAPABILITY_UNAVAILABLE,
                message = "Capability is unavailable in model runtime",
                retryable = true,
            )
        }

        if (
            requestedCapability !in
            addon.grantedHostCapabilities
        ) {
            return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.CAPABILITY_NOT_DECLARED,
                message =
                    "Capability is not granted to this addon",
            )
        }

        if (!addon.canUseHostCapability(requestedCapability)) {
            return ModelProviderAuthorization.Rejected(
                code = ModelFailureCode.UNAUTHORIZED_CALLER,
                message = "Registry denied host capability",
            )
        }

        return ModelProviderAuthorization.Authorized
    }

    fun enforceAuthorizedCaller(callingUid: Int) {
        val state = registry.authorizationState()
        if (!state.isInitialized) {
            throw SecurityException("Addon registry is not ready")
        }

        val callerPackages = packagesForUid(callingUid)
        val authorized = state.addons.any { addon ->
            addon.canBeManaged &&
                    addon.installedMetadata?.uid == callingUid &&
                    addon.managementEndpoint
                        ?.packageName in callerPackages
        }

        if (!authorized) {
            throw SecurityException(
                "Caller is not an authorized addon",
            )
        }
    }

    fun capabilitiesForCaller(
        callingUid: Int,
    ): Set<HostCapabilityId> {
        val state = registry.authorizationState()
        if (!state.isInitialized) {
            throw SecurityException("Addon registry is not ready")
        }

        val callerPackages = packagesForUid(callingUid)
        val callerAddons = state.addons.filter { addon ->
            addon.installedMetadata?.uid == callingUid &&
                    addon.managementEndpoint
                ?.packageName in callerPackages
        }

        if (callerAddons.none { addon -> addon.canBeManaged }) {
            throw SecurityException(
                "Caller is not an authorized addon",
            )
        }

        return availableCapabilities()
            .filterTo(mutableSetOf()) { capability ->
                callerAddons.any { addon ->
                    addon.canUseHostCapability(capability)
                }
            }
    }

    private fun packagesForUid(callingUid: Int): Set<String> {
        return try {
            packageManager
                .getPackagesForUid(callingUid)
                .orEmpty()
                .toSet()
        } catch (_: RuntimeException) {
            emptySet()
        }
    }

    private fun AddonManagementBlockReason
            .toAuthorizationRejection():
            ModelProviderAuthorization.Rejected {
        return when (this) {
            AddonManagementBlockReason.SOURCE_UNAVAILABLE ->
                ModelProviderAuthorization.Rejected(
                    code = ModelFailureCode.ADDON_NOT_TRUSTED,
                    message = "Addon trust source is unavailable",
                    retryable = true,
                )

            AddonManagementBlockReason.ADDON_NOT_INSTALLED ->
                ModelProviderAuthorization.Rejected(
                    code = ModelFailureCode.UNAUTHORIZED_CALLER,
                    message = "Addon is not installed",
                )

            AddonManagementBlockReason.ADDON_NOT_TRUSTED ->
                ModelProviderAuthorization.Rejected(
                    code = ModelFailureCode.ADDON_NOT_TRUSTED,
                    message = "Addon is not trusted by registry",
                )

            AddonManagementBlockReason.ADDON_INCOMPATIBLE ->
                ModelProviderAuthorization.Rejected(
                    code = ModelFailureCode.ADDON_INCOMPATIBLE,
                    message = "Addon is incompatible with this host",
                )
        }
    }
}

/**
 * Результат проверки Binder caller и запрошенной host capability.
 *
 * @since 0.4
 */
internal sealed interface ModelProviderAuthorization {

    data object Authorized : ModelProviderAuthorization

    data class Rejected(
        val code: ModelFailureCode,
        val message: String,
        val retryable: Boolean = false,
    ) : ModelProviderAuthorization
}
