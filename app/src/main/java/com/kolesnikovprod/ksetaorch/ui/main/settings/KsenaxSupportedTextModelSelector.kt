package com.kolesnikovprod.ksetaorch.ui.main.settings

import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState

/**
 * Чистые правила выбора основной текстовой модели и её install-target.
 *
 * @since 0.4
 * @author Stephan Kolesnikov
 */
object KsenaxSupportedTextModelSelector {

    /**
     * Возвращает признак подтверждённой установки [model] в текущем UI-state.
     *
     * @since 0.4
     */
    fun isInstalled(
        uiState: KsenaxMainUiState,
        model: KsenaxSupportedTextModel,
    ): Boolean = when (model) {
        KsenaxSupportedTextModel.Gemma -> uiState.isGemmaInstalled
        KsenaxSupportedTextModel.FunctionGemma ->
            uiState.isFunctionGemmaInstalled
    }

    /**
     * Сопоставляет response-модель с соответствующим install overlay target.
     *
     * @since 0.4
     */
    fun installOverlayTargetFor(
        model: KsenaxSupportedTextModel,
    ): KsenaxInstallOverlayTarget = when (model) {
        KsenaxSupportedTextModel.Gemma ->
            KsenaxInstallOverlayTarget.Gemma4E2B
        KsenaxSupportedTextModel.FunctionGemma ->
            KsenaxInstallOverlayTarget.FunctionGemma270M
    }

    /**
     * Сохраняет текущий выбор, пока его модель установлена; иначе выбирает
     * доступную модель либо возвращает `null`, если ни одной установки нет.
     *
     * @since 0.4
     */
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

    /**
     * Выбирает модель для немедленного создания chat ViewModel.
     *
     * [resolvedSelection] является install-aware источником истины после
     * инициализации. [savedSelection] закрывает короткое окно process recreation,
     * когда route уже восстанавливается, а файловая проверка ещё не завершилась.
     * Gemma остаётся детерминированным install-target при отсутствии обоих.
     *
     * @since 0.4
     */
    fun resolveForChatRoute(
        resolvedSelection: KsenaxSupportedTextModel?,
        savedSelection: KsenaxSupportedTextModel?,
    ): KsenaxSupportedTextModel =
        resolvedSelection ?: savedSelection ?: KsenaxSupportedTextModel.Gemma
}
