package com.kolesnikovprod.ksetaorch.addons.presentation

import android.graphics.Bitmap
import dev.openksenax.addons.contract.AddonId
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallStage

/**
 * Готовая к отображению карточка зарегистрированного аддона.
 *
 * Presentation не вычисляет trust или compatibility: текст и доступные
 * действия получены из verdict реестра.
 *
 * @since 0.3
 */
internal data class AddonCardUiModel(
    val addonId: AddonId,
    val packageName: String,
    val title: String,
    val description: String,
    val versionLabel: String,
    val installationLabel: String,
    val trustLabel: String,
    val compatibilityLabel: String,
    val capabilityLabels: List<String>,
    val isInstalled: Boolean,
    val canOpen: Boolean,
    val canInstall: Boolean,
    val canManageRuntime: Boolean,
    val canUninstall: Boolean,
    val installStage: AddonInstallStage? = null,
    val installProgress: Float? = null,
    val installBytesPerSecond: Long? = null,
    val isWeakConnection: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateMessage: String? = null,
    val banner: AddonBannerUiState,
) {
    val isInstallInProgress: Boolean
        get() = installStage != null
}

/**
 * Состояние проверяемой кликабельной шапки одного аддона.
 *
 * `sha256` связывает асинхронно загруженный bitmap с текущим registry
 * descriptor и не позволяет показать изображение прошлой версии.
 *
 * @since 0.3
 */
internal sealed interface AddonBannerUiState {

    val sha256: String?

    data class Loading(
        override val sha256: String,
    ) : AddonBannerUiState

    data class Ready(
        override val sha256: String,
        val bitmap: Bitmap,
    ) : AddonBannerUiState

    data class Unavailable(
        override val sha256: String?,
    ) : AddonBannerUiState
}
