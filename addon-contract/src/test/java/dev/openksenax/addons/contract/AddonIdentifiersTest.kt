package dev.openksenax.addons.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddonIdentifiersTest {

    @Test
    fun addonIdAcceptsStableLowercaseIdentifier() {
        val id = AddonId("dev.openksenax.addon.no-radar")

        assertEquals("dev.openksenax.addon.no-radar", id.value)
    }

    @Test
    fun addonIdRejectsUppercaseAndWhitespace() {
        assertThrows(IllegalArgumentException::class.java) {
            AddonId("Dev.OpenKsenax.Addon")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AddonId("dev.openksenax bad")
        }
    }

    @Test
    fun capabilityIdRejectsBlankAndPathSeparators() {
        assertThrows(IllegalArgumentException::class.java) {
            HostCapabilityId("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HostCapabilityId("model/text-generation")
        }
    }
}
