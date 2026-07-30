package com.kolesnikovprod.ksetaorch.addons.catalog

/**
 * Канал публикации внешнего каталога.
 *
 * @since 0.3
 */
enum class AddonChannel {
    STABLE,
    BETA,
    COMMUNITY,
}

/**
 * Фактический источник данных конкретного snapshot.
 *
 * @since 0.3
 */
enum class AddonCatalogSource {
    NETWORK,
    CACHE,
}

/**
 * Проверенный и преобразованный срез внешнего каталога.
 *
 * Snapshot не содержит сведений о фактически установленных APK.
 * Их добавляет только registry-контур.
 *
 * @since 0.3
 */
data class AddonCatalogSnapshot(
    val schemaVersion: Int,
    val channel: AddonChannel,
    val generatedAtEpochMillis: Long,
    val loadedAtEpochMillis: Long,
    val entries: List<AddonCatalogEntry>,
    val source: AddonCatalogSource,
)
