package com.kolesnikovprod.ksetaorch.addons.download

import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AddonDownloadUrlPolicyTest {

    private val policy = AddonDownloadUrlPolicy(
        setOf(
            "github.com",
            "release-assets.githubusercontent.com",
        ),
    )

    @Test
    fun `accepts approved HTTPS release URL`() {
        assertNotNull(
            policy.parse(
                "https://github.com/org/repo/releases/download/v1/addon.apk",
            ),
        )
    }

    @Test
    fun `rejects unsafe initial URLs`() {
        assertNull(policy.parse("http://github.com/addon.apk"))
        assertNull(policy.parse("https://user@github.com/addon.apk"))
        assertNull(policy.parse("https://github.com/addon.apk#fragment"))
        assertNull(
            policy.parse("https://github.com.attacker.invalid/addon.apk"),
        )
    }

    @Test
    fun `allows approved redirect and rejects foreign host`() {
        val current = Url(
            "https://github.com/org/repo/releases/download/v1/addon.apk",
        )
        val accepted = policy.resolveRedirect(
            current,
            "https://release-assets.githubusercontent.com/object",
        )

        assertEquals(
            "release-assets.githubusercontent.com",
            accepted?.host,
        )
        assertNull(
            policy.resolveRedirect(
                current,
                "https://example.invalid/addon.apk",
            ),
        )
    }
}
