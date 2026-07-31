package com.kolesnikovprod.ksetaorch.addons.presentation

import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallProgress
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallStage

/**
 * Отделяет сетевую телеметрию активной APK-загрузки от Compose и ViewModel.
 *
 * Первые 25 секунд не объявляются слабым соединением. После grace-периода
 * соединение считается слабым, если измеренная скорость ниже порога или
 * новые байты не приходили 25 секунд. Успешное ускорение возвращает зелёное
 * состояние без перезапуска загрузки.
 *
 * @since 0.3
 */
internal class AddonInstallConnectionMonitor(
    private val elapsedRealtimeMillis: () -> Long =
        { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val warningDelayMillis: Long =
        DEFAULT_WARNING_DELAY_MILLIS,
    private val weakConnectionBytesPerSecond: Long =
        DEFAULT_WEAK_CONNECTION_BYTES_PER_SECOND,
) {

    private val startedAtMillis = elapsedRealtimeMillis()
    private var lastByteProgressAtMillis = startedAtMillis
    private var downloadedBytes = 0L
    private var latestProgress = AddonInstallProgress(
        stage = AddonInstallStage.DOWNLOADING,
    )

    init {
        require(warningDelayMillis > 0L)
        require(weakConnectionBytesPerSecond > 0L)
    }

    @Synchronized
    fun update(progress: AddonInstallProgress) {
        val now = elapsedRealtimeMillis()
        if (progress.downloadedBytes > downloadedBytes) {
            lastByteProgressAtMillis = now
        }
        downloadedBytes = progress.downloadedBytes
        latestProgress = progress
    }

    @Synchronized
    fun currentQuality(): AddonInstallConnectionQuality {
        if (latestProgress.stage != AddonInstallStage.DOWNLOADING) {
            return AddonInstallConnectionQuality.NORMAL
        }

        val now = elapsedRealtimeMillis()
        if (now - startedAtMillis < warningDelayMillis) {
            return AddonInstallConnectionQuality.NORMAL
        }

        val stalled =
            now - lastByteProgressAtMillis >= warningDelayMillis
        val measuredSlowly = latestProgress.bytesPerSecond
            ?.let { speed ->
                speed < weakConnectionBytesPerSecond
            }
            ?: false

        return if (stalled || measuredSlowly) {
            AddonInstallConnectionQuality.WEAK
        } else {
            AddonInstallConnectionQuality.NORMAL
        }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val DEFAULT_WARNING_DELAY_MILLIS = 25_000L
        const val DEFAULT_WEAK_CONNECTION_BYTES_PER_SECOND =
            256L * 1024L
    }
}
