package com.kolesnikovprod.ksetaorch.download.domain

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTransferMetrics
import java.util.ArrayDeque
import kotlin.math.ceil

/**
 * Сглаживает фактическую скорость загрузки по короткому окну byte-снапшотов.
 *
 * Расчёт использует монотонное время и разницу между самым старым и самым новым
 * замером в окне. Нулевой прирост байтов остаётся частью окна, поэтому при
 * реальной паузе скорость постепенно опускается до нуля, а ETA скрывается.
 *
 * Экземпляр хранит состояние только одной активной задачи. Новый `downloadId`,
 * откат счётчика байтов или времени автоматически начинает новое окно.
 *
 * @since 0.3
 */
internal class DownloadTransferMetricsEstimator(
    private val sampleWindowSize: Int = DEFAULT_SAMPLE_WINDOW_SIZE,
) {

    private data class TransferSample(
        val downloadedBytes: Long,
        val observedAtNanos: Long,
    )

    private var activeDownloadId: Long? = null
    private val samples = ArrayDeque<TransferSample>(sampleWindowSize)

    init {
        require(sampleWindowSize >= MINIMUM_SAMPLE_WINDOW_SIZE) {
            "Transfer metric window must contain at least two samples"
        }
    }

    @Synchronized
    fun estimate(
        downloadId: Long,
        downloadedBytes: Long,
        totalBytes: Long?,
        observedAtNanos: Long = System.nanoTime(),
    ): KsenaxDownloadTransferMetrics {
        val normalizedDownloadedBytes = downloadedBytes.coerceAtLeast(0L)
        val normalizedTotalBytes = totalBytes?.takeIf { it >= 0L }
        val latestSample = samples.peekLast()

        if (
            activeDownloadId != downloadId ||
            latestSample?.let {
                normalizedDownloadedBytes < it.downloadedBytes ||
                    observedAtNanos <= it.observedAtNanos
            } == true
        ) {
            samples.clear()
            activeDownloadId = downloadId
        }

        samples.addLast(
            TransferSample(
                downloadedBytes = normalizedDownloadedBytes,
                observedAtNanos = observedAtNanos,
            )
        )

        while (samples.size > sampleWindowSize) {
            samples.removeFirst()
        }

        val averageSpeedBytesPerSecond = calculateAverageSpeed()
        val estimatedRemainingTimeSeconds = calculateRemainingTime(
            downloadedBytes = normalizedDownloadedBytes,
            totalBytes = normalizedTotalBytes,
            averageSpeedBytesPerSecond = averageSpeedBytesPerSecond,
        )

        return KsenaxDownloadTransferMetrics(
            downloadedBytes = normalizedDownloadedBytes,
            totalBytes = normalizedTotalBytes,
            averageSpeedBytesPerSecond = averageSpeedBytesPerSecond,
            estimatedRemainingTimeSeconds = estimatedRemainingTimeSeconds,
        )
    }

    private fun calculateAverageSpeed(): Long {
        if (samples.size < MINIMUM_SAMPLE_WINDOW_SIZE) return 0L

        val firstSample = samples.first
        val lastSample = samples.last
        val transferredBytes =
            (lastSample.downloadedBytes - firstSample.downloadedBytes)
                .coerceAtLeast(0L)
        val elapsedNanos = lastSample.observedAtNanos - firstSample.observedAtNanos

        if (transferredBytes == 0L || elapsedNanos <= 0L) return 0L

        return (
            transferredBytes.toDouble() *
                NANOS_PER_SECOND.toDouble() /
                elapsedNanos.toDouble()
            ).toLong().coerceAtLeast(0L)
    }

    private fun calculateRemainingTime(
        downloadedBytes: Long,
        totalBytes: Long?,
        averageSpeedBytesPerSecond: Long,
    ): Long? {
        val knownTotalBytes = totalBytes ?: return null
        val remainingBytes = (knownTotalBytes - downloadedBytes).coerceAtLeast(0L)

        if (remainingBytes == 0L) return 0L
        if (averageSpeedBytesPerSecond <= 0L) return null

        return ceil(
            remainingBytes.toDouble() / averageSpeedBytesPerSecond.toDouble()
        ).toLong()
    }

    private companion object {
        const val DEFAULT_SAMPLE_WINDOW_SIZE = 5
        const val MINIMUM_SAMPLE_WINDOW_SIZE = 2
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
