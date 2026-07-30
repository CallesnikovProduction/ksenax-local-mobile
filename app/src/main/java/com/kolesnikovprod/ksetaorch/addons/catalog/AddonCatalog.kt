package com.kolesnikovprod.ksetaorch.addons.catalog

/**
 * Главный порт чтения каталога аддонов.
 *
 * Остальной код OKx не должен знать:
 * - где расположен внешний registry;
 * - каким HTTP-клиентом он загружается;
 * - как устроен внешний JSON;
 * - где хранится кэш.
 *
 * @since 0.3
 */
interface AddonCatalog {

    /**
     * Возвращает целостный snapshot доступных аддонов.
     *
     * @throws AddonCatalogException если каталог нельзя получить
     * ни выбранным способом, ни через допустимый fallback.
     *
     * @since 0.3
     */
    suspend fun getSnapshot(
        refreshPolicy: CatalogRefreshPolicy =
            CatalogRefreshPolicy.NETWORK_FIRST,
    ): AddonCatalogSnapshot
}
