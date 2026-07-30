package com.kolesnikovprod.ksetaorch.addons.modelprovider

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId
import dev.openksenax.addons.contract.model.IModelGenerationCallback
import dev.openksenax.addons.contract.model.IOpenKsenaxModelProvider
import dev.openksenax.addons.contract.model.ModelFailureCode
import dev.openksenax.addons.contract.model.ModelGenerationMetrics
import dev.openksenax.addons.contract.model.ModelGenerationRequest
import dev.openksenax.addons.contract.model.ModelGenerationResult
import dev.openksenax.addons.contract.model.ModelGenerationStatus
import dev.openksenax.addons.contract.model.ModelProviderContract
import dev.openksenax.addons.contract.model.ModelRequestValidationIssue
import dev.openksenax.addons.contract.model.ModelRequestValidationKind
import dev.openksenax.addons.contract.model.validationIssue
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Application-owned зависимости Model Provider.
 *
 * Интерфейс внутренний: addon APK видит только AIDL-контракт,
 * а не host composition root и model-session.
 *
 * @since 0.3
 */
internal interface ModelProviderDependencies {

    val addonRegistry: AddonRegistry

    val modelGenerationEngine: ModelGenerationEngine
}

/**
 * Bound service, через который доверенные addon APK используют
 * общую локальную model-session OKx.
 *
 * Service сопоставляет Binder UID с registry-owned identity и не
 * пересчитывает trust, certificate и compatibility. Запросы
 * идентифицируются парой `(callingUid, requestId)`.
 *
 * @since 0.3
 */
class OpenKsenaxModelProviderService : Service() {

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default,
        )

    private val requestStore = ModelProviderRequestStore()

    private val dependencies: ModelProviderDependencies
        get() =
            application as? ModelProviderDependencies
                ?: error(
                    "Application must implement " +
                            "ModelProviderDependencies",
                )

    private val callerAuthorizer by lazy {
        ModelProviderCallerAuthorizer(
            packageManager = packageManager,
            registry = dependencies.addonRegistry,
            availableCapabilities = {
                dependencies.modelGenerationEngine
                    .availableCapabilities
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder.takeIf {
            intent?.action ==
                    ModelProviderContract.SERVICE_ACTION
        }
    }

    override fun onDestroy() {
        requestStore.drain().forEach { request ->
            request.unlinkDeathRecipient()
            request.job.cancel(
                CancellationException(
                    "Model Provider service was destroyed",
                ),
            )
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    private val binder =
        object : IOpenKsenaxModelProvider.Stub() {

            override fun getProviderApiVersion(): Int {
                callerAuthorizer.enforceAuthorizedCaller(
                    callingUid = Binder.getCallingUid(),
                )

                return ModelProviderContract
                    .CURRENT_PROVIDER_API
            }

            override fun getAvailableCapabilities():
                    Array<String> {
                return callerAuthorizer
                    .capabilitiesForCaller(
                        callingUid = Binder.getCallingUid(),
                    )
                    .map(HostCapabilityId::value)
                    .sorted()
                    .toTypedArray()
            }

            override fun generate(
                request: ModelGenerationRequest,
                callback: IModelGenerationCallback,
            ) {
                val callingUid = Binder.getCallingUid()
                val validationIssue = request.validationIssue()

                if (validationIssue != null) {
                    callback.safeComplete(
                        ModelGenerationResult.failure(
                            requestId = request.requestId,
                            code = validationIssue.toFailureCode(),
                            message = validationIssue.message,
                        ),
                    )
                    return
                }

                val addonId = AddonId(request.addonId)
                val capabilityId =
                    HostCapabilityId(request.capabilityId)
                val authorization = callerAuthorizer.authorize(
                    callingUid = callingUid,
                    claimedAddonId = addonId,
                    requestedCapability = capabilityId,
                )
                if (authorization is ModelProviderAuthorization.Rejected) {
                    callback.safeComplete(
                        ModelGenerationResult.failure(
                            requestId = request.requestId,
                            code = authorization.code,
                            message = authorization.message,
                            retryable = authorization.retryable,
                        ),
                    )
                    return
                }

                startGeneration(
                    callingUid = callingUid,
                    command = request.toAuthorizedCommand(
                        addonId = addonId,
                        capabilityId = capabilityId,
                    ),
                    callback = callback,
                )
            }

            override fun cancel(requestId: String) {
                if (requestId.isBlank()) return

                requestStore.jobFor(
                    ModelProviderRequestKey(
                        callingUid = Binder.getCallingUid(),
                        requestId = requestId,
                    ),
                )?.cancel(
                    CancellationException(
                        "Cancelled by addon",
                    ),
                )
            }
        }

    private fun startGeneration(
        callingUid: Int,
        command: AuthorizedModelGenerationCommand,
        callback: IModelGenerationCallback,
    ) {
        val key = ModelProviderRequestKey(
            callingUid = callingUid,
            requestId = command.requestId,
        )
        val acceptedAtNanos = System.nanoTime()
        val callbackBinder = callback.asBinder()

        lateinit var trackedRequest: TrackedModelRequest

        val job = serviceScope.launch(
            start = CoroutineStart.LAZY,
        ) {
            try {
                val outcome = withTimeout(
                    command.timeoutMillis,
                ) {
                    dependencies.modelGenerationEngine
                        .generate(command)
                }

                callback.safeComplete(
                    outcome.toIpcResult(
                        requestId = command.requestId,
                        elapsedMillis = elapsedMillisSince(
                            acceptedAtNanos,
                        ),
                    ),
                )
            } catch (_: TimeoutCancellationException) {
                callback.safeComplete(
                    ModelGenerationResult.failure(
                        requestId = command.requestId,
                        code = ModelFailureCode.TIMEOUT,
                        message = "Model generation timed out",
                        retryable = true,
                    ),
                )
            } catch (cancellation: CancellationException) {
                callback.safeComplete(
                    ModelGenerationResult.failure(
                        requestId = command.requestId,
                        code = ModelFailureCode.CANCELLED,
                        message = cancellation.message
                            .limitedProviderFailureMessage(),
                        retryable = true,
                    ),
                )
            } catch (_: ModelGenerationEngineNotReadyException) {
                callback.safeComplete(
                    ModelGenerationResult.failure(
                        requestId = command.requestId,
                        code = ModelFailureCode.PROVIDER_NOT_READY,
                        message =
                            "OpenKsenax text-generation model is not ready",
                        retryable = true,
                    ),
                )
            } catch (error: Exception) {
                Log.e(
                    LOG_TAG,
                    "Model generation failed for a trusted addon request",
                    error,
                )
                callback.safeComplete(
                    ModelGenerationResult.failure(
                        requestId = command.requestId,
                        code = ModelFailureCode.ENGINE_FAILURE,
                        // Не отдаём addon-у runtime path из exception message.
                        message = "Model generation failed",
                        retryable = true,
                    ),
                )
            } finally {
                requestStore.complete(
                    key = key,
                    request = trackedRequest,
                )
            }
        }

        val deathRecipient = IBinder.DeathRecipient {
            job.cancel(
                CancellationException(
                    "Addon callback process died",
                ),
            )
        }

        trackedRequest = TrackedModelRequest(
            callingUid = callingUid,
            job = job,
            callbackBinder = callbackBinder,
            deathRecipient = deathRecipient,
        )

        val registrationFailure = requestStore.register(
            key = key,
            request = trackedRequest,
        )

        if (registrationFailure != null) {
            job.cancel()
            callback.safeComplete(
                ModelGenerationResult.failure(
                    requestId = command.requestId,
                    code = registrationFailure.code,
                    message = registrationFailure.message,
                    retryable = registrationFailure.retryable,
                ),
            )
            return
        }

        try {
            callbackBinder.linkToDeath(deathRecipient, 0)
        } catch (_: RemoteException) {
            requestStore.complete(key, trackedRequest)
            job.cancel()
            return
        }

        if (!job.start()) {
            requestStore.complete(key, trackedRequest)
        }
    }

    private fun ModelGenerationEngineResult.toIpcResult(
        requestId: String,
        elapsedMillis: Long,
    ): ModelGenerationResult {
        return ModelGenerationResult(
            requestId = requestId,
            status = ModelGenerationStatus.SUCCESS,
            text = text,
            modelId = modelId,
            metrics = ModelGenerationMetrics(
                queueTimeMillis =
                    (elapsedMillis - inferenceTimeMillis).coerceAtLeast(0L),
                inferenceTimeMillis = inferenceTimeMillis,
                inputTokenCount = inputTokenCount,
                outputTokenCount = outputTokenCount,
            ),
        ).normalizedForIpc(
            requestId = requestId,
            elapsedMillis = elapsedMillis,
        )
    }

    private fun ModelRequestValidationIssue.toFailureCode():
            ModelFailureCode {
        return when (kind) {
            ModelRequestValidationKind.INVALID ->
                ModelFailureCode.INVALID_REQUEST

            ModelRequestValidationKind.UNSUPPORTED ->
                ModelFailureCode.UNSUPPORTED_REQUEST_FEATURE
        }
    }

    private fun IModelGenerationCallback.safeComplete(
        result: ModelGenerationResult,
    ) {
        try {
            onCompleted(result)
        } catch (_: RemoteException) {
            // Callback process disappeared; DeathRecipient cancels the job.
        } catch (_: RuntimeException) {
            // A local/malformed callback must not crash the host service.
        }
    }

    private fun elapsedMillisSince(startedAtNanos: Long): Long {
        return (System.nanoTime() - startedAtNanos) /
                NANOSECONDS_PER_MILLISECOND
    }

    private companion object {
        const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
        const val LOG_TAG = "OpenKsenaxModelProvider"
    }
}
