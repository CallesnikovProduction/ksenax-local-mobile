package com.kolesnikovprod.ksetaorch.addons.catalog.serialization

import kotlinx.serialization.json.Json

/**
 * Изолирует kotlinx.serialization от основного catalog-кода.
 *
 * @since 0.3
 */
internal class AddonCatalogDecoder(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    },
) {

    fun decode(rawDocument: String): AddonCatalogDocument {
        return json.decodeFromString(
            deserializer = AddonCatalogDocument.serializer(),
            string = rawDocument,
        )
    }
}
