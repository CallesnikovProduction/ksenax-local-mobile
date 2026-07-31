package com.kolesnikovprod.ksetaorch.addons.presentation

import android.graphics.Bitmap
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallProgress
import dev.openksenax.addons.contract.AddonId

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
    val installState: AddonInstallUiState =
        AddonInstallUiState.Idle,
    val isInstallConfirmationVisible: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateMessage: String? = null,
    val banner: AddonBannerUiState,
) {
    val isInstallInProgress: Boolean
        get() = installState is AddonInstallUiState.Active

    val hasInstallFailure: Boolean
        get() = installState is AddonInstallUiState.Failed
}

/**
 * Presentation-состояние одной попытки установки.
 *
 * Download-domain публикует фактические байты и стадии, а этот тип добавляет
 * только UX-проекцию качества соединения и устойчивый отказ, который
 * пользователь явно сбрасывает через REFRESH.
 *
 * @since 0.3
 */
internal sealed interface AddonInstallUiState {

    data object Idle : AddonInstallUiState

    data class Active(
        val progress: AddonInstallProgress,
        val connectionQuality: AddonInstallConnectionQuality =
            AddonInstallConnectionQuality.NORMAL,
    ) : AddonInstallUiState

    data class Failed(
        val reason: AddonInstallFailureUiReason,
    ) : AddonInstallUiState
}

/**
 * Визуальная оценка соединения активной APK-загрузки.
 *
 * @since 0.3
 */
internal enum class AddonInstallConnectionQuality {
    NORMAL,
    WEAK,
}

/**
 * Устойчивые причины отказа, для которых карточка показывает собственный
 * recovery UX вместо глобального сообщения.
 *
 * @since 0.3
 */
internal enum class AddonInstallFailureUiReason {
    NO_INTERNET,
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
