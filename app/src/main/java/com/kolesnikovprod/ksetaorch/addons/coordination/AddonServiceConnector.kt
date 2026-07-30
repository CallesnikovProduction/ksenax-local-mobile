package com.kolesnikovprod.ksetaorch.addons.coordination

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import dev.openksenax.addons.contract.AddonManifestContract
import dev.openksenax.addons.contract.management.AddonCommandResult
import dev.openksenax.addons.contract.management.AddonCommandStatus
import dev.openksenax.addons.contract.management.AddonManagementContract
import dev.openksenax.addons.contract.management.AddonManagementFailureCode
import dev.openksenax.addons.contract.management.AddonManagementOperation
import dev.openksenax.addons.contract.management.AddonManagementResponse
import dev.openksenax.addons.contract.management.AddonRuntimeStatus
import dev.openksenax.addons.contract.management.AddonRuntimeState
import dev.openksenax.addons.contract.management.IAddonManagementCallback
import dev.openksenax.addons.contract.management.IOpenKsenaxAddonManagement
import com.kolesnikovprod.ksetaorch.addons.registry.AddonManagementEndpoint
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Создаёт временное Binder-подключение к management service аддона.
 *
 * @since 0.3
 */
internal interface AddonServiceConnector {

    suspend fun connect(
        endpoint: AddonManagementEndpoint,
    ): AddonServiceSession
}

/**
 * Ограниченная временем жизни Binder-сессия с addon management API.
 *
 * Все команды являются асинхронными oneway-вызовами. Binder thread OKx не
 * ждёт, пока аддон подготовит статус, изменит runtime или создаст свой UI.
 *
 * @since 0.3
 */
internal class AddonServiceSession(
    private val management: IOpenKsenaxAddonManagement,
    private val context: Context,
    private val connection: ServiceConnection,
    private val expectedPackageName: String,
    private val expectedUid: Int,
    private val requestTimeoutMillis: Long,
) : Closeable {

    private val closed = AtomicBoolean(false)

    suspend fun requestRuntimeStatus(): AddonRuntimeStatus {
        val response = request(AddonManagementOperation.RUNTIME_STATUS) {
                requestId,
                callback,
            ->
            management.requestRuntimeStatus(requestId, callback)
        }
        return response.runtimeStatus
            ?.takeIf {
                response.commandResult == null &&
                    response.uiEntryPoint == null &&
                    it.state != AddonRuntimeState.UNKNOWN
            }
            ?: throw AddonManagementRequestException.InvalidResponse(
                "Runtime status response has invalid payload",
            )
    }

    suspend fun requestSetEnabled(
        enabled: Boolean,
    ): AddonSetEnabledResponse {
        val response = request(AddonManagementOperation.SET_ENABLED) {
                requestId,
                callback,
            ->
            management.requestSetEnabled(requestId, enabled, callback)
        }
        val commandResult = response.commandResult
            ?.takeIf {
                response.uiEntryPoint == null &&
                    it.status != AddonCommandStatus.UNKNOWN &&
                    response.runtimeStatus?.state !=
                        AddonRuntimeState.UNKNOWN
            }
            ?: throw AddonManagementRequestException.InvalidResponse(
                "Set-enabled response has invalid payload",
            )
        val runtimeStatus = response.runtimeStatus
        if (
            commandResult.status.isSuccessful &&
            runtimeStatus == null
        ) {
            throw AddonManagementRequestException.InvalidResponse(
                "Successful set-enabled response has no runtime status",
            )
        }
        return AddonSetEnabledResponse(
            commandResult = commandResult,
            runtimeStatus = runtimeStatus,
        )
    }

    suspend fun requestUiEntryPoint(): PendingIntent {
        val response = request(AddonManagementOperation.UI_ENTRY_POINT) {
                requestId,
                callback,
            ->
            management.requestUiEntryPoint(requestId, callback)
        }
        return response.uiEntryPoint
            ?.takeIf {
                response.commandResult == null &&
                    response.runtimeStatus == null
            }
            ?.takeIf(::isTrustedUiEntryPoint)
            ?: throw AddonManagementRequestException.InvalidResponse(
                "UI entry-point response has invalid payload",
            )
    }

    private suspend fun request(
        operation: AddonManagementOperation,
        send: (
            requestId: String,
            callback: IAddonManagementCallback,
        ) -> Unit,
    ): AddonManagementResponse {
        check(!closed.get()) {
            "Addon management session is already closed"
        }

        val requestId = UUID.randomUUID().toString()
        return withTimeoutOrNull(requestTimeoutMillis.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                val binder = management.asBinder()
                lateinit var deathRecipient: IBinder.DeathRecipient

                fun unlinkDeathRecipient() {
                    try {
                        binder.unlinkToDeath(deathRecipient, 0)
                    } catch (_: RuntimeException) {
                        // Binder уже мог завершиться или быть отвязан.
                    }
                }

                fun cancelRemoteRequest() {
                    try {
                        management.cancel(requestId)
                    } catch (_: Exception) {
                        // Best-effort cancellation of an already dead peer.
                    }
                }

                fun fail(error: Throwable) {
                    if (completed.compareAndSet(false, true)) {
                        unlinkDeathRecipient()
                        continuation.resumeWithException(error)
                    }
                }

                deathRecipient = IBinder.DeathRecipient {
                    fail(AddonConnectionException.ConnectionLost())
                }

                val callback = object : IAddonManagementCallback.Stub() {
                    override fun onResult(
                        response: AddonManagementResponse?,
                    ) {
                        if (!completed.compareAndSet(false, true)) return

                        unlinkDeathRecipient()
                        if (response == null) {
                            cancelRemoteRequest()
                            continuation.resumeWithException(
                                AddonManagementRequestException
                                    .InvalidResponse(
                                        "Management callback returned null",
                                    ),
                            )
                            return
                        }
                        val error = response.validationError(
                            expectedRequestId = requestId,
                            expectedOperation = operation,
                        )
                        if (error != null) {
                            cancelRemoteRequest()
                            continuation.resumeWithException(error)
                        } else {
                            continuation.resume(response)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(false, true)) {
                        unlinkDeathRecipient()
                        cancelRemoteRequest()
                    }
                }

                try {
                    binder.linkToDeath(deathRecipient, 0)
                    send(requestId, callback)
                } catch (error: Exception) {
                    fail(error)
                }
            }
        } ?: throw AddonManagementRequestException.Timeout(
            requestTimeoutMillis,
        )
    }

    private fun isTrustedUiEntryPoint(
        pendingIntent: PendingIntent,
    ): Boolean {
        if (pendingIntent.creatorPackage != expectedPackageName) return false
        if (pendingIntent.creatorUid != expectedUid) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!pendingIntent.isActivity) return false
            if (!pendingIntent.isImmutable) return false
        }
        return true
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.unbindSafely(connection)
        }
    }
}

/**
 * Ошибки установления Binder-соединения с management service.
 *
 * @since 0.3
 */
internal sealed class AddonConnectionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class BindingRejected :
        AddonConnectionException(
            "Android rejected addon service binding",
        )

    class NullBinding :
        AddonConnectionException(
            "Addon service returned a null binding",
        )

    class ManagementApiMismatch(
        val receivedVersion: Int,
    ) : AddonConnectionException(
        "Unsupported addon management API: $receivedVersion",
    )

    class ConnectionLost :
        AddonConnectionException(
            "Addon service connection was lost",
        )

    class Timeout(timeoutMillis: Long) :
        AddonConnectionException(
            "Addon service connection timed out after ${timeoutMillis}ms",
        )
}

/**
 * Ошибка уже установленного management-соединения.
 *
 * @since 0.3
 */
internal sealed class AddonManagementRequestException(
    message: String,
) : Exception(message) {

    class InvalidResponse(message: String) :
        AddonManagementRequestException(message)

    class RemoteFailure(
        val code: AddonManagementFailureCode,
        detail: String?,
    ) : AddonManagementRequestException(
        detail ?: "Addon rejected management request: $code",
    )

    class Timeout(timeoutMillis: Long) :
        AddonManagementRequestException(
            "Addon management request timed out after ${timeoutMillis}ms",
        )
}

/**
 * Результат команды изменения runtime вместе с подтверждённым состоянием.
 *
 * @since 0.3
 */
internal data class AddonSetEnabledResponse(
    val commandResult: AddonCommandResult,
    val runtimeStatus: AddonRuntimeStatus?,
)

/**
 * Android Binder-реализация connector-порта.
 *
 * @since 0.3
 */
internal class AndroidAddonServiceConnector(
    context: Context,
    private val connectionTimeoutMillis: Long = 10_000L,
    private val requestTimeoutMillis: Long = 15_000L,
) : AddonServiceConnector {

    private val applicationContext = context.applicationContext

    override suspend fun connect(
        endpoint: AddonManagementEndpoint,
    ): AddonServiceSession {
        if (
            endpoint.managementApiVersion !=
            AddonManagementContract.CURRENT_API_VERSION
        ) {
            throw AddonConnectionException.ManagementApiMismatch(
                endpoint.managementApiVersion,
            )
        }

        return withTimeoutOrNull(connectionTimeoutMillis.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val bindingCompleted = AtomicBoolean(false)

                lateinit var serviceConnection: ServiceConnection

                fun fail(error: Throwable) {
                    if (bindingCompleted.compareAndSet(false, true)) {
                        continuation.resumeWithException(error)
                    }
                }

                serviceConnection = object : ServiceConnection {

                    override fun onServiceConnected(
                        name: ComponentName,
                        service: IBinder,
                    ) {
                        if (
                            !bindingCompleted.compareAndSet(false, true)
                        ) {
                            return
                        }

                        val management =
                            IOpenKsenaxAddonManagement.Stub
                                .asInterface(service)
                        continuation.resume(
                            AddonServiceSession(
                                management = management,
                                context = applicationContext,
                                connection = this,
                                expectedPackageName = endpoint.packageName,
                                expectedUid = endpoint.uid,
                                requestTimeoutMillis = requestTimeoutMillis,
                            ),
                        )
                    }

                    override fun onServiceDisconnected(
                        name: ComponentName,
                    ) {
                        fail(AddonConnectionException.ConnectionLost())
                    }

                    override fun onNullBinding(name: ComponentName) {
                        applicationContext.unbindSafely(this)
                        fail(AddonConnectionException.NullBinding())
                    }
                }

                val intent = Intent(
                    AddonManifestContract.ADDON_SERVICE_ACTION,
                ).apply {
                    component = ComponentName(
                        endpoint.packageName,
                        endpoint.serviceClassName,
                    )
                }

                val bindingAccepted = try {
                    applicationContext.bindService(
                        intent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE,
                    )
                } catch (error: Exception) {
                    fail(error)
                    false
                }

                if (!bindingAccepted) {
                    fail(AddonConnectionException.BindingRejected())
                }

                continuation.invokeOnCancellation {
                    applicationContext.unbindSafely(serviceConnection)
                }
            }
        } ?: throw AddonConnectionException.Timeout(
            connectionTimeoutMillis,
        )
    }
}

private fun AddonManagementResponse.validationError(
    expectedRequestId: String,
    expectedOperation: AddonManagementOperation,
): AddonManagementRequestException? {
    if (requestId != expectedRequestId) {
        return AddonManagementRequestException.InvalidResponse(
            "Management callback requestId does not match",
        )
    }
    if (operation != expectedOperation) {
        return AddonManagementRequestException.InvalidResponse(
            "Management callback operation does not match",
        )
    }
    failureCode?.let { code ->
        if (code == AddonManagementFailureCode.UNKNOWN) {
            return AddonManagementRequestException.InvalidResponse(
                "Management callback contains unknown failure code",
            )
        }
        if (
            commandResult != null ||
            runtimeStatus != null ||
            uiEntryPoint != null
        ) {
            return AddonManagementRequestException.InvalidResponse(
                "Failure response must not contain a success payload",
            )
        }
        return AddonManagementRequestException.RemoteFailure(
            code = code,
            detail = failureMessage,
        )
    }
    if (failureMessage != null) {
        return AddonManagementRequestException.InvalidResponse(
            "Successful response must not contain failureMessage",
        )
    }
    return null
}

private val AddonCommandStatus.isSuccessful: Boolean
    get() =
        this == AddonCommandStatus.SUCCESS ||
            this == AddonCommandStatus.ALREADY_IN_REQUESTED_STATE

private fun Context.unbindSafely(connection: ServiceConnection) {
    try {
        unbindService(connection)
    } catch (_: RuntimeException) {
        // Binding уже мог быть снят системой или соседней callback-веткой.
    }
}
