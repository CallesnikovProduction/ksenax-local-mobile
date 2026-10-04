package com.kolesnikovprod.ksetaorch.ui.main.settings

import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class KsenaxSupportedTextModelSelectorTest {

    @Test
    fun functionGemmaUsesItsOwnInstallTarget() {
        assertEquals(
            KsenaxInstallOverlayTarget.FunctionGemma270M,
            KsenaxSupportedTextModelSelector.installOverlayTargetFor(
                KsenaxSupportedTextModel.FunctionGemma,
            ),
        )
    }

    @Test
    fun savedFunctionGemmaSelectionIsKeptWhenInstalled() {
        assertEquals(
            KsenaxSupportedTextModel.FunctionGemma,
            KsenaxSupportedTextModelSelector.resolveSelectedInstalledModel(
                currentSelection = KsenaxSupportedTextModel.FunctionGemma,
                isGemmaInstalled = true,
                isFunctionGemmaInstalled = true,
            ),
        )
    }

    @Test
    fun installedFunctionGemmaBecomesFallbackWhenGemmaIsMissing() {
        assertEquals(
            KsenaxSupportedTextModel.FunctionGemma,
            KsenaxSupportedTextModelSelector.resolveSelectedInstalledModel(
                currentSelection = KsenaxSupportedTextModel.Gemma,
                isGemmaInstalled = false,
                isFunctionGemmaInstalled = true,
            ),
        )
    }

    @Test
    fun noModelIsSelectedWhenNothingIsInstalled() {
        assertEquals(
            null,
            KsenaxSupportedTextModelSelector.resolveSelectedInstalledModel(
                currentSelection = KsenaxSupportedTextModel.Gemma,
                isGemmaInstalled = false,
                isFunctionGemmaInstalled = false,
            ),
        )
    }

    @Test
    fun resolvedSelectionWinsForChatRoute() {
        assertEquals(
            KsenaxSupportedTextModel.Gemma,
            KsenaxSupportedTextModelSelector.resolveForChatRoute(
                resolvedSelection = KsenaxSupportedTextModel.Gemma,
                savedSelection = KsenaxSupportedTextModel.FunctionGemma,
            ),
        )
    }

    @Test
    fun savedSelectionIsUsedDuringEarlyRouteRestoration() {
        assertEquals(
            KsenaxSupportedTextModel.FunctionGemma,
            KsenaxSupportedTextModelSelector.resolveForChatRoute(
                resolvedSelection = null,
                savedSelection = KsenaxSupportedTextModel.FunctionGemma,
            ),
        )
    }

    @Test
    fun gemmaIsChatRouteFallbackWithoutAnySelection() {
        assertEquals(
            KsenaxSupportedTextModel.Gemma,
            KsenaxSupportedTextModelSelector.resolveForChatRoute(
                resolvedSelection = null,
                savedSelection = null,
            ),
        )
    }
}
