package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallCheckState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
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
 * 5) загрузка прервалась?
 * 6) пользователь отменил?
 * 7) модель валидна?
 * 8) надо ли удалить битый артефакт?
 *
 * Иными словами, класс является конечным автоматом, который обеспечивает правильную работу
 * со сценариями установки.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxModelInstallCoordinator(
    private val installUseCase: KsenaxModelInstallUseCase,
) {

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

        emit {
            copy(
                preparationState = KsenaxInstallCheckState.LOADING,
                isValidating = false,
            )
        }

        val isInstallPrepared = installUseCase.prepareInstallCandidate()

        emit {
            copy(
                preparationState =
                    if (isInstallPrepared) {
                        KsenaxInstallCheckState.SUCCESS
                    } else {
                        KsenaxInstallCheckState.FAILURE
                    },
                isValidating = isInstallPrepared,
            )
        }

        val isValidInstallation =
            isInstallPrepared && installUseCase.hasValidInstallation()

        emit {
            copy(
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

        if (!isValidInstallation) {
            installUseCase.deleteLocalArtifacts()

            emit {
                copy(hasCandidate = KsenaxInstallCheckState.FAILURE)
            }
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
                        downloadProgress      = 0f
                    )
                }
                installUseCase.clearArtifacts()
                break
            }

            emit {
                copy(downloadProgress = status.progress)
            }

            when (status.state) {
                // Пускается валидация на даже успех
                KsenaxDownloadState.SUCCESSFUL -> {
                    emit {
                        copy(
                            downloadProgress = 1f,
                            isDownloading = false,
                            preparationState = KsenaxInstallCheckState.LOADING,
                            isValidating = false,
                        )
                    }

                    val isInstallPrepared = installUseCase.prepareInstallCandidate()

                    emit {
                        copy(
                            preparationState =
                                if (isInstallPrepared) {
                                    KsenaxInstallCheckState.SUCCESS
                                } else {
                                    KsenaxInstallCheckState.FAILURE
                                },
                            isValidating = isInstallPrepared,
                        )
                    }

                    val isValidInstallation = try {
                        isInstallPrepared && installUseCase.hasValidInstallation()
                    } finally {
                        emit {
                            copy(isValidating = false)
                        }
                    }

                    emit {
                        copy(
                            currentDownloadId     = NO_DOWNLOAD_ID,
                            downloadProgress      = if (isValidInstallation) 1f else 0f,
                            isDownloading         = false,
                            isInterrupted = !isValidInstallation,
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

                    installUseCase.clearSavedDownloadId()

                    if (!isValidInstallation) {
                        installUseCase.deleteLocalArtifacts()
                    }

                    break
                }

                KsenaxDownloadState.FAILED -> {
                    emit {
                        copy(
                            currentDownloadId     = NO_DOWNLOAD_ID,
                            isDownloading         = false,
                            isInterrupted = true,
                            preparationState = KsenaxInstallCheckState.NON_CONFIRMED,
                            downloadProgress      = 0f,
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

    private companion object {
        const val DOWNLOAD_POLL_DELAY_MILLIS = 500L
        const val MODEL_VALIDATION_RESULT_DELAY_MILLIS = 350L
    }
}
