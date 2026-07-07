package com.kolesnikovprod.ksetaorch.ui.main.settings

import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState

/**
 * Помогает выбрать и проверить модель транскрипции для голосового ввода.
 *
 * Объект не хранит собственного состояния и работает как чистый decision-helper:
 * получает текущий [KsenaxMainUiState], анализирует выбранную пользователем модель,
 * признаки установленных моделей и возвращает решение для ViewModel.
 *
 * Этот слой нужен, чтобы [KsenaxMainViewModel] не содержала внутри себя правила выбора
 * модели транскрипции и маппинг модели на цель установки.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
object KsenaxTranscribingModelSelector {

    /**
     * Возвращает модель, которую следует показывать выбранной в настройках транскрипции.
     *
     * Текущий пользовательский выбор сохраняется, пока соответствующая модель установлена.
     * Если выбранная модель недоступна, используется установленная fallback-модель:
     * сначала Vosk, затем Gemma. Когда не установлено ничего, выбранной модели нет.
     *
     * @param currentSelection последний выбор пользователя в текущем UI-состоянии.
     * @param isGemmaInstalled установлена ли Gemma.
     * @param isVoskInstalled установлен ли Vosk.
     * @return установленная выбранная модель или `null`, если выбирать нечего.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun resolveSelectedInstalledModel(
        currentSelection: KsenaxTranscribingModel?,
        isGemmaInstalled: Boolean,
        isVoskInstalled: Boolean,
    ): KsenaxTranscribingModel? {
        return currentSelection
            ?.takeIf { model ->
                when (model) {
                    KsenaxTranscribingModel.Gemma -> isGemmaInstalled
                    KsenaxTranscribingModel.Vosk  -> isVoskInstalled
                }
            }
            ?: when {
                isVoskInstalled  -> KsenaxTranscribingModel.Vosk
                isGemmaInstalled -> KsenaxTranscribingModel.Gemma
                else             -> null
            }
    }

    /**
     * Возвращает модель транскрипции, которую нужно использовать для новой записи голоса.
     *
     * Если пользователь уже выбрал модель вручную, метод возвращает её. Если явного выбора
     * ещё нет, применяется fallback-логика: сначала выбирается установленная Vosk-модель,
     * затем установленная Gemma-модель, а если ни одна модель не установлена, возвращается
     * Vosk как предпочтительный вариант для предложения установки.
     *
     * Метод не проверяет готовность файлов модели сам по себе, а опирается только на флаги
     * [KsenaxMainUiState.isVoskInstalled] и [KsenaxMainUiState.isGemmaInstalled].
     *
     * @param uiState текущее состояние главного экрана.
     * @return модель транскрипции, которую следует попробовать использовать для записи.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun resolveTranscribingModelForVoiceRecording(
        uiState: KsenaxMainUiState
    ): KsenaxTranscribingModel {
        return resolveSelectedInstalledModel(
            currentSelection = uiState.selectedTranscribingModel,
            isGemmaInstalled = uiState.isGemmaInstalled,
            isVoskInstalled = uiState.isVoskInstalled,
        ) ?: KsenaxTranscribingModel.Vosk
    }

    /**
     * Проверяет, считается ли указанная модель транскрипции установленной.
     *
     * Метод не обращается к файловой системе и не выполняет тяжёлую проверку модели.
     * Он только читает уже подготовленные флаги из [KsenaxMainUiState], поэтому подходит
     * для быстрых UI-решений: можно ли сразу начинать запись или нужно показать сценарий
     * установки модели.
     *
     * @param uiState текущее состояние главного экрана.
     * @param model модель транскрипции, которую нужно проверить.
     * @return `true`, если выбранная модель отмечена как установленная в UI-состоянии.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun isInstalled(
        uiState: KsenaxMainUiState,
        model: KsenaxTranscribingModel
    ): Boolean {
        return when (model) {
            KsenaxTranscribingModel.Gemma -> uiState.isGemmaInstalled
            KsenaxTranscribingModel.Vosk  -> uiState.isVoskInstalled
        }
    }

    /**
     * Возвращает цель установки, соответствующую выбранной модели транскрипции.
     *
     * Метод нужен для перехода от пользовательской настройки транскрипции к download/install
     * слою приложения. Например, если пользователь выбрал Vosk, но Vosk ещё не установлен,
     * ViewModel может получить через этот метод [KsenaxInstallOverlayTarget.VoskSmallRu] и
     * открыть правильный экран или overlay установки.
     *
     * @param model модель транскрипции, для которой нужно определить install-target.
     * @return цель установки, соответствующая указанной модели.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun installOverlayTargetFor(
        model: KsenaxTranscribingModel
    ): KsenaxInstallOverlayTarget {
        return when (model) {
            KsenaxTranscribingModel.Gemma -> KsenaxInstallOverlayTarget.Gemma4E2B
            KsenaxTranscribingModel.Vosk  -> KsenaxInstallOverlayTarget.VoskSmallRu
        }
    }
}
