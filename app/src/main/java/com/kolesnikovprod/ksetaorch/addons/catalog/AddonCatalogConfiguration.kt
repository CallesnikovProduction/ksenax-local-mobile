package com.kolesnikovprod.ksetaorch.addons.catalog

/**
 * Конфигурация конкретного канала каталога.
 *
 * В будущем OKx сможет создать отдельные экземпляры
 * для stable, beta и community-каналов.
 *
 * [registryUrl] может быть `null`, пока удалённый registry не подключён.
 * Такая конфигурация не инициирует сеть и не притворяется рабочей.
 *
 * @since 0.3
 */
data class AddonCatalogConfiguration(
    val registryUrl: String?,
    val supportedSchemaVersion: Int,
    val expectedChannel: AddonChannel,
    val cacheFileName: String,
    val maximumTrustedCacheAgeMillis: Long,
) {

    init {
        require(registryUrl == null || registryUrl.isNotBlank()) {
            "registryUrl must be null or non-blank"
        }
        require(supportedSchemaVersion > 0) {
            "supportedSchemaVersion must be greater than zero"
        }
        require(cacheFileName.isNotBlank()) {
            "cacheFileName must not be blank"
        }
        require(cacheFileName == java.io.File(cacheFileName).name) {
            "cacheFileName must be a file name, not a path"
        }
        require(maximumTrustedCacheAgeMillis > 0L) {
            "maximumTrustedCacheAgeMillis must be greater than zero"
        }
    }

    companion object {

        /**
         * Создаёт конфигурацию stable-канала с явно заданным registry.
         *
         * @since 0.3
         */
        fun stable(
            registryUrl: String,
        ): AddonCatalogConfiguration {
            return AddonCatalogConfiguration(
                registryUrl = registryUrl,
                supportedSchemaVersion = 1,
                expectedChannel = AddonChannel.STABLE,
                cacheFileName =
                    "stable-${registryUrl.cacheNamespace()}.json",
                maximumTrustedCacheAgeMillis =
                    DEFAULT_TRUSTED_CACHE_AGE_MILLIS,
            )
        }

        /**
         * Создаёт честную offline-конфигурацию stable-канала.
         *
         * Она позволяет прочитать ранее проверенный кэш, но сетевые
         * политики без кэша завершаются
         * [AddonCatalogException.RemoteSourceNotConfigured].
         *
         * @since 0.3
         */
        fun offline(): AddonCatalogConfiguration {
            return AddonCatalogConfiguration(
                registryUrl = null,
                supportedSchemaVersion = 1,
                expectedChannel = AddonChannel.STABLE,
                cacheFileName = "stable-offline.json",
                maximumTrustedCacheAgeMillis =
                    DEFAULT_TRUSTED_CACHE_AGE_MILLIS,
            )
        }

        private const val DEFAULT_TRUSTED_CACHE_AGE_MILLIS =
            24L * 60L * 60L * 1_000L

        private fun String.cacheNamespace(): String {
            val digest = java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(toByteArray(Charsets.UTF_8))
            return digest
                .take(8)
                .joinToString(separator = "") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
        }
    }
}
