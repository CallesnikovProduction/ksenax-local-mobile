package com.kolesnikovprod.ksetaorch.addons.catalog

/**
 * Определяет, откуда каталог должен быть получен.
 *
 * @since 0.3
 */
enum class CatalogRefreshPolicy {

    /**
     * Сначала читаем кэш.
     * Если кэша нет или он повреждён — обращаемся к сети.
     *
     * @since 0.3
     */
    CACHE_FIRST,

    /**
     * Сначала обращаемся к сети.
     * При сетевой ошибке используем корректный кэш.
     *
     * @since 0.3
     */
    NETWORK_FIRST,

    /**
     * Обязательно загружаем каталог из сети.
     * Кэш не используется как fallback.
     *
     * @since 0.3
     */
    FORCE_NETWORK,

    /**
     * Используем только ранее сохранённый кэш.
     *
     * @since 0.3
     */
    CACHE_ONLY,
}
