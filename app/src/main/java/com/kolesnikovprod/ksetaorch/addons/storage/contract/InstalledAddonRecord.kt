package com.kolesnikovprod.ksetaorch.addons.storage.contract

import dev.openksenax.addons.contract.AddonId
import kotlinx.serialization.Serializable

/**
 * Сериализуемая локальная metadata-проекция установленного аддона.
 *
 * Запись обслуживает UI и host-owned файловое состояние. Она не является
 * источником решений об installation, trust, compatibility или capability
 * grants: эти решения принадлежат addon registry.
 *
 * Повреждённая запись может быть восстановлена из PackageManager и catalog,
 * поэтому потребители должны трактовать её как локальную проекцию, а не как
 * авторизационный контракт.
 *
 * @since 0.4
 */
@Serializable
internal data class InstalledAddonRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val addonId: String,
    val packageName: String,
    val displayName: String,
    val shortDescription: String,
    val fullDescription: String?,
    val versionCode: Long,
    val versionName: String?,
    val installedAtEpochMillis: Long,
    val lastUpdatedAtEpochMillis: Long,
    val repositoryUrl: String?,
    val requiredHostCapabilities: List<String>,
    val apkSha256: String?,
    val bannerSha256: String?,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
        require(runCatching { AddonId(addonId) }.isSuccess)
        require(packageName.isNotBlank())
        require(displayName.isNotBlank())
        require(versionCode > 0L)
        require(installedAtEpochMillis >= 0L)
        require(lastUpdatedAtEpochMillis >= 0L)
    }

    internal companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
