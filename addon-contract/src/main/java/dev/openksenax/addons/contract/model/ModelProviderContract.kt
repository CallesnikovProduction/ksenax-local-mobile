package dev.openksenax.addons.contract.model

/**
 * Публичный IPC-контракт model provider.
 *
 * @since 0.3
 */
object ModelProviderContract {

    const val SERVICE_ACTION =
        "dev.openksenax.action.MODEL_PROVIDER"

    const val CURRENT_PROVIDER_API: Int = 1

    /**
     * Защита от превышения Binder transaction limit.
     *
     * Для действительно больших контекстов позже
     * понадобится ParcelFileDescriptor/streaming.
     *
     * @since 0.3
     */
    const val MAX_REQUEST_CHARACTERS: Int = 100_000

    /**
     * Максимальный текстовый payload ответа до перехода
     * на streaming или [android.os.ParcelFileDescriptor].
     *
     * @since 0.3
     */
    const val MAX_RESULT_CHARACTERS: Int = 100_000

    const val MAX_REQUEST_ID_CHARACTERS: Int = 128
    const val MAX_IDENTIFIER_CHARACTERS: Int = 200
    const val MAX_MESSAGES_PER_REQUEST: Int = 128

    /**
     * Ограничивает длину очереди перед одним shared model runtime.
     *
     * @since 0.3
     */
    const val MAX_CONCURRENT_REQUESTS: Int = 8

    /**
     * Не даёт одному addon UID вытеснить из очереди остальные
     * аддоны.
     *
     * @since 0.3
     */
    const val MAX_CONCURRENT_REQUESTS_PER_UID: Int = 2

    const val MAX_FAILURE_MESSAGE_CHARACTERS: Int = 2_048

    const val MIN_TIMEOUT_MILLIS: Long = 1_000L
    const val MAX_TIMEOUT_MILLIS: Long = 120_000L
}
