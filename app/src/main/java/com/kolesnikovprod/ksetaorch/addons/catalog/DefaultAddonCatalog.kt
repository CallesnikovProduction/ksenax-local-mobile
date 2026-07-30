package com.kolesnikovprod.ksetaorch.addons.catalog

import com.kolesnikovprod.ksetaorch.addons.catalog.cache.AddonCatalogCache
import com.kolesnikovprod.ksetaorch.addons.catalog.mapping.AddonCatalogMapper
import com.kolesnikovprod.ksetaorch.addons.catalog.remote.AddonCatalogRemoteSource
import com.kolesnikovprod.ksetaorch.addons.catalog.remote.AddonCatalogRemoteResponseException
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDecoder
import com.kolesnikovprod.ksetaorch.addons.catalog.validation.AddonCatalogValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Главная реализация каталога.
 *
 * Она координирует:
 * - сеть;
 * - кэш;
 * - декодирование;
 * - validation;
 * - mapping.
 *
 * Она не знает ничего об установленных APK и UI.
 *
 * @since 0.3
 */
internal class DefaultAddonCatalog(
    private val remoteSource: AddonCatalogRemoteSource?,
    private val cache: AddonCatalogCache,
    private val decoder: AddonCatalogDecoder,
    private val validator: AddonCatalogValidator,
    private val mapper: AddonCatalogMapper,
    private val currentTimeMillis: () -> Long =
        System::currentTimeMillis,
    private val computationDispatcher: CoroutineDispatcher =
        Dispatchers.Default,
) : AddonCatalog {

    override suspend fun getSnapshot(
        refreshPolicy: CatalogRefreshPolicy,
    ): AddonCatalogSnapshot {
        return when (refreshPolicy) {
            CatalogRefreshPolicy.CACHE_FIRST ->
                loadCacheOrNull() ?: loadNetwork()

            CatalogRefreshPolicy.NETWORK_FIRST ->
                loadNetworkWithCacheFallback()

            CatalogRefreshPolicy.FORCE_NETWORK ->
                loadNetwork()

            CatalogRefreshPolicy.CACHE_ONLY ->
                loadCacheOrNull()
                    ?: throw AddonCatalogException.CacheUnavailable()
        }
    }

    private suspend fun loadNetworkWithCacheFallback(): AddonCatalogSnapshot {
        return try {
            loadNetwork()
        } catch (networkFailure: AddonCatalogException) {
            loadCacheOrNull() ?: throw networkFailure
        }
    }

    private suspend fun loadNetwork(): AddonCatalogSnapshot {
        val configuredRemoteSource = remoteSource
            ?: throw AddonCatalogException.RemoteSourceNotConfigured()

        val rawDocument = try {
            configuredRemoteSource.fetchRawDocument()
        } catch (error: CancellationException) {
            throw error
        } catch (error: AddonCatalogRemoteResponseException) {
            throw AddonCatalogException.RemoteResponseRejected(
                cause = error,
            )
        } catch (error: Exception) {
            throw AddonCatalogException.NetworkUnavailable(
                cause = error,
            )
        }

        val snapshot = parse(
            rawDocument = rawDocument,
            source = AddonCatalogSource.NETWORK,
            loadedAtEpochMillis = currentTimeMillis(),
        )

        // Ошибка записи кэша не должна уничтожать
        // успешно загруженный сетевой каталог.
        try {
            cache.write(rawDocument)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Сетевой snapshot уже проверен и остаётся пригодным.
        }

        return snapshot
    }

    private suspend fun loadCacheOrNull(): AddonCatalogSnapshot? {
        val cached = try {
            cache.read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            parse(
                rawDocument = cached.rawDocument,
                source = AddonCatalogSource.CACHE,
                loadedAtEpochMillis = cached.storedAtEpochMillis,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: AddonCatalogException) {
            try {
                cache.clear()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Повреждённый кэш просто больше не используется.
            }

            null
        }
    }

    private suspend fun parse(
        rawDocument: String,
        source: AddonCatalogSource,
        loadedAtEpochMillis: Long,
    ): AddonCatalogSnapshot {
        return withContext(computationDispatcher) {
            val document = try {
                decoder.decode(rawDocument)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw AddonCatalogException.DecodeFailed(
                    cause = error,
                )
            }

            val violations = validator.validate(document)

            if (violations.isNotEmpty()) {
                throw AddonCatalogException.InvalidDocument(
                    violations = violations,
                )
            }

            mapper.map(
                document = document,
                source = source,
                loadedAtEpochMillis = loadedAtEpochMillis,
            )
        }
    }
}
