package com.kolesnikovprod.ksetaorch.addons.catalog.remote

/**
 * Низкоуровневый источник удалённого JSON.
 *
 * Он ничего не знает о доменных моделях аддонов.
 * Его задача — получить строку документа.
 *
 * @since 0.3
 */
fun interface AddonCatalogRemoteSource {

    suspend fun fetchRawDocument(): String
}
