package com.kolesnikovprod.ksetaorch.addons.modelprovider

import dev.openksenax.addons.contract.HostCapabilityId

/**
 * Внутренний порт model runtime OKx.
 *
 * Реализация может использовать LiteRT-LM, Gemma,
 * FunctionGemma или другой backend.
 *
 * IPC service не должен знать детали конкретной модели.
 *
 * @since 0.3
 */
interface ModelGenerationEngine {

    /**
     * Capabilities, для которых host содержит реализацию.
     *
     * Это структурная характеристика Provider и вход для registry compatibility.
     * Временное отсутствие model-файла не должно отнимать уже вычисленный
     * registry grant. Фактическую готовность конкретного runtime описывает
     * [availableCapabilities].
     *
     * @since 0.3
     */
    val supportedCapabilities: Set<HostCapabilityId>

    /**
     * Capabilities, которые готовы принять запрос прямо сейчас.
     *
     * @since 0.3
     */
    val availableCapabilities: Set<HostCapabilityId>

    /**
     * Выполняет уже проверенный provider-запрос.
     *
     * @since 0.3
     */
    suspend fun generate(
        command: AuthorizedModelGenerationCommand,
    ): ModelGenerationEngineResult
}

/**
 * Runtime перестал удовлетворять capability между authorization и inference.
 *
 * Provider преобразует эту внутреннюю ошибку в
 * [dev.openksenax.addons.contract.model.ModelFailureCode.PROVIDER_NOT_READY],
 * не раскрывая addon-у внутренние пути и детали model runtime.
 *
 * @since 0.3
 */
internal class ModelGenerationEngineNotReadyException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
