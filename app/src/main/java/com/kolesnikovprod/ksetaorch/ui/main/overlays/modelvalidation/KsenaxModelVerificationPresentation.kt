package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

/**
 * Визуальный статус одной стадии проверки модели.
 *
 * @since 0.3
 */
enum class KsenaxModelVerificationStatus {
    Pending,
    Active,
    Success,
    Failure,
}

/**
 * Готовая presentation-модель трёх стадий validation-overlay.
 *
 * @since 0.3
 */
data class KsenaxModelVerificationUiState(
    val presence: KsenaxModelVerificationStatus,
    val integrity: KsenaxModelVerificationStatus,
    val reachability: KsenaxModelVerificationStatus,
    val supportingText: String,
) {
    internal val faceState: KsenaxModelVerificationFaceState
        get() = when {
            stages.any { stage ->
                stage.status == KsenaxModelVerificationStatus.Failure
            } -> KsenaxModelVerificationFaceState.Failure

            stages.all { stage ->
                stage.status == KsenaxModelVerificationStatus.Success
            } -> KsenaxModelVerificationFaceState.Success

            else -> KsenaxModelVerificationFaceState.InProcess
        }

    internal val stages: List<KsenaxModelVerificationStagePresentation>
        get() = listOf(
            KsenaxModelVerificationStagePresentation(
                pipelineLabel = "наличие",
                criterionLabel = "файлы",
                status = presence,
            ),
            KsenaxModelVerificationStagePresentation(
                pipelineLabel = "целостность",
                criterionLabel = "целостность",
                status = integrity,
            ),
            KsenaxModelVerificationStagePresentation(
                pipelineLabel = "достижимость",
                criterionLabel = "доступность",
                status = reachability,
            ),
        )
}

internal enum class KsenaxModelVerificationFaceState {
    InProcess,
    Failure,
    Success,
}

/**
 * Подписи и статус одной стадии в pipeline и criteria-panel.
 *
 * @since 0.3
 */
internal data class KsenaxModelVerificationStagePresentation(
    val pipelineLabel: String,
    val criterionLabel: String,
    val status: KsenaxModelVerificationStatus,
)
