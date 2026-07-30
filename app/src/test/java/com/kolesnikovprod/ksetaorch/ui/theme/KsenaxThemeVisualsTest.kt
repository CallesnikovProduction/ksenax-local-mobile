package com.kolesnikovprod.ksetaorch.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxThemeVisualsTest {

    @Test
    fun everyThemeIdHasExactlyOneSelectableVisualContract() {
        assertEquals(
            KsenaxThemeId.entries.toSet(),
            KsenaxAvailableThemes.map { theme -> theme.id }.toSet(),
        )
        assertEquals(
            KsenaxThemeId.entries.size,
            KsenaxAvailableThemes.size,
        )
    }

    @Test
    fun visualContractsContainRequiredBackgroundAssetsAndStarPalette() {
        KsenaxAvailableThemes.forEach { theme ->
            assertTrue(theme.previewDrawableRes != 0)
            assertTrue(theme.backgroundDrawableRes != 0)
            assertTrue(theme.foregroundDrawableRes != 0)
            assertTrue(theme.sparkleColors.isNotEmpty())
            assertEquals(4, theme.markerColors.size)
            assertTrue(theme.typingPhrases.isNotEmpty())
            assertTrue(theme.typingPhrases.none(String::isBlank))
            assertEquals(theme.id, theme.id.visuals.id)
        }
    }
}
