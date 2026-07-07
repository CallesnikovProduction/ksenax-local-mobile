package com.kolesnikovprod.ksetaorch.ui.helpers

import com.kolesnikovprod.ksetaorch.download.KsenaxModelInstallCoordinator
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget

/**
 * Хранит соответствие между install-target главного экрана и координатором установки.
 *
 * Селектор не запускает скачивание сам по себе. Он только отвечает на вопрос:
 * какой [KsenaxModelInstallCoordinator] должен обслуживать конкретную цель установки.
 *
 * Это нужно, чтобы ViewModel не содержала прямой when-маппинг между UI-target
 * и install-координаторами Gemma/Vosk.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxInstallCoordinatorSelector(
    private val gemmaCoordinator: KsenaxModelInstallCoordinator,
    private val functionGemmaCoordinator: KsenaxModelInstallCoordinator,
    private val voskCoordinator : KsenaxModelInstallCoordinator
) {

    /**
     * Возвращает координатор установки для указанной цели.
     *
     * @param target цель установки, выбранная в UI.
     * @return координатор, который умеет запускать, наблюдать и отменять установку этой модели.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    fun coordinatorFor(
        target: KsenaxInstallOverlayTarget,
    ): KsenaxModelInstallCoordinator {
        return when (target) {
            KsenaxInstallOverlayTarget.Gemma4E2B -> gemmaCoordinator
            KsenaxInstallOverlayTarget.FunctionGemma270M ->
                functionGemmaCoordinator
            KsenaxInstallOverlayTarget.VoskSmallRu -> voskCoordinator
        }
    }

    fun gemmaInitialSnapshot() = gemmaCoordinator.initialSnapshot()

    fun functionGemmaInitialSnapshot() =
        functionGemmaCoordinator.initialSnapshot()

    fun voskInitialSnapshot() = voskCoordinator.initialSnapshot()
}
