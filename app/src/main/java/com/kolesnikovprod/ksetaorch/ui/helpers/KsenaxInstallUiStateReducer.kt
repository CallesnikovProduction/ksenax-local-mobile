package com.kolesnikovprod.ksetaorch.ui.helpers

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
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
        val previousSnapshot = snapshotFor(uiState, target)
        val presentationSnapshot = snapshot.withRetainedTransferEstimatesFrom(
            previousSnapshot = previousSnapshot,
        )

        return when (target) {
            KsenaxInstallOverlayTarget.Gemma4E2B -> {
                uiState.copy(gemmaInstallSnapshot = presentationSnapshot)
            }
            KsenaxInstallOverlayTarget.FunctionGemma270M -> {
                uiState.copy(
                    functionGemmaInstallSnapshot = presentationSnapshot,
                )
            }
            KsenaxInstallOverlayTarget.VoskSmallRu -> {
                uiState.copy(voskInstallSnapshot = presentationSnapshot)
            }
        }
    }

    private fun KsenaxInstallSnapshot.withRetainedTransferEstimatesFrom(
        previousSnapshot: KsenaxInstallSnapshot,
    ): KsenaxInstallSnapshot {
        val belongsToSameActiveDownload =
            isDownloading &&
                currentDownloadId != NO_DOWNLOAD_ID &&
                currentDownloadId == previousSnapshot.currentDownloadId
        if (!belongsToSameActiveDownload) return this

        val previousMetrics = previousSnapshot.transferMetrics
        val currentMetrics = transferMetrics
        return copy(
            transferMetrics = currentMetrics.copy(
                averageSpeedBytesPerSecond =
                    currentMetrics.averageSpeedBytesPerSecond
                        .takeIf { bytesPerSecond -> bytesPerSecond > 0L }
                        ?: previousMetrics.averageSpeedBytesPerSecond,
                estimatedRemainingTimeSeconds =
                    currentMetrics.estimatedRemainingTimeSeconds
                        ?: previousMetrics.estimatedRemainingTimeSeconds,
            ),
        )
    }

    /**
     * Переводит overlay установки в единый progress-режим.
     *
     * Конкретный этап — скачивание или проверка — UI определяет по безопасному
     * [com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot].
     *
     * @param uiState текущее состояние главного экрана.
     * @return состояние с активным progress overlay.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun showProgress(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(
            modelDownloadOverlayState = KsenaxModelDownloadOverlayState.Progress,
            isModelDownloadOverlayMinimized = false,
            isCancelDownloadConfirmationVisible = false,
        )
    }

    /**
     * Показывает короткий финальный handoff после успешной install-валидации.
     *
     * @since 0.3
     */
    fun showCompleted(
        uiState: KsenaxMainUiState,
    ): KsenaxMainUiState {
        return uiState.copy(
            modelDownloadOverlayState =
                KsenaxModelDownloadOverlayState.Completed,
            isCancelDownloadConfirmationVisible = false,
            isModelDownloadOverlayMinimized = false,
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
            isModelDownloadOverlayMinimized      = false,
            isActiveDownloadStalled              = false,
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
            isModelDownloadOverlayMinimized      = false,
            isActiveDownloadStalled              = false,
            allowDownloadOverMeteredNetwork     = false,
            allowDownloadOverRoaming            = false,
            isCancelDownloadConfirmationVisible = false,
        )
    }
}
