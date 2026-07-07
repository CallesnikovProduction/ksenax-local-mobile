package com.kolesnikovprod.ksetaorch.ui.main.settings

import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState

object KsenaxSupportedTextModelSelector {

    fun isInstalled(
        uiState: KsenaxMainUiState,
        model: KsenaxSupportedTextModel,
    ): Boolean = when (model) {
        KsenaxSupportedTextModel.Gemma -> uiState.isGemmaInstalled
        KsenaxSupportedTextModel.FunctionGemma ->
            uiState.isFunctionGemmaInstalled
    }

    fun installOverlayTargetFor(
        model: KsenaxSupportedTextModel,
    ): KsenaxInstallOverlayTarget = when (model) {
        KsenaxSupportedTextModel.Gemma ->
            KsenaxInstallOverlayTarget.Gemma4E2B
        KsenaxSupportedTextModel.FunctionGemma ->
            KsenaxInstallOverlayTarget.FunctionGemma270M
    }

    fun resolveSelectedInstalledModel(
        currentSelection: KsenaxSupportedTextModel?,
        isGemmaInstalled: Boolean,
        isFunctionGemmaInstalled: Boolean,
    ): KsenaxSupportedTextModel? {
        val currentIsInstalled = when (currentSelection) {
            KsenaxSupportedTextModel.Gemma -> isGemmaInstalled
            KsenaxSupportedTextModel.FunctionGemma ->
                isFunctionGemmaInstalled
            null -> false
        }
        if (currentIsInstalled) return currentSelection

        return when {
            isGemmaInstalled -> KsenaxSupportedTextModel.Gemma
            isFunctionGemmaInstalled -> KsenaxSupportedTextModel.FunctionGemma
            else -> null
        }
    }
}
