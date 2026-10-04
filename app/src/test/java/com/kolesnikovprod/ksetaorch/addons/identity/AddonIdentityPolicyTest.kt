package com.kolesnikovprod.ksetaorch.addons.identity

import dev.openksenax.addons.contract.AddonId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonIdentityPolicyTest {

    @Test
    fun `accepts identity that fits Android and storage boundaries`() {
        assertTrue(
            AddonIdentityPolicy.isSupportedPackageName(
                "com.callesnikovprod.noradar",
            ),
        )
        assertEquals(
            "noradar",
            AddonIdentityPolicy.storagePackageLeafOrNull(
                "com.callesnikovprod.noradar",
            ),
        )
        assertTrue(
            AddonIdentityPolicy.isSupportedAddonId(
                AddonId("dev.openksenax.addon.no-radar"),
            ),
        )
    }

    @Test
    fun `rejects identities that cannot become safe storage keys`() {
        val oversizedLeaf = "a".repeat(
            AddonIdentityPolicy.MAX_PACKAGE_LEAF_LENGTH + 1,
        )
        assertFalse(
            AddonIdentityPolicy.isSupportedPackageName(
                "com.example.$oversizedLeaf",
            ),
        )
        assertNull(
            AddonIdentityPolicy.storagePackageLeafOrNull(
                "com.example.$oversizedLeaf",
            ),
        )
        assertFalse(
            AddonIdentityPolicy.isSupportedAddonId(
                AddonId(
                    "a".repeat(
                        AddonIdentityPolicy.MAX_ADDON_ID_LENGTH + 1,
                    ),
                ),
            ),
        )
    }
}
