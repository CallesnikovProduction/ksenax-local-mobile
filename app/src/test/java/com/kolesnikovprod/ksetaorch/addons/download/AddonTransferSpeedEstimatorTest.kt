package com.kolesnikovprod.ksetaorch.addons.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonTransferSpeedEstimatorTest {

    @Test
    fun reportsRateOnlyAfterStableSampleWindow() {
        var timeNanos = 0L
        val estimator = AddonTransferSpeedEstimator(
            nanoTime = { timeNanos },
            minimumSampleNanos = 500_000_000L,
        )

        assertNull(estimator.update(0L))

        timeNanos = 250_000_000L
        assertNull(estimator.update(64L * 1024L))

        timeNanos = 500_000_000L
        assertEquals(
            256L * 1024L,
            estimator.update(128L * 1024L),
        )
    }

    @Test
    fun resetsWhenTransferredByteCounterMovesBackwards() {
        var timeNanos = 0L
        val estimator = AddonTransferSpeedEstimator(
            nanoTime = { timeNanos },
            minimumSampleNanos = 1L,
        )

        assertNull(estimator.update(100L))
        timeNanos = 1L
        assertEquals(100_000_000_000L, estimator.update(200L))
        timeNanos = 2L
        assertNull(estimator.update(50L))
    }
}
