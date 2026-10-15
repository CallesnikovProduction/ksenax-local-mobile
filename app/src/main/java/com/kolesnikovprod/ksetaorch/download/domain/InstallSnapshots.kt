package com.kolesnikovprod.ksetaorch.download.domain

import com.kolesnikovprod.ksetaorch.download.domain.data.*

/** Единственное построение согласованных producer-снапшотов установки. @since 0.4 */
internal object InstallSnapshots {
    fun queued(id: Long) = KsenaxInstallSnapshot(
        currentDownloadId = id,
        isDownloading = true,
        downloadState = KsenaxDownloadState.PENDING,
    )

    fun transfer(id: Long, task: KsenaxDownloadTaskSnapshot) = queued(id).copy(
        downloadProgress = task.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
        downloadState = task.state,
        downloadWaitReason = task.waitReason,
        transferMetrics = task.transferMetrics,
    )

    fun installed(metrics: KsenaxDownloadTransferMetrics = KsenaxDownloadTransferMetrics()) =
        KsenaxInstallSnapshot(
            isInstalled = true,
            downloadProgress = 1f,
            downloadState = KsenaxDownloadState.SUCCESSFUL,
            preparationState = KsenaxInstallCheckState.SUCCESS,
            hasCandidate = KsenaxInstallCheckState.SUCCESS,
            isValidInstallation = KsenaxInstallCheckState.SUCCESS,
            transferMetrics = metrics,
        )

    fun failed(reason: KsenaxInstallFailure) = KsenaxInstallSnapshot(
        isInterrupted = true,
        downloadState = KsenaxDownloadState.FAILED,
        preparationState = KsenaxInstallCheckState.FAILURE,
        hasCandidate = KsenaxInstallCheckState.FAILURE,
        isValidInstallation = KsenaxInstallCheckState.FAILURE,
        failure = reason,
    )

    fun cancelled() = KsenaxInstallSnapshot(isCancelled = true)
}
