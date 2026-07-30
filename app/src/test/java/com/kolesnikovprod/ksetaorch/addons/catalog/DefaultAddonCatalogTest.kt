package com.kolesnikovprod.ksetaorch.addons.catalog

import com.kolesnikovprod.ksetaorch.addons.catalog.cache.AddonCatalogCache
import com.kolesnikovprod.ksetaorch.addons.catalog.cache.CachedAddonCatalog
import com.kolesnikovprod.ksetaorch.addons.catalog.mapping.DefaultAddonCatalogMapper
import com.kolesnikovprod.ksetaorch.addons.catalog.remote.AddonCatalogRemoteSource
import com.kolesnikovprod.ksetaorch.addons.catalog.remote.AddonCatalogRemoteResponseException
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDecoder
import com.kolesnikovprod.ksetaorch.addons.catalog.validation.DefaultAddonCatalogValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultAddonCatalogTest {

    @Test
    fun networkFirstUsesValidCacheWhenRemoteIsNotConfigured() =
        runBlocking {
            val catalog = createCatalog(
                remoteSource = null,
                cache = InMemoryCache(VALID_DOCUMENT),
            )

            val snapshot = catalog.getSnapshot(
                CatalogRefreshPolicy.NETWORK_FIRST,
            )

            assertEquals(AddonCatalogSource.CACHE, snapshot.source)
            assertEquals(1L, snapshot.loadedAtEpochMillis)
        }

    @Test
    fun forceNetworkFailsWhenRemoteIsNotConfigured() {
        val error = assertThrows(
            AddonCatalogException.RemoteSourceNotConfigured::class.java,
        ) {
            runBlocking {
                createCatalog(
                    remoteSource = null,
                    cache = InMemoryCache(null),
                ).getSnapshot(CatalogRefreshPolicy.FORCE_NETWORK)
            }
        }

        assertEquals(
            "Remote addon catalog source is not configured",
            error.message,
        )
    }

    @Test
    fun remoteCancellationIsNeverWrappedAsNetworkFailure() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                createCatalog(
                    remoteSource = AddonCatalogRemoteSource {
                        throw CancellationException("cancelled")
                    },
                    cache = InMemoryCache(null),
                ).getSnapshot(CatalogRefreshPolicy.FORCE_NETWORK)
            }
        }
    }

    @Test
    fun rejectedRemoteResponseIsNotReportedAsNoInternet() {
        assertThrows(
            AddonCatalogException.RemoteResponseRejected::class.java,
        ) {
            runBlocking {
                createCatalog(
                    remoteSource = AddonCatalogRemoteSource {
                        throw AddonCatalogRemoteResponseException(
                            "HTTP 404",
                        )
                    },
                    cache = InMemoryCache(null),
                ).getSnapshot(CatalogRefreshPolicy.FORCE_NETWORK)
            }
        }
    }

    private fun createCatalog(
        remoteSource: AddonCatalogRemoteSource?,
        cache: AddonCatalogCache,
    ): DefaultAddonCatalog {
        return DefaultAddonCatalog(
            remoteSource = remoteSource,
            cache = cache,
            decoder = AddonCatalogDecoder(),
            validator = DefaultAddonCatalogValidator(
                supportedSchemaVersion = 1,
                expectedChannel = AddonChannel.STABLE,
            ),
            mapper = DefaultAddonCatalogMapper(),
            currentTimeMillis = { 2L },
        )
    }

    private class InMemoryCache(
        rawDocument: String?,
    ) : AddonCatalogCache {

        private var rawDocument: String? = rawDocument

        override suspend fun read(): CachedAddonCatalog? {
            return rawDocument?.let { value ->
                CachedAddonCatalog(
                    rawDocument = value,
                    storedAtEpochMillis = 1L,
                )
            }
        }

        override suspend fun write(rawDocument: String) {
            this.rawDocument = rawDocument
        }

        override suspend fun clear() {
            rawDocument = null
        }
    }

    private companion object {
        val VALID_DOCUMENT: String =
            """
            {
              "schemaVersion": 1,
              "channel": "stable",
              "generatedAtEpochMillis": 1,
              "addons": []
            }
            """.trimIndent()
    }
}
