package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.domain.DownloadTransferMetricsEstimator
import org.junit.Assert.*
import org.junit.Test

class DownloadTransferMetricsTest {
    @Test fun unknownTotalHasNoEta() {
        val estimator = DownloadTransferMetricsEstimator()
        estimator.estimate(1, 0, null, 1)
        val result = estimator.estimate(1, 1000, null, 1_000_000_001)
        assertEquals(1000L, result.averageSpeedBytesPerSecond)
        assertNull(result.estimatedRemainingTimeSeconds)
    }

    @Test fun stalledWindowDecaysToZero() {
        val estimator = DownloadTransferMetricsEstimator()
        estimator.estimate(1, 0, 10000, 1)
        estimator.estimate(1, 1000, 10000, 1_000_000_001)
        var result = estimator.estimate(1, 1000, 10000, 2_000_000_001)
        for (second in 3L..6L) result = estimator.estimate(1, 1000, 10000, second * 1_000_000_000 + 1)
        assertEquals(0L, result.averageSpeedBytesPerSecond)
        assertNull(result.estimatedRemainingTimeSeconds)
    }

    @Test fun taskChangeAndByteResetCannotCreateFakeSpeed() {
        val estimator = DownloadTransferMetricsEstimator()
        estimator.estimate(1, 2000, 10000, 1)
        assertEquals(0L, estimator.estimate(1, 100, 10000, 1_000_000_001).averageSpeedBytesPerSecond)
        assertEquals(0L, estimator.estimate(2, 5000, 10000, 2_000_000_001).averageSpeedBytesPerSecond)
    }

    @Test fun multiGigabyteCountersAndEtaDoNotOverflow() {
        val estimator = DownloadTransferMetricsEstimator()
        estimator.estimate(1, 5_000_000_000L, 10_000_000_000L, 1)
        val result = estimator.estimate(1, 6_000_000_000L, 10_000_000_000L, 1_000_000_001)
        assertEquals(1_000_000_000L, result.averageSpeedBytesPerSecond)
        assertEquals(4L, result.estimatedRemainingTimeSeconds)
    }
}
