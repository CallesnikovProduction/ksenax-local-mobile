package com.kolesnikovprod.ksetaorch.download.domain.data

/** Причина отказа установки без утечки HTTP URL или credentials. @since 0.4 */
enum class KsenaxInstallFailure {
    NETWORK, NOT_FOUND, AUTHORIZATION, RATE_LIMITED, SERVER,
    INSUFFICIENT_STORAGE, STORAGE, INTEGRITY, MISSING_TASK, SOURCE_CHANGED, UNKNOWN;

    /** Автоматический повтор уместен только для временной транспортной ошибки. @since 0.4 */
    val isRetryable: Boolean
        get() = this == NETWORK || this == RATE_LIMITED || this == SERVER
}
