package com.kolesnikovprod.ksetaorch.addons.download

/**
 * Состоянием управляемый оценщик текущей скорости загрузки addon APK.
 *
 * При каждом вызове [update] получает накопленное количество скачанных байт
 * и периодически вычисляет скорость относительно последнего принятого
 * sample. Между полноценными пересчётами возвращает последнее известное
 * значение, чтобы не обновлять UI и notification на каждом сетевом chunk.
 *
 * Первый вызов только устанавливает начальную точку и возвращает `null`.
 * Если накопленный счётчик байт уменьшается, estimator считает это началом
 * новой передачи, сбрасывает состояние и также возвращает `null`.
 *
 * Для измерения интервалов используется монотонный источник времени,
 * а не календарное системное время.
 *
 * Экземпляр хранит изменяемое состояние и предназначен для одной
 * последовательной download-операции. Класс не является thread-safe.
 *
 * @property nanoTime монотонный источник времени в наносекундах.
 * @property minimumSampleNanos минимальный интервал между пересчётами
 * скорости.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal class AddonTransferSpeedEstimator(
    private val nanoTime          : () -> Long = System::nanoTime,
    private val minimumSampleNanos: Long       = DEFAULT_MINIMUM_SAMPLE_NANOS,
) {

    /**
     * Время последнего принятого семпла. На старте принимает `null`, поскольку замер еще не начат.
     */
    private var sampleTimeNanos: Long? = null

    /**
     * Сколько байт было скачано на предыдущем семпле.
     */
    private var sampleBytes: Long = 0L

    /**
     * Последняя рассчитанная скорость.
     * Нужна для того, чтобы между полноценными измерениями возвращать
     * стабильное последнее значение,
     * а не `null`.
     */
    private var lastBytesPerSecond: Long? = null

    init {
        require(minimumSampleNanos > 0L)
    }

    /**
     * Обновляет оценку скорости для текущего накопленного объёма загрузки.
     *
     * @param downloadedBytes общее накопленное количество байт, скачанных с начала
     * текущей передачи.
     * @return текущая оценка в байт/сек либо `null`, если для расчёта
     * ещё недостаточно данных или счётчик передачи был сброшен.
     *
     * @since 0.3
     */
    fun update(downloadedBytes: Long): Long? {
        require(downloadedBytes >= 0L)

        val now = nanoTime()
        val previousTime = sampleTimeNanos

        // первый вызов и сброс по разным характерным причинам: ретрай, пересоздание файла...
        if (
            previousTime == null ||
            downloadedBytes < sampleBytes
        ) {
            sampleTimeNanos = now
            sampleBytes = downloadedBytes
            lastBytesPerSecond = null
            return null
        }

        val elapsed = now - previousTime

        // В случае, если прошло мало времени, то новая скорость не вычисляется
        if (elapsed < minimumSampleNanos) {
            return lastBytesPerSecond
        }

        // если ранее было 5M, сейчас 7М, то transferred = 2M
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
