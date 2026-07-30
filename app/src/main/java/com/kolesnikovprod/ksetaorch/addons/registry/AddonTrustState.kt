package com.kolesnikovprod.ksetaorch.addons.registry

/**
 * Результат сравнения установленного APK
 * с identity, опубликованной в каталоге.
 *
 * @since 0.3
 */
sealed interface AddonTrustState {

    data object NotInstalled : AddonTrustState

    /**
     * APK обнаружен, но каталог недоступен и доверие нельзя доказать.
     *
     * @since 0.3
     */
    data object CatalogUnavailable : AddonTrustState

    /**
     * Аддон присутствует в каталоге и подписан
     * ожидаемым сертификатом.
     *
     * @since 0.3
     */
    data object TrustedOfficial : AddonTrustState

    /**
     * Запись есть в каталоге, но она не помечена официальной.
     *
     * В будущем здесь может появиться user trust flow.
     *
     * @since 0.3
     */
    data object CataloguedButNotOfficial : AddonTrustState

    /**
     * APK объявляет себя OpenKsenax-аддоном,
     * но в каталоге записи о нём нет.
     *
     * @since 0.3
     */
    data object UnlistedInstalled : AddonTrustState

    data class SignatureMismatch(
        val expectedSha256: String,
        val actualSha256: Set<String>,
    ) : AddonTrustState
}
