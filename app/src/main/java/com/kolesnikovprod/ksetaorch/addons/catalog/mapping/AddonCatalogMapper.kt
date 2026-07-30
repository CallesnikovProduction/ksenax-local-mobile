package com.kolesnikovprod.ksetaorch.addons.catalog.mapping

import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSnapshot
import com.kolesnikovprod.ksetaorch.addons.catalog.AddonCatalogSource
import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDocument

/**
 * Преобразует внешний wire-format во внутреннюю модель OKx.
 *
 * @since 0.3
 */
internal interface AddonCatalogMapper {

    fun map(
        document: AddonCatalogDocument,
        source: AddonCatalogSource,
        loadedAtEpochMillis: Long,
    ): AddonCatalogSnapshot
}
