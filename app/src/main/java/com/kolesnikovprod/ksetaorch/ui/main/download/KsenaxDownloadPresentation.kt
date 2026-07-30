package com.kolesnikovprod.ksetaorch.ui.main.download

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadWaitReason
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxModelDownloadOverlayState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Пользовательский этап установки, не раскрывающий UI внутренности
 * DownloadManager или модельных gateway.
 *
 * @since 0.3
 */
enum class KsenaxDownloadPresentationStage {
    Download,
    Verification,
    Completed,
}

/**
 * Готовая модель отображения активной установки.
 *
 * Сырые байты форматируются только здесь, в presentation-контуре. Скорость и
 * ETA намеренно отсутствуют во время подготовки и проверки модели.
 *
 * @since 0.3
 */
data class KsenaxDownloadPresentation(
    val modelName: String,
    val progress: Float,
    val percent: Int,
    val stage: KsenaxDownloadPresentationStage,
    val stageDescription: String,
    val transferredText: String,
    val speedText: String?,
    val etaText: String?,
    val canCancel: Boolean,
    val showSlowConnectionHint: Boolean,
)

/**
 * Собирает UI-модель активной установки из process-level main state.
 *
 * @since 0.3
 */
fun KsenaxMainUiState.activeDownloadPresentation():
    KsenaxDownloadPresentation? {
    val target = activeInstallOverlayTarget ?: return null
    val snapshot = activeInstallSnapshot ?: return null
    if (
        modelDownloadOverlayState != KsenaxModelDownloadOverlayState.Progress &&
        modelDownloadOverlayState != KsenaxModelDownloadOverlayState.Completed
    ) {
        return null
    }

    val stage = when {
        modelDownloadOverlayState ==
            KsenaxModelDownloadOverlayState.Completed ->
            KsenaxDownloadPresentationStage.Completed
        snapshot.isDownloading ->
            KsenaxDownloadPresentationStage.Download
        else ->
            KsenaxDownloadPresentationStage.Verification
    }
    val visibleProgress = if (
        stage == KsenaxDownloadPresentationStage.Download
    ) {
        snapshot.downloadProgress.coerceIn(0f, 1f)
    } else {
        1f
    }
    val isActivelyTransferring =
        snapshot.isDownloading &&
            snapshot.downloadState == KsenaxDownloadState.RUNNING

    return KsenaxDownloadPresentation(
        modelName = target.overlayTitle,
        progress = visibleProgress,
        percent = (visibleProgress * 100f)
            .roundToInt()
            .coerceIn(0, 100),
        stage = stage,
        stageDescription = when (stage) {
            KsenaxDownloadPresentationStage.Download ->
                downloadStageDescription(
                    state = snapshot.downloadState,
                    waitReason = snapshot.downloadWaitReason,
                )
            KsenaxDownloadPresentationStage.Verification ->
                "проверка локального файла"
            KsenaxDownloadPresentationStage.Completed ->
                "модель загружена"
        },
        transferredText = formatTransferredBytes(snapshot.transferMetrics),
        speedText = snapshot.transferMetrics
            .takeIf { isActivelyTransferring }
            ?.let(::formatDownloadSpeed),
        etaText = snapshot.transferMetrics
            .takeIf { isActivelyTransferring }
            ?.let(::formatDownloadEta),
        canCancel =
            stage == KsenaxDownloadPresentationStage.Download,
        showSlowConnectionHint =
            stage == KsenaxDownloadPresentationStage.Download &&
                isActiveDownloadStalled,
    )
}

internal fun downloadStageDescription(
    state: KsenaxDownloadState,
    waitReason: KsenaxDownloadWaitReason?,
): String {
    return when (state) {
        KsenaxDownloadState.PENDING ->
            "ожидание запуска загрузки"
        KsenaxDownloadState.RUNNING ->
            "скачивание файлов модели"
        KsenaxDownloadState.PAUSED -> when (waitReason) {
            KsenaxDownloadWaitReason.WAITING_TO_RETRY ->
                "система повторит загрузку"
            KsenaxDownloadWaitReason.WAITING_FOR_NETWORK ->
                "ожидание подключения к сети"
            KsenaxDownloadWaitReason.WAITING_FOR_UNMETERED_NETWORK ->
                "ожидание Wi-Fi или нелимитной сети"
            KsenaxDownloadWaitReason.PAUSED_BY_SYSTEM,
            null,
            -> "загрузка приостановлена системой"
        }
        KsenaxDownloadState.UNKNOWN ->
            "получение состояния загрузки"
        KsenaxDownloadState.SUCCESSFUL ->
            "загрузка файлов завершена"
        KsenaxDownloadState.FAILED ->
            "загрузка завершилась ошибкой"
    }
}

/**
 * Возвращает компактную download-модель только после явного сворачивания
 * overlay пользователем.
 *
 * @since 0.3
 */
fun KsenaxMainUiState.minimizedDownloadPresentation():
    KsenaxDownloadPresentation? {
    if (!isModelDownloadOverlayMinimized) return null
    return activeDownloadPresentation()
}

internal fun formatTransferredBytes(
    metrics: KsenaxDownloadTransferMetrics,
    locale: Locale = Locale.getDefault(),
): String {
    val downloaded = formatBytes(metrics.downloadedBytes, locale)
    val total = metrics.totalBytes
        ?.takeIf { it > 0L }
        ?.let { formatBytes(it, locale) }
    return if (total == null) downloaded else "$downloaded / $total"
}

internal fun formatDownloadSpeed(
    metrics: KsenaxDownloadTransferMetrics,
    locale: Locale = Locale.getDefault(),
): String {
    val speed = metrics.averageSpeedBytesPerSecond
    return if (speed <= 0L) {
        "Вычисляем…"
    } else {
        "${formatBytes(speed, locale)}/с"
    }
}

internal fun formatDownloadEta(
    metrics: KsenaxDownloadTransferMetrics,
): String {
    val seconds = metrics.estimatedRemainingTimeSeconds ?: return "ETA —"
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0L) {
        "ETA $hours:${minutes.toString().padStart(2, '0')}:" +
            remainingSeconds.toString().padStart(2, '0')
    } else {
        "ETA $minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }
}

private fun formatBytes(
    bytes: Long,
    locale: Locale,
): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1_000L) return "$safeBytes Б"

    val unitIndex = (
        ln(safeBytes.toDouble()) / ln(1_000.0)
    ).toInt().coerceIn(1, BYTE_UNITS.lastIndex)
    val unitValue = safeBytes / 1_000.0.pow(unitIndex)
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return "${formatter.format(unitValue)} ${BYTE_UNITS[unitIndex]}"
}

private val BYTE_UNITS = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
