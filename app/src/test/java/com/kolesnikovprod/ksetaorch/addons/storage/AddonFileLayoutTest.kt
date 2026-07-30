package com.kolesnikovprod.ksetaorch.addons.storage

import dev.openksenax.addons.contract.AddonId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonFileLayoutTest {

    private val layout = AddonFileLayout(
        File("build/test-addon-storage"),
    )

    @Test
    fun usesLastPackageSegmentForInstalledDirectory() {
        assertEquals(
            "noradar",
            layout.packageDirectory(
                "com.callesnikovprod.noradar",
            ).name,
        )
    }

    @Test
    fun keepsDownloadsInsideTemporaryDirectory() {
        val apk = layout.temporaryApk(
            AddonId("dev.openksenax.addon.no-radar"),
            versionCode = 2L,
        )
        val banner = layout.temporaryBanner(
            AddonId("dev.openksenax.addon.no-radar"),
            sha256 = "A".repeat(64),
        )

        assertEquals("temp", apk.parentFile?.name)
        assertEquals("temp", banner.parentFile?.name)
        assertTrue(apk.name.endsWith("-2.apk"))
        assertTrue(banner.name.endsWith(".banner"))
        assertTrue(
            layout.isDownloadArtifactFor(
                AddonId("dev.openksenax.addon.no-radar"),
                layout.rootDirectory.resolve(
                    "dev.openksenax.addon.no-radar-2.apk",
                ),
            ),
        )
    }
}
