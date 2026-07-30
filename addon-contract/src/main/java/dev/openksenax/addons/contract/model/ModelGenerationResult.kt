package dev.openksenax.addons.contract.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Единый Parcelable-результат вместо sealed AIDL-иерархии.
 *
 * Такой формат проще версионировать между APK.
 *
 * @since 0.3
 */
data class ModelGenerationResult(
    val requestId: String,
    val status: ModelGenerationStatus,
    val text: String? = null,
    val modelId: String? = null,
    val failureCode: ModelFailureCode? = null,
    val failureMessage: String? = null,
    val retryable: Boolean = false,
    val metrics: ModelGenerationMetrics? = null,
) : Parcelable {

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeString(requestId)
        parcel.writeString(status.name)
        parcel.writeString(text)
        parcel.writeString(modelId)
        parcel.writeString(failureCode?.name)
        parcel.writeString(failureMessage)
        parcel.writeInt(if (retryable) 1 else 0)
        parcel.writeInt(if (metrics == null) 0 else 1)
        metrics?.writeToParcel(parcel, flags)
    }

    override fun describeContents(): Int = 0

    companion object {

        /**
         * Создаёт типизированный отказ IPC без exception-контракта.
         *
         * @since 0.3
         */
        fun failure(
            requestId: String,
            code: ModelFailureCode,
            message: String? = null,
            retryable: Boolean = false,
        ): ModelGenerationResult {
            return ModelGenerationResult(
                requestId = requestId,
                status = ModelGenerationStatus.FAILURE,
                failureCode = code,
                failureMessage = message,
                retryable = retryable,
            )
        }

        /** @since 0.3 */
        @JvmField
        val CREATOR: Parcelable.Creator<ModelGenerationResult> =
            object : Parcelable.Creator<ModelGenerationResult> {
                override fun createFromParcel(
                    parcel: Parcel,
                ): ModelGenerationResult {
                    return ModelGenerationResult(
                        requestId = parcel.readString().orEmpty(),
                        status = ModelGenerationStatus.fromWireValue(
                            parcel.readString(),
                        ),
                        text = parcel.readString(),
                        modelId = parcel.readString(),
                        failureCode = parcel.readString()?.let(
                            ModelFailureCode::fromWireValue,
                        ),
                        failureMessage = parcel.readString(),
                        retryable = parcel.readInt() != 0,
                        metrics = if (parcel.readInt() == 0) {
                            null
                        } else {
                            ModelGenerationMetrics.CREATOR
                                .createFromParcel(parcel)
                        },
                    )
                }

                override fun newArray(
                    size: Int,
                ): Array<ModelGenerationResult?> =
                    arrayOfNulls(size)
            }
    }
}

/** @since 0.3 */
enum class ModelGenerationStatus {
    SUCCESS,
    FAILURE,
    UNKNOWN,
    ;

    companion object {
        /** @since 0.3 */
        fun fromWireValue(value: String?): ModelGenerationStatus {
            return entries.firstOrNull { status ->
                status.name == value
            } ?: UNKNOWN
        }
    }
}

/**
 * Стабильные коды завершения IPC-запроса.
 *
 * @since 0.3
 */
enum class ModelFailureCode {
    INVALID_REQUEST,
    UNSUPPORTED_REQUEST_FEATURE,
    UNAUTHORIZED_CALLER,
    ADDON_NOT_TRUSTED,
    ADDON_INCOMPATIBLE,
    CAPABILITY_NOT_DECLARED,
    CAPABILITY_UNAVAILABLE,
    PROVIDER_NOT_READY,
    DUPLICATE_REQUEST_ID,
    TOO_MANY_REQUESTS,
    TIMEOUT,
    CANCELLED,
    RESULT_TOO_LARGE,
    ENGINE_FAILURE,
    UNKNOWN,
    ;

    companion object {
        /** @since 0.3 */
        fun fromWireValue(value: String): ModelFailureCode {
            return entries.firstOrNull { code ->
                code.name == value
            } ?: UNKNOWN
        }
    }
}

/**
 * Метрики одного model-provider запроса.
 *
 * @since 0.3
 */
data class ModelGenerationMetrics(
    val queueTimeMillis: Long,
    val inferenceTimeMillis: Long,
    val inputTokenCount: Int?,
    val outputTokenCount: Int?,
) : Parcelable {

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeLong(queueTimeMillis)
        parcel.writeLong(inferenceTimeMillis)
        parcel.writeNullableInt(inputTokenCount)
        parcel.writeNullableInt(outputTokenCount)
    }

    override fun describeContents(): Int = 0

    companion object {

        /** @since 0.3 */
        @JvmField
        val CREATOR: Parcelable.Creator<ModelGenerationMetrics> =
            object : Parcelable.Creator<ModelGenerationMetrics> {
                override fun createFromParcel(
                    parcel: Parcel,
                ): ModelGenerationMetrics {
                    return ModelGenerationMetrics(
                        queueTimeMillis = parcel.readLong(),
                        inferenceTimeMillis = parcel.readLong(),
                        inputTokenCount = parcel.readNullableInt(),
                        outputTokenCount = parcel.readNullableInt(),
                    )
                }

                override fun newArray(
                    size: Int,
                ): Array<ModelGenerationMetrics?> =
                    arrayOfNulls(size)
            }
    }
}

private fun Parcel.writeNullableInt(value: Int?) {
    writeInt(if (value == null) 0 else 1)
    value?.let(::writeInt)
}

private fun Parcel.readNullableInt(): Int? {
    return if (readInt() == 0) null else readInt()
}
