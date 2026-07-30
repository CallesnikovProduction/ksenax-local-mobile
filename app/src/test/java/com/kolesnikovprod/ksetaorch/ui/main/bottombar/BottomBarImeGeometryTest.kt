package com.kolesnikovprod.ksetaorch.ui.main.bottombar

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomBarImeGeometryTest {

    @Test
    fun closedKeyboardProducesZeroVisibility() {
        assertEquals(
            0f,
            calculateImeVisibilityFraction(
                currentBottomPx = 0,
                animationSourceBottomPx = 0,
                animationTargetBottomPx = 0,
            ),
        )
    }

    @Test
    fun fullyOpenedKeyboardProducesFullVisibility() {
        assertEquals(
            1f,
            calculateImeVisibilityFraction(
                currentBottomPx = 900,
                animationSourceBottomPx = 900,
                animationTargetBottomPx = 900,
            ),
        )
    }

    @Test
    fun closingKeyboardKeepsContinuousProgress() {
        assertEquals(
            0.5f,
            calculateImeVisibilityFraction(
                currentBottomPx = 450,
                animationSourceBottomPx = 900,
                animationTargetBottomPx = 0,
            ),
        )
    }

    @Test
    fun openingKeyboardKeepsContinuousProgress() {
        assertEquals(
            0.5f,
            calculateImeVisibilityFraction(
                currentBottomPx = 450,
                animationSourceBottomPx = 0,
                animationTargetBottomPx = 900,
            ),
        )
    }
}
