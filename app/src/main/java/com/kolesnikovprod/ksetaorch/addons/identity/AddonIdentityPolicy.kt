package com.kolesnikovprod.ksetaorch.addons.identity

import dev.openksenax.addons.contract.AddonId

/**
 * Host-level ограничения внешней addon identity, необходимой для безопасного
 * сопоставления catalog, PackageManager и файлового layout.
 *
 * Публичный [AddonId] задаёт wire-синтаксис, а эта policy дополнительно
 * ограничивает длину значений, которые OKx способен безопасно использовать
 * в именах app-specific файлов. Она не принимает trust-решений.
 *
 * @since 0.4
 */
internal object AddonIdentityPolicy {

    const val MAX_ADDON_ID_LENGTH = 128
    const val MAX_PACKAGE_NAME_LENGTH = 255
    const val MAX_PACKAGE_LEAF_LENGTH = 64

    fun isSupportedAddonId(addonId: AddonId): Boolean {
        return addonId.value.length <= MAX_ADDON_ID_LENGTH
    }

    fun isSupportedPackageName(packageName: String): Boolean {
        return packageName.length <= MAX_PACKAGE_NAME_LENGTH &&
            PACKAGE_NAME_REGEX.matches(packageName) &&
            packageName.substringAfterLast('.').length <=
                MAX_PACKAGE_LEAF_LENGTH
    }

    fun storagePackageLeafOrNull(packageName: String): String? {
        return packageName
            .takeIf(::isSupportedPackageName)
            ?.substringAfterLast('.')
    }

    private val PACKAGE_NAME_REGEX = Regex(
        "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$",
    )
}
