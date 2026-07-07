package com.kolesnikovprod.ksetaorch.ui.helpers

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxModelDownloadOverlayState

/**
 * Применяет install-состояния к [KsenaxMainUiState].
 *
 * Объект не запускает скачивание, не отменяет загрузки и не работает с файловой системой.
 * Его задача — только аккуратно обновлять UI-состояние главного экрана, связанное
 * с overlay установки моделей Gemma/Vosk.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
object KsenaxInstallUiStateReducer {

    /**
     * Возвращает install snapshot для указанной цели установки.
     *
     * Метод нужен, чтобы ViewModel не знала напрямую, из какого поля брать snapshot:
     * [KsenaxMainUiState.gemmaInstallSnapshot] или [KsenaxMainUiState.voskInstallSnapshot].
     *
     * @param uiState текущее состояние главного экрана.
     * @param target цель установки модели.
     * @return snapshot установки, соответствующий указанной цели.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun snapshotFor(
        uiState: KsenaxMainUiState,
        target:  KsenaxInstallOverlayTarget,
    ): KsenaxInstallSnapshot {
        return when (target) {
            KsenaxInstallOverlayTarget.Gemma4E2B
                -> uiState.gemmaInstallSnapshot
            KsenaxInstallOverlayTarget.FunctionGemma270M
                -> uiState.functionGemmaInstallSnapshot
            KsenaxInstallOverlayTarget.VoskSmallRu
                -> uiState.voskInstallSnapshot
        }
    }

    fun withSnapshot(
        uiState:  KsenaxMainUiState,
        target:   KsenaxInstallOverlayTarget,
        snapshot: KsenaxInstallSnapshot,
    ): KsenaxMainUiState {
        return when (target) {
            KsenaxInstallOverlayTarget.Gemma4E2B -> {
                uiState.copy(gemmaInstallSnapshot = snapshot)
            }
            KsenaxInstallOverlayTarget.FunctionGemma270M -> {
                uiState.copy(functionGemmaInstallSnapshot = snapshot)
            }
            KsenaxInstallOverlayTarget.VoskSmallRu -> {
                uiState.copy(voskInstallSnapshot = snapshot)
            }
        }
    }

    /**
     * Переводит overlay установки в состояние распаковки модели.
     *
     * Сейчас это особенно полезно для Vosk, где после скачивания может быть отдельная
     * стадия подготовки/распаковки файлов.
     *
     * @param uiState текущее состояние главного экрана.
     * @return состояние с unpacking overlay.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun showUnpacking(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(
            modelDownloadOverlayState = KsenaxModelDownloadOverlayState.Unpacking,
            isCancelDownloadConfirmationVisible = false,
        )
    }

    /**
     * Переводит overlay установки в состояние активного скачивания.
     *
     * Метод также прячет подтверждение отмены, потому что при нормальном переходе
     * к скачиванию confirmation-dialog не должен оставаться открытым.
     *
     * @param uiState текущее состояние главного экрана.
     * @return состояние с активным downloading overlay.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun showDownloading(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(
            modelDownloadOverlayState = KsenaxModelDownloadOverlayState.Downloading,
            isCancelDownloadConfirmationVisible = false,
        )
    }

    /**
     * Переводит overlay установки в состояние предложения скачать модель.
     *
     * Используется, когда модель ещё не установлена, а пользователю нужно показать
     * карточку/диалог с предложением загрузки.
     *
     * @param uiState текущее состояние главного экрана.
     * @param target модель, для которой нужно показать предложение установки.
     * @return состояние с открытым install-offer overlay.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun showModelOffer(
        uiState: KsenaxMainUiState,
        target: KsenaxInstallOverlayTarget,
    ): KsenaxMainUiState {
        return uiState.copy(
            activeInstallOverlayTarget          = target,
            modelDownloadOverlayState           = KsenaxModelDownloadOverlayState.ModelOffer,
            isCancelDownloadConfirmationVisible = false,
        )
    }

    /**
     * Показывает подтверждение отмены текущего скачивания.
     *
     * @param uiState текущее состояние главного экрана.
     * @return состояние с видимым confirmation-dialog для отмены скачивания.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun showCancelConfirmation(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(isCancelDownloadConfirmationVisible = true)
    }

    /**
     * Скрывает подтверждение отмены текущего скачивания.
     *
     * @param uiState текущее состояние главного экрана.
     * @return состояние со скрытым confirmation-dialog.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun hideCancelConfirmation(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(isCancelDownloadConfirmationVisible = false)
    }

    /**
     * Полностью скрывает overlay установки модели.
     *
     * Метод также сбрасывает активную цель установки, пользовательские разрешения
     * скачивания по metered/roaming сети и флаг подтверждения отмены.
     *
     * @param uiState текущее состояние главного экрана.
     * @return состояние без активного install overlay.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun hideOverlay(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(
            modelDownloadOverlayState           = KsenaxModelDownloadOverlayState.Hidden,
            activeInstallOverlayTarget          = null,
            allowDownloadOverMeteredNetwork     = false,
            allowDownloadOverRoaming            = false,
            isCancelDownloadConfirmationVisible = false,
        )
    }
}
