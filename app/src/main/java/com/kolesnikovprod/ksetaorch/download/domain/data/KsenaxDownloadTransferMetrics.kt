package com.kolesnikovprod.ksetaorch.download.domain.data

/**
 * Метрики фактической передачи данных для одной задачи загрузки.
 *
 * Значения остаются сырыми и не содержат UI-форматирования: presentation-слой
 * сам выбирает единицы измерения, локаль и вид ETA.
 *
 * @property downloadedBytes сколько байтов уже принято в локальный файл.
 * @property totalBytes полный размер артефакта или `null`, если
 * [android.app.DownloadManager] пока не сообщил его.
 * @property averageSpeedBytesPerSecond сглаженная скорость текущей загрузки
 * в байтах в секунду. `0L` означает, что для расчёта ещё недостаточно данных
 * или передача временно не движется.
 * @property estimatedRemainingTimeSeconds оценка оставшегося времени в секундах
 * или `null`, если полный размер/скорость пока неизвестны.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class KsenaxDownloadTransferMetrics(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val averageSpeedBytesPerSecond: Long = 0L,
    val estimatedRemainingTimeSeconds: Long? = null,
) {
    init {
        require(downloadedBytes >= 0L) {
            "Downloaded byte count must not be negative"
        }
        require(totalBytes == null || totalBytes >= 0L) {
            "Total byte count must not be negative"
        }
        require(averageSpeedBytesPerSecond >= 0L) {
            "Average download speed must not be negative"
        }
        require(
            estimatedRemainingTimeSeconds == null ||
                estimatedRemainingTimeSeconds >= 0L
        ) {
            "Estimated remaining time must not be negative"
        }
    }
}
