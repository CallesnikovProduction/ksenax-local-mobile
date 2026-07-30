package com.kolesnikovprod.ksetaorch.addons.registry

/**
 * Состояние установки относительно каталога.
 *
 * @since 0.3
 */
sealed interface AddonInstallationState {

    /**
     * PackageManager ещё не был успешно просканирован.
     *
     * Нельзя честно говорить ни "установлен",
     * ни "не установлен".
     *
     * @since 0.3
     */
    data object Unknown : AddonInstallationState

    data object NotInstalled : AddonInstallationState

    data class Installed(
        val versionCode: Long,
        val versionName: String?,
    ) : AddonInstallationState

    data class UpdateAvailable(
        val installedVersionCode: Long,
        val installedVersionName: String?,
        val availableVersionCode: Long,
        val availableVersionName: String,
    ) : AddonInstallationState

    /**
     * На устройстве установлена версия новее той,
     * которая сейчас опубликована в stable-каталоге.
     *
     * Такое возможно для dev/beta-сборки.
     *
     * @since 0.3
     */
    data class InstalledNewerThanCatalog(
        val installedVersionCode: Long,
        val installedVersionName: String?,
        val catalogVersionCode: Long,
    ) : AddonInstallationState
}
