package com.kolesnikovprod.ksetaorch.addons.catalog

import com.kolesnikovprod.ksetaorch.addons.catalog.validation.CatalogViolation

/**
 * Типизированные ошибки каталога.
 *
 * Они позволяют registry/UI отличить сетевую проблему
 * от повреждённого документа или отсутствующего кэша.
 *
 * @since 0.3
 */
sealed class AddonCatalogException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * Сетевая конфигурация каталога ещё не задана.
     *
     * @since 0.3
     */
    class RemoteSourceNotConfigured : AddonCatalogException(
        message = "Remote addon catalog source is not configured",
    )

    /**
     * Настроенный удалённый источник недоступен.
     *
     * @since 0.3
     */
    class NetworkUnavailable(
        cause: Throwable,
    ) : AddonCatalogException(
        message = "Addon catalog network source is unavailable",
        cause = cause,
    )

    /**
     * Remote source ответил, но его HTTP/transport-протокол отклонён.
     *
     * Это не отсутствие подключения: presentation не должна показывать
     * offline-сообщение для HTTP error, unsafe redirect или слишком большого
     * ответа.
     *
     * @since 0.3
     */
    class RemoteResponseRejected(
        cause: Throwable,
    ) : AddonCatalogException(
        message = "Addon catalog remote response was rejected",
        cause = cause,
    )

    /**
     * Валидный кэш каталога отсутствует.
     *
     * @since 0.3
     */
    class CacheUnavailable : AddonCatalogException(
        message = "Cached addon catalog is unavailable",
    )

    /**
     * JSON не соответствует wire-format каталога.
     *
     * @since 0.3
     */
    class DecodeFailed(
        cause: Throwable,
    ) : AddonCatalogException(
        message = "Addon catalog JSON cannot be decoded",
        cause = cause,
    )

    /**
     * Документ декодирован, но нарушает catalog-инварианты.
     *
     * @since 0.3
     */
    class InvalidDocument(
        val violations: List<CatalogViolation>,
    ) : AddonCatalogException(
        message = buildString {
            append("Addon catalog is invalid: ")

            append(
                violations.joinToString { violation ->
                    "${violation.path}: ${violation.message}"
                },
            )
        },
    )
}
