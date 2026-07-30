package com.kolesnikovprod.ksetaorch.addons.catalog.validation

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonChannel
import com.kolesnikovprod.ksetaorch.addons.catalog.mapping.DefaultAddonCatalogMapper
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSource
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDocument
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogEntryDocument
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCompatibilityDocument
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonPresentationDocument
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonReleaseDocument
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonSecurityDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAddonCatalogValidatorTest {

    private val validator = DefaultAddonCatalogValidator(
        supportedSchemaVersion = 1,
        expectedChannel = AddonChannel.STABLE,
    )

    @Test
    fun validDocumentCoversEveryMapperConversion() {
        val document = validDocument()

        assertTrue(validator.validate(document).isEmpty())

        val snapshot = DefaultAddonCatalogMapper().map(
            document = document,
            source = AddonCatalogSource.NETWORK,
            loadedAtEpochMillis = 2L,
        )

        assertEquals(
            "dev.openksenax.addon.sample",
            snapshot.entries.single().addonId.value,
        )
        assertEquals(
            setOf("model.text-generation"),
            snapshot.entries.single()
                .requiredHostCapabilities
                .map { capability -> capability.value }
                .toSet(),
        )
        assertEquals(
            "https://example.invalid/banner.png",
            snapshot.entries.single().presentation.banner?.url,
        )
        assertEquals(
            SHA_256,
            snapshot.entries.single().presentation.banner?.sha256,
        )
    }

    @Test
    fun rejectsValuesThatWouldBreakOrWeakenDomainMapping() {
        val invalidEntry = validEntry().copy(
            addonId = "Invalid Addon Id",
            packageName = "InvalidPackage",
            displayName = " ",
            shortDescription = "",
            executionModel = "in_process_dex",
            release = validEntry().release.copy(
                versionCode = 0L,
                versionName = "",
                apkUrl = "http://example.invalid/addon.apk",
                apkSha256 = "not-a-sha",
                sizeBytes = 0L,
            ),
            compatibility = validEntry().compatibility.copy(
                protocolVersion = 0,
                managementApiVersion = 0,
                minimumHostApi = 0,
                minimumAndroidSdk = 0,
            ),
            requiredHostCapabilities =
                listOf("Model.text-generation"),
            presentation = AddonPresentationDocument(
                iconUrl = "http://example.invalid/icon.png",
                repositoryUrl =
                    "https://example.invalid/repository#fragment",
                bannerUrl = "http://example.invalid/banner.png",
                bannerSha256 = "not-a-sha",
            ),
            security = AddonSecurityDocument(
                official = false,
                signingCertificateSha256 = "not-a-sha",
            ),
        )

        val violationPaths = validator.validate(
            validDocument().copy(addons = listOf(invalidEntry)),
        ).map { violation -> violation.path }.toSet()

        val expectedPaths = setOf(
            "addons[0].addonId",
            "addons[0].packageName",
            "addons[0].displayName",
            "addons[0].shortDescription",
            "addons[0].executionModel",
            "addons[0].release.versionCode",
            "addons[0].release.versionName",
            "addons[0].release.apkUrl",
            "addons[0].release.apkSha256",
            "addons[0].release.sizeBytes",
            "addons[0].compatibility.protocolVersion",
            "addons[0].compatibility.managementApiVersion",
            "addons[0].compatibility.minimumHostApi",
            "addons[0].compatibility.minimumAndroidSdk",
            "addons[0].requiredHostCapabilities[0]",
            "addons[0].presentation.iconUrl",
            "addons[0].presentation.repositoryUrl",
            "addons[0].presentation.bannerUrl",
            "addons[0].presentation.bannerSha256",
            "addons[0].security.signingCertificateSha256",
        )

        assertTrue(violationPaths.containsAll(expectedPaths))
    }

    @Test
    fun rejectsDuplicateAddonIdentityAndCapabilities() {
        val duplicatedEntry = validEntry().copy(
            requiredHostCapabilities = listOf(
                "model.text-generation",
                "model.text-generation",
            ),
        )
        val violations = validator.validate(
            validDocument().copy(
                addons = listOf(
                    duplicatedEntry,
                    duplicatedEntry,
                ),
            ),
        )

        assertTrue(
            violations.any { violation ->
                violation.message.startsWith("Duplicate addonId")
            },
        )
        assertTrue(
            violations.any { violation ->
                violation.message.startsWith("Duplicate packageName")
            },
        )
        assertEquals(
            2,
            violations.count { violation ->
                violation.message.startsWith("Duplicate capability")
            },
        )
    }

    @Test
    fun requiresBannerUrlAndSha256AsOneAtomicDescriptor() {
        val onlyUrl = validEntry().copy(
            presentation = validEntry().presentation.copy(
                bannerSha256 = null,
            ),
        )
        val onlySha = validEntry().copy(
            presentation = validEntry().presentation.copy(
                bannerUrl = null,
            ),
        )

        listOf(onlyUrl, onlySha).forEach { entry ->
            assertTrue(
                validator.validate(
                    validDocument().copy(addons = listOf(entry)),
                ).any { violation ->
                    violation.path == "addons[0].presentation"
                },
            )
        }
    }

    @Test
    fun rejectsPackagesThatWouldShareOneStorageDirectory() {
        val first = validEntry().copy(
            addonId = "dev.openksenax.addon.first",
            packageName = "dev.openksenax.first.shared",
        )
        val second = validEntry().copy(
            addonId = "dev.openksenax.addon.second",
            packageName = "dev.openksenax.second.shared",
        )

        val violations = validator.validate(
            validDocument().copy(addons = listOf(first, second)),
        )

        assertTrue(
            violations.any { violation ->
                violation.message ==
                    "Duplicate addon storage directory: shared"
            },
        )
    }

    private fun validDocument(): AddonCatalogDocument {
        return AddonCatalogDocument(
            schemaVersion = 1,
            channel = "stable",
            generatedAtEpochMillis = 1L,
            addons = listOf(validEntry()),
        )
    }

    private fun validEntry(): AddonCatalogEntryDocument {
        return AddonCatalogEntryDocument(
            addonId = "dev.openksenax.addon.sample",
            packageName = "dev.openksenax.addon.sample",
            displayName = "Sample",
            shortDescription = "Sample addon",
            executionModel = "autonomous_application",
            release = AddonReleaseDocument(
                versionCode = 1L,
                versionName = "1.0",
                apkUrl = "https://example.invalid/sample.apk",
                apkSha256 = SHA_256,
                sizeBytes = 1L,
            ),
            compatibility = AddonCompatibilityDocument(
                protocolVersion = 1,
                managementApiVersion = 1,
                minimumHostApi = 1,
                minimumAndroidSdk = 28,
            ),
            requiredHostCapabilities =
                listOf("model.text-generation"),
            presentation = AddonPresentationDocument(
                iconUrl = "https://example.invalid/icon.png",
                repositoryUrl = "https://example.invalid/repository",
                bannerUrl = "https://example.invalid/banner.png",
                bannerSha256 = SHA_256,
            ),
            security = AddonSecurityDocument(
                official = true,
                signingCertificateSha256 = SHA_256,
            ),
        )
    }

    private companion object {
        val SHA_256: String = "A".repeat(64)
    }
}
