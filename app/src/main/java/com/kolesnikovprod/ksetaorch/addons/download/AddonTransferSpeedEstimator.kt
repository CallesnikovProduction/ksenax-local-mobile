package com.kolesnikovprod.ksetaorch.addons.download

/**
 * Скользящая оценка скорости APK download без привязки к UI.
 *
 * @since 0.3
 */
internal class AddonTransferSpeedEstimator(
    private val nanoTime: () -> Long = System::nanoTime,
    private val minimumSampleNanos: Long =
        DEFAULT_MINIMUM_SAMPLE_NANOS,
) {

    private var sampleTimeNanos: Long? = null
    private var sampleBytes: Long = 0L
    private var lastBytesPerSecond: Long? = null

    init {
        require(minimumSampleNanos > 0L)
    }

    fun update(downloadedBytes: Long): Long? {
        require(downloadedBytes >= 0L)
        val now = nanoTime()
        val previousTime = sampleTimeNanos
        if (previousTime == null || downloadedBytes < sampleBytes) {
            sampleTimeNanos = now
            sampleBytes = downloadedBytes
            lastBytesPerSecond = null
            return null
        }

        val elapsed = now - previousTime
        if (elapsed < minimumSampleNanos) {
            return lastBytesPerSecond
        }

        val transferred = downloadedBytes - sampleBytes
        lastBytesPerSecond = (
            transferred.toDouble() *
                NANOS_PER_SECOND.toDouble() /
                elapsed.toDouble()
            )
            .coerceAtLeast(0.0)
            .toLong()
        sampleTimeNanos = now
        sampleBytes = downloadedBytes
        return lastBytesPerSecond
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val DEFAULT_MINIMUM_SAMPLE_NANOS = 500_000_000L
    }
}
