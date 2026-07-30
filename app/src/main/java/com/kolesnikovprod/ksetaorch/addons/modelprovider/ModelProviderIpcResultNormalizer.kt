package com.kolesnikovprod.ksetaorch.addons.modelprovider

import dev.openksenax.addons.contract.model.ModelFailureCode
import dev.openksenax.addons.contract.model.ModelGenerationResult
import dev.openksenax.addons.contract.model.ModelGenerationStatus
import dev.openksenax.addons.contract.model.ModelProviderContract
import kotlin.math.max

/**
 * Проверяет model-engine result перед Parcelable/Binder boundary.
 *
 * @since 0.3
 */
internal fun ModelGenerationResult.normalizedForIpc(
    requestId: String,
    elapsedMillis: Long,
): ModelGenerationResult {
    val normalizedMetrics = metrics?.let { current ->
        current.copy(
            queueTimeMillis = max(
                0L,
                elapsedMillis - current.inferenceTimeMillis,
            ),
        )
    }

    val normalized = copy(
        requestId = requestId,
        failureMessage = when (failureCode) {
            ModelFailureCode.ENGINE_FAILURE ->
                "Model generation failed"

            else -> failureMessage.limitedProviderFailureMessage()
        },
        metrics = normalizedMetrics,
    )

    val invalidMetrics = normalized.metrics?.let { value ->
        value.queueTimeMillis < 0L ||
                value.inferenceTimeMillis < 0L ||
                value.inputTokenCount?.let { it < 0 } == true ||
                value.outputTokenCount?.let { it < 0 } == true
    } == true

    val malformed = when (normalized.status) {
        ModelGenerationStatus.SUCCESS ->
            normalized.text == null ||
                    normalized.failureCode != null ||
                    normalized.failureMessage != null ||
                    normalized.retryable

        ModelGenerationStatus.FAILURE ->
            normalized.failureCode == null ||
                    normalized.text != null

        ModelGenerationStatus.UNKNOWN -> true
    } || invalidMetrics

    if (malformed) {
        return ModelGenerationResult.failure(
            requestId = requestId,
            code = ModelFailureCode.ENGINE_FAILURE,
            message = "Model engine returned malformed result",
        )
    }

    val resultCharacters =
        normalized.text.orEmpty().length +
                normalized.modelId.orEmpty().length +
                normalized.failureMessage.orEmpty().length

    if (
        resultCharacters >
        ModelProviderContract.MAX_RESULT_CHARACTERS
    ) {
        return ModelGenerationResult.failure(
            requestId = requestId,
            code = ModelFailureCode.RESULT_TOO_LARGE,
            message = "Model result exceeds IPC size limit",
        )
    }

    return normalized
}

internal fun String?.limitedProviderFailureMessage(): String? {
    return this?.take(
        ModelProviderContract.MAX_FAILURE_MESSAGE_CHARACTERS,
    )
}
