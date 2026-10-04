package com.kolesnikovprod.ksetaorch.addons.download

import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.HostCapabilityId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAddonManifestMetadataTest {

    @Test
    fun `execution model parsing is locale independent`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals(
                AddonExecutionModel.AUTONOMOUS_APPLICATION,
                "autonomous_application".toAddonExecutionModelOrNull(),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `capability parsing rejects entire malformed declaration`() {
        assertNull(
            "model.text-generation,INVALID VALUE"
                .toHostCapabilityIdsOrNull(),
        )
    }

    @Test
    fun `capability parsing accepts empty and valid declarations`() {
        assertEquals(
            emptySet<HostCapabilityId>(),
            "".toHostCapabilityIdsOrNull(),
        )
        assertEquals(
            setOf(
                HostCapabilityId("model.text-generation"),
                HostCapabilityId("model.embeddings"),
            ),
            "model.text-generation, model.embeddings"
                .toHostCapabilityIdsOrNull(),
        )
    }
}
