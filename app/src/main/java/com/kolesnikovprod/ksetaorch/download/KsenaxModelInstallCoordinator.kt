package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.InstallCandidateFinalizer
import com.kolesnikovprod.ksetaorch.download.domain.InstallFinalizationEvent
import com.kolesnikovprod.ksetaorch.download.domain.InstallFinalizationOutcome
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallCheckState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Контроллер состояния установки модели для UI. Он находится выше по иерархии,
 * чем остальные установка-ориентированные объекты Kotlin. Отсюда всё рулится
 * в [KsenaxModelInstallUseCase].
 *
 * Отвечает на банально простой вопрос: «как это превратить в состояние экрана?»
 * В его ответственности также находятся ответы на вопросы:
 * 1) есть ли сохранённый downloadId?
 * 2) надо ли валидировать локальный артефакт?
 * 3) идёт ли загрузка?
 * 4) сколько процентов скачано?
 * 5) сколько байтов уже загружено и каков полный размер?
 * 6) какова сглаженная скорость и расчётное оставшееся время?
 * 7) загрузка прервалась?
 * 8) пользователь отменил?
 * 9) модель валидна?
 * 10) надо ли удалить битый артефакт?
 *
 * Иными словами, класс является конечным автоматом, который обеспечивает правильную работу
 * со сценариями установки, пока UI-процесс активен. Долговечность самой
 * передачи и post-download финализации принадлежит Android DownloadManager и
 * background install worker, а не lifecycle этого coordinator-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxModelInstallCoordinator(
    private val installUseCase: KsenaxModelInstallUseCase,
) {

    private val installCandidateFinalizer =
        InstallCandidateFinalizer(installUseCase)

    /**
     * Функция создания первого снапшота для экрана.
     * Сразу подставляет сохранённый `downloadId` из хранилища.
     *
     * Возвращает [NO_DOWNLOAD_ID], если нет задачи загрузки.
     * Все остальные флаги и значения остаются по умолчанию.
     *
     * @since 0.2
     */
    fun initialSnapshot(): KsenaxInstallSnapshot {
        return KsenaxInstallSnapshot(
            currentDownloadId = installUseCase.getSavedDownloadId(),
        )
    }

    /**
     * Синхронное действие для кнопки скачивания.
     *
     * Внутри use case запускает [android.app.DownloadManager]-задачу,
     * потом сохраняет `downloadId`, а coordinator отражает это в снапшоте.
     *
     * @since 0.2
     */
    fun startDownload(
        currentSnapshot: KsenaxInstallSnapshot,
        policy: KsenaxDownloadPolicy = KsenaxDownloadPolicy(),
    ): KsenaxInstallSnapshot {
        val downloadId = installUseCase.startDownloadAndSave(policy)

        return currentSnapshot.copy(
            currentDownloadId     = downloadId,  // Полученный Id теперь пихаем
            downloadProgress      = 0f,          // Стартуем -> с нуля
            downloadState         = KsenaxDownloadState.PENDING,
            downloadWaitReason    = null,
            transferMetrics       = KsenaxDownloadTransferMetrics(),
            isDownloading         = true,
            isInterrupted = false,
            isCancelled   = false,
            preparationState = KsenaxInstallCheckState.NON_CONFIRMED,
            // Защита от временного сохранения "модель валидна"
            isInstalled      = false,
            hasCandidate     = KsenaxInstallCheckState.LOADING,
            isValidInstallation   = KsenaxInstallCheckState.NON_CONFIRMED
        )
    }

    /**
     * Отменяет текущий `downloadId` через use case и возвращает снапшот с сообщением отмены.
     *
     * @since 0.2
     */
    fun cancelDownload(
        currentSnapshot: KsenaxInstallSnapshot,
    ): KsenaxInstallSnapshot {
        installUseCase.cancelDownload(currentSnapshot.currentDownloadId)

        return currentSnapshot.copy(
            currentDownloadId     = NO_DOWNLOAD_ID,
            downloadProgress      = 0f,
            downloadState         = KsenaxDownloadState.UNKNOWN,
            downloadWaitReason    = null,
            transferMetrics       = KsenaxDownloadTransferMetrics(),
            isDownloading         = false,
            isInterrupted = false,
            isCancelled   = true,
            preparationState = KsenaxInstallCheckState.NON_CONFIRMED,
            isInstalled      = false,
            hasCandidate     = KsenaxInstallCheckState.FAILURE,
            isValidInstallation   = KsenaxInstallCheckState.FAILURE
        )
    }

    /**
     * Центральный метод координатора.
     *
     * Запускает восстановление/наблюдение состояния установки.
     *
     * @since 0.2
     */
    suspend fun observeInstallState(
        initialSnapshot:    KsenaxInstallSnapshot,
        onSnapshotChanged: (KsenaxInstallSnapshot) -> Unit,
    ) {
        // Текущая локальная копия состояния внутри корутины.
        var snapshot = initialSnapshot

        /**
         * Позволяет выполнять действия со снапшотом и вернуть новый снапшот.
         */
        fun emit(update: KsenaxInstallSnapshot.() -> KsenaxInstallSnapshot) {
            snapshot = snapshot.update()
            onSnapshotChanged(snapshot)
        }

        emit {
            copy(isValidating = true)
        }

        // Если активной загрузки нет, то проверяем локальный артефакт:
        if (snapshot.currentDownloadId == NO_DOWNLOAD_ID) {
            validateLocalInstallation(
                snapshot = snapshot,
                emit     = ::emit,
            )
            return
        }

        observeActiveDownload(
            activeDownloadId = snapshot.currentDownloadId,
            emit             = ::emit,
        )
    }

    private suspend fun validateLocalInstallation(
        snapshot: KsenaxInstallSnapshot,
        emit:    (KsenaxInstallSnapshot.() -> KsenaxInstallSnapshot) -> Unit,
    ) {
        emit {
            // Сохраняется новое состояние снапшота
            copy(
                hasCandidate   = KsenaxInstallCheckState.LOADING,
                isValidInstallation = KsenaxInstallCheckState.LOADING,
            )
        }

        // Если уже установлена -> нечего обслуживать
        if (snapshot.isInstalled) {
            emit {
                copy(isValidating = false)
            }
            return
        }

        val hasInstallCandidate = installUseCase.hasInstallCandidate()

        // Если в папке ничего нет/подходящего кандидата нет...
        if (!hasInstallCandidate) {
            emit {
                copy(
                    hasCandidate   = KsenaxInstallCheckState.FAILURE,
                    isValidInstallation = KsenaxInstallCheckState.FAILURE,
                    isValidating   = false,
                )
            }
            return
        }

        // Проверили, но не подтвердили: нужна подготовка и глубокая проверка.
        emit {
            copy(hasCandidate = KsenaxInstallCheckState.SUCCESS)
        }

        val finalizationOutcome = finalizeInstallCandidate(
            expectedDownloadId = null,
            emit = emit,
        )
        val isValidInstallation =
            finalizationOutcome == InstallFinalizationOutcome.INSTALLED

        emit {
            copy(
                preparationState =
                    if (isValidInstallation) {
                        KsenaxInstallCheckState.SUCCESS
                    } else {
                        KsenaxInstallCheckState.FAILURE
                    },
                hasCandidate =
                    if (isValidInstallation) {
                        KsenaxInstallCheckState.SUCCESS
                    } else {
                        KsenaxInstallCheckState.FAILURE
                    },
                isValidInstallation =
                    if (isValidInstallation) {
                        KsenaxInstallCheckState.SUCCESS
                    } else {
                        KsenaxInstallCheckState.FAILURE
                    },
            )
        }

        delay(MODEL_VALIDATION_RESULT_DELAY_MILLIS.milliseconds)

        emit {
            copy(
                isInstalled      = isValidInstallation,
                isInterrupted = !isValidInstallation,
                isValidating     = false,
            )
        }
    }

    private suspend fun observeActiveDownload(
        activeDownloadId: Long,
        emit:            (KsenaxInstallSnapshot.() -> KsenaxInstallSnapshot) -> Unit,
    ) {
        emit {
            copy(
                isValidating     = false,
                preparationState = KsenaxInstallCheckState.NON_CONFIRMED,
                isDownloading         = true,
                isInterrupted = false,
                isCancelled   = false,
            )
        }

        while (true) {
            val status = installUseCase.queryDownloadSnapshot(activeDownloadId)

            // DownloadManager не нашёл задачу по id
            if (status == null) {
                emit {
                    copy(
                        currentDownloadId     = NO_DOWNLOAD_ID,
                        isDownloading         = false,
                        isInterrupted = true,
                        downloadProgress      = 0f,
                        downloadState         = KsenaxDownloadState.UNKNOWN,
                        downloadWaitReason    = null,
                        transferMetrics       = KsenaxDownloadTransferMetrics(),
                    )
                }
                installUseCase.clearArtifacts()
                break
            }

            emit {
                copy(
                    downloadProgress = status.progress,
                    downloadState = status.state,
                    downloadWaitReason = status.waitReason,
                    transferMetrics = status.transferMetrics,
                )
            }

            when (status.state) {
                // Пускается валидация на даже успех
                KsenaxDownloadState.SUCCESSFUL -> {
                    emit {
                        copy(
                            downloadProgress = 1f,
                            isDownloading = false,
                            downloadState = KsenaxDownloadState.SUCCESSFUL,
                            downloadWaitReason = null,
                            isValidating = false,
                        )
                    }

                    val finalizationOutcome = finalizeInstallCandidate(
                        expectedDownloadId = activeDownloadId,
                        emit = emit,
                    )
                    val isValidInstallation =
                        finalizationOutcome == InstallFinalizationOutcome.INSTALLED
                    val isSuperseded =
                        finalizationOutcome == InstallFinalizationOutcome.SUPERSEDED
                    val savedDownloadId =
                        if (isSuperseded) {
                            installUseCase.getSavedDownloadId()
                        } else {
                            NO_DOWNLOAD_ID
                        }

                    emit {
                        copy(
                            currentDownloadId     = savedDownloadId,
                            downloadProgress      = if (isValidInstallation) 1f else 0f,
                            transferMetrics =
                                if (isValidInstallation) {
                                    status.transferMetrics
                                } else {
                                    KsenaxDownloadTransferMetrics()
                                },
                            isDownloading         =
                                isSuperseded && savedDownloadId != NO_DOWNLOAD_ID,
                            downloadState         =
                                when {
                                    isValidInstallation ->
                                        KsenaxDownloadState.SUCCESSFUL
                                    isSuperseded ->
                                        KsenaxDownloadState.UNKNOWN
                                    else ->
                                        KsenaxDownloadState.FAILED
                                },
                            downloadWaitReason    = null,
                            preparationState =
                                if (isValidInstallation) {
                                    KsenaxInstallCheckState.SUCCESS
                                } else {
                                    KsenaxInstallCheckState.FAILURE
                                },
                            isInterrupted =
                                finalizationOutcome == InstallFinalizationOutcome.INVALID,
                            isCancelled =
                                isSuperseded && savedDownloadId == NO_DOWNLOAD_ID,
                            isInstalled      = isValidInstallation,
                            hasCandidate =
                                if (isValidInstallation)
                                    KsenaxInstallCheckState.SUCCESS
                                else KsenaxInstallCheckState.FAILURE,
                            isValidInstallation =
                                if (isValidInstallation)
                                    KsenaxInstallCheckState.SUCCESS
                                else KsenaxInstallCheckState.FAILURE,
                        )
                    }

                    break
                }

                KsenaxDownloadState.FAILED -> {
                    emit {
                        copy(
                            currentDownloadId     = NO_DOWNLOAD_ID,
                            isDownloading         = false,
                            downloadState         = KsenaxDownloadState.FAILED,
                            downloadWaitReason    = null,
                            isInterrupted = true,
                            preparationState = KsenaxInstallCheckState.NON_CONFIRMED,
                            downloadProgress      = 0f,
                            transferMetrics       = KsenaxDownloadTransferMetrics(),
                            hasCandidate     = KsenaxInstallCheckState.FAILURE,
                            isValidInstallation   = KsenaxInstallCheckState.FAILURE
                        )
                    }
                    installUseCase.clearArtifacts()
                    break
                }

                KsenaxDownloadState.PENDING,
                KsenaxDownloadState.RUNNING,
                KsenaxDownloadState.PAUSED,
                KsenaxDownloadState.UNKNOWN -> Unit
            }

            delay(DOWNLOAD_POLL_DELAY_MILLIS.milliseconds)
        }
    }

    /**
     * Маппит внутренние события общей финализации в UI-снапшот.
     *
     * Сам prepare/validate-алгоритм находится в [InstallCandidateFinalizer] и
     * одинаков для активного UI и фонового WorkManager.
     */
    private suspend fun finalizeInstallCandidate(
        expectedDownloadId: Long?,
        emit: (KsenaxInstallSnapshot.() -> KsenaxInstallSnapshot) -> Unit,
    ): InstallFinalizationOutcome {
        return installCandidateFinalizer.finalize(
            expectedDownloadId = expectedDownloadId,
            onEvent = { event ->
                when (event) {
                    InstallFinalizationEvent.PreparationStarted -> {
                        emit {
                            copy(
                                preparationState = KsenaxInstallCheckState.LOADING,
                                isValidating = false,
                            )
                        }
                    }

                    is InstallFinalizationEvent.PreparationFinished -> {
                        emit {
                            copy(
                                preparationState =
                                    if (event.isSuccessful) {
                                        KsenaxInstallCheckState.SUCCESS
                                    } else {
                                        KsenaxInstallCheckState.FAILURE
                                    },
                                isValidating = event.isSuccessful,
                            )
                        }
                    }

                    InstallFinalizationEvent.ValidationStarted -> {
                        emit {
                            copy(
                                isValidating = true,
                                isValidInstallation = KsenaxInstallCheckState.LOADING,
                            )
                        }
                    }

                    is InstallFinalizationEvent.ValidationFinished -> {
                        emit {
                            copy(
                                isValidating = false,
                                isValidInstallation =
                                    if (event.isSuccessful) {
                                        KsenaxInstallCheckState.SUCCESS
                                    } else {
                                        KsenaxInstallCheckState.FAILURE
                                    },
                            )
                        }
                    }
                }
            },
        )
    }

    private companion object {
        const val DOWNLOAD_POLL_DELAY_MILLIS = 500L
        const val MODEL_VALIDATION_RESULT_DELAY_MILLIS = 350L
    }
}
