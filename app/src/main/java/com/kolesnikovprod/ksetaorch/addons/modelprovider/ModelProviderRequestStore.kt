package com.kolesnikovprod.ksetaorch.addons.modelprovider

import android.os.IBinder
import dev.openksenax.addons.contract.model.ModelFailureCode
import dev.openksenax.addons.contract.model.ModelProviderContract
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job

/**
 * Ограниченная книга активных Model Provider запросов.
 *
 * Ключ включает UID, поэтому addon-ы могут независимо
 * использовать одинаковые requestId. Все check-and-register операции
 * атомарны относительно global/per-UID limits.
 *
 * @since 0.3
 */
internal class ModelProviderRequestStore {

    private val requests =
        ConcurrentHashMap<ModelProviderRequestKey, TrackedModelRequest>()

    private val registrationLock = Any()

    fun register(
        key: ModelProviderRequestKey,
        request: TrackedModelRequest,
    ): ModelProviderRegistrationFailure? {
        return synchronized(registrationLock) {
            when {
                requests.containsKey(key) ->
                    ModelProviderRegistrationFailure(
                        code = ModelFailureCode.DUPLICATE_REQUEST_ID,
                        message =
                            "requestId is already running for this addon UID",
                        retryable = false,
                    )

                requests.size >=
                        ModelProviderContract.MAX_CONCURRENT_REQUESTS ->
                    ModelProviderRegistrationFailure(
                        code = ModelFailureCode.TOO_MANY_REQUESTS,
                        message = "Model Provider queue is full",
                        retryable = true,
                    )

                requests.values.count { running ->
                    running.callingUid == request.callingUid
                } >= ModelProviderContract
                    .MAX_CONCURRENT_REQUESTS_PER_UID ->
                    ModelProviderRegistrationFailure(
                        code = ModelFailureCode.TOO_MANY_REQUESTS,
                        message =
                            "Addon has too many active requests",
                        retryable = true,
                    )

                else -> {
                    requests[key] = request
                    null
                }
            }
        }
    }

    fun jobFor(key: ModelProviderRequestKey): Job? {
        return requests[key]?.job
    }

    fun complete(
        key: ModelProviderRequestKey,
        request: TrackedModelRequest,
    ) {
        if (requests.remove(key, request)) {
            request.unlinkDeathRecipient()
        }
    }

    fun drain(): List<TrackedModelRequest> {
        return synchronized(registrationLock) {
            requests.values.toList().also {
                requests.clear()
            }
        }
    }
}

internal data class ModelProviderRegistrationFailure(
    val code: ModelFailureCode,
    val message: String,
    val retryable: Boolean,
)

internal data class ModelProviderRequestKey(
    val callingUid: Int,
    val requestId: String,
)

internal data class TrackedModelRequest(
    val callingUid: Int,
    val job: Job,
    val callbackBinder: IBinder,
    val deathRecipient: IBinder.DeathRecipient,
) {
    fun unlinkDeathRecipient() {
        try {
            callbackBinder.unlinkToDeath(
                deathRecipient,
                0,
            )
        } catch (_: NoSuchElementException) {
            // Callback died before linkToDeath completed.
        }
    }
}
