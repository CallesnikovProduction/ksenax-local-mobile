package com.kolesnikovprod.ksetaorch.addons.catalog.cache

/**
 * Сохранённая необработанная версия registry JSON.
 *
 * @since 0.3
 */
data class CachedAddonCatalog(
    val rawDocument: String,
    val storedAtEpochMillis: Long,
)

/**
 * Порт локального кэша.
 *
 * Кэш хранит именно raw JSON, а не доменные объекты.
 * Благодаря этому документ каждый раз проходит актуальную
 * validation/mapping-цепочку.
 *
 * @since 0.3
 */
interface AddonCatalogCache {

    suspend fun read(): CachedAddonCatalog?

    suspend fun write(rawDocument: String)

    suspend fun clear()
}
