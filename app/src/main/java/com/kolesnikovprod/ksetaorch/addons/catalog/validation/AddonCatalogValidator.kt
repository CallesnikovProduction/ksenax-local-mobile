package com.kolesnikovprod.ksetaorch.addons.catalog.validation

import com.kolesnikovprod.ksetaorch.addons.catalog.serialization.AddonCatalogDocument

/**
 * Одна конкретная проблема во внешнем registry.
 *
 * @since 0.3
 */
data class CatalogViolation(
    val path: String,
    val message: String,
)

/**
 * Проверяет семантическую корректность decoded-документа.
 *
 * JSON может успешно распарситься, но при этом содержать:
 * - повторяющиеся addonId;
 * - неверный SHA-256;
 * - пустой packageName;
 * - неподдерживаемую версию схемы.
 *
 * @since 0.3
 */
internal interface AddonCatalogValidator {

    fun validate(
        document: AddonCatalogDocument,
    ): List<CatalogViolation>
}
