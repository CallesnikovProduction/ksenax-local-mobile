package com.kolesnikovprod.ksetaorch.addons.registry

import dev.openksenax.addons.contract.AddonId
import kotlinx.coroutines.flow.StateFlow

/**
 * Единый источник состояния всей addon-экосистемы OKx.
 *
 * @since 0.3
 */
interface AddonRegistry {

    val state: StateFlow<AddonRegistryState>

    /**
     * Одновременно обновляет catalog и discovery,
     * а затем объединяет их в RegisteredAddon.
     *
     * @since 0.3
     */
    suspend fun refresh(
        mode: AddonRegistryRefreshMode =
            AddonRegistryRefreshMode.DEFAULT,
    ): AddonRegistryState

    /**
     * Гарантирует хотя бы одну завершённую попытку загрузки registry.
     *
     * Нужен exported host services при cold start: caller может отличить
     * ещё не загруженный registry от реального trust-отказа. Конкретная
     * catalog refresh policy остаётся внутри реализации.
     *
     * @since 0.3
     */
    suspend fun ensureInitialized(): AddonRegistryState

    /**
     * Возвращает fail-closed state для command/model authorization.
     *
     * В отличие от наблюдаемого UI snapshot, этот вызов повторно проверяет
     * trust TTL и текущую Android package identity. Его следует выполнять вне
     * main thread.
     *
     * @since 0.3
     */
    fun authorizationState(): AddonRegistryState

    fun find(addonId: AddonId): RegisteredAddon? {
        return state.value.addons
            .firstOrNull { addon -> addon.addonId == addonId }
    }
}

/**
 * Наблюдаемое состояние registry и его источников.
 *
 * @since 0.3
 */
data class AddonRegistryState(
    val addons                            : List<RegisteredAddon> =
        emptyList(),
    val discoveryIssues                   : List<AddonRegistryDiscoveryIssue> =
        emptyList(),
    val isRefreshing                      : Boolean = false,
    val catalogStatus                     : AddonRegistrySourceStatus =
        AddonRegistrySourceStatus.NotLoaded,
    val discoveryStatus                   : AddonRegistrySourceStatus =
        AddonRegistrySourceStatus.NotLoaded,
    val refreshedAtEpochMillis            : Long?   = null,
    val authorizationValidUntilEpochMillis: Long?   = null,
    val isInitialized                     : Boolean = false,
)

/**
 * Registry-owned режим обновления без утечки catalog API наружу.
 *
 * @since 0.3
 */
enum class AddonRegistryRefreshMode {
    DEFAULT,
    CACHE_ONLY,
    FORCE_REMOTE,
}

/**
 * Безопасная для presentation диагностическая проекция отклонённого APK.
 *
 * @since 0.3
 */
data class AddonRegistryDiscoveryIssue(
    val packageName: String?,
    val serviceClassName: String?,
    val kind: AddonRegistryDiscoveryIssueKind,
    val detail: String? = null,
)

/**
 * Стабильная категория discovery-ошибки на registry boundary.
 *
 * @since 0.3
 */
enum class AddonRegistryDiscoveryIssueKind {
    MISSING_SERVICE_INFO,
    SERVICE_NOT_EXPORTED,
    INVALID_MANAGEMENT_PERMISSION,
    MISSING_METADATA,
    INVALID_METADATA,
    SIGNING_CERTIFICATE_UNAVAILABLE,
    MULTIPLE_SERVICES_IN_PACKAGE,
    DUPLICATE_ADDON_ID,
    UNEXPECTED_FAILURE,
}

/**
 * Состояние последнего обращения registry к одному источнику.
 *
 * @since 0.3
 */
sealed interface AddonRegistrySourceStatus {

    data object NotLoaded : AddonRegistrySourceStatus

    data object Fresh : AddonRegistrySourceStatus

    /**
     * Последнее обновление не удалось,
     * но registry использует предыдущий snapshot.
     *
     * @since 0.3
     */
    data class Stale(
        val errorMessage: String?,
        val failureKind: AddonRegistrySourceFailureKind? = null,
    ) : AddonRegistrySourceStatus

    /**
     * Источник не удалось загрузить,
     * и предыдущего snapshot тоже нет.
     *
     * @since 0.3
     */
    data class Unavailable(
        val errorMessage: String?,
        val failureKind: AddonRegistrySourceFailureKind? = null,
    ) : AddonRegistrySourceStatus
}

/**
 * Registry-owned категория ошибки источника без утечки catalog-исключений в UI.
 *
 * @since 0.3
 */
enum class AddonRegistrySourceFailureKind {
    NETWORK_UNAVAILABLE,
    REMOTE_RESPONSE_REJECTED,
    REMOTE_NOT_CONFIGURED,
    CACHE_UNAVAILABLE,
    INVALID_DOCUMENT,
    UNKNOWN,
}
