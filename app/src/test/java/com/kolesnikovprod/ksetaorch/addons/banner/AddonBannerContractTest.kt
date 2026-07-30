package com.kolesnikovprod.ksetaorch.addons.banner

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonBannerContractTest {

    @Test
    fun bannerUsesPublishedHostDimensions() {
        assertEquals(1_920, AddonBannerContract.WIDTH_PIXELS)
        assertEquals(576, AddonBannerContract.HEIGHT_PIXELS)
        assertEquals(
            1_920f / 576f,
            AddonBannerContract.ASPECT_RATIO,
            0.0001f,
        )
    }
}
