package dev.openksenax.addons.contract.model

import android.os.Parcel
import android.os.Parcelable
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId

/**
 * Запрос аддона к Model Provider.
 *
 * addonId дополнительно проверяется через Binder calling UID.
 * Простого значения addonId недостаточно для авторизации.
 *
 * API 1 поддерживает только обычную text generation. Schema,
 * tool-messages, произвольные sampling-параметры и priority не
 * игнорируются: service явно отклоняет их до инференса.
 * Точное значение [ModelSamplingParameters.HOST_DEFAULT] означает,
 * что настройками управляет runtime OKx.
 *
 * @since 0.3
 */
data class ModelGenerationRequest(
    val requestId: String,
    val addonId: String,
    val capabilityId: String,
    val systemInstruction: String? = null,
    val messages: List<ModelMessage>,
    val outputSchemaJson: String? = null,
    val sampling: ModelSamplingParameters =
        ModelSamplingParameters.HOST_DEFAULT,
    val priority: ModelRequestPriority =
        ModelRequestPriority.FOREGROUND_AUTOMATION,
    val timeoutMillis: Long = 60_000L,
) : Parcelable {

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeString(requestId)
        parcel.writeString(addonId)
        parcel.writeString(capabilityId)
        parcel.writeString(systemInstruction)
        parcel.writeTypedList(messages)
        parcel.writeString(outputSchemaJson)
        sampling.writeToParcel(parcel, flags)
        parcel.writeString(priority.name)
        parcel.writeLong(timeoutMillis)
    }

    override fun describeContents(): Int = 0

    /**
     * Возвращает первую ошибку ABI-запроса или `null`.
     *
     * Авторизация caller-а сюда не входит.
     *
     * @since 0.3
     */
    fun validationError(): String? {
        return validationIssue()?.message
    }

    companion object {

        /** @since 0.3 */
        @JvmField
        val CREATOR: Parcelable.Creator<ModelGenerationRequest> =
            object : Parcelable.Creator<ModelGenerationRequest> {
                override fun createFromParcel(
                    parcel: Parcel,
                ): ModelGenerationRequest {
                    return ModelGenerationRequest(
                        requestId = parcel.readString().orEmpty(),
                        addonId = parcel.readString().orEmpty(),
                        capabilityId = parcel.readString().orEmpty(),
                        systemInstruction = parcel.readString(),
                        messages = parcel.createTypedArrayList(
                            ModelMessage.CREATOR,
                        ).orEmpty(),
                        outputSchemaJson = parcel.readString(),
                        sampling = ModelSamplingParameters.CREATOR
                            .createFromParcel(parcel),
                        priority = ModelRequestPriority.fromWireValue(
                            parcel.readString(),
                        ),
                        timeoutMillis = parcel.readLong(),
                    )
                }

                override fun newArray(
                    size: Int,
                ): Array<ModelGenerationRequest?> =
                    arrayOfNulls(size)
            }
    }
}

/**
 * Одно сообщение stateless text-generation диалога.
 *
 * @since 0.3
 */
data class ModelMessage(
    val role: ModelMessageRole,
    val content: String,
) : Parcelable {

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeString(role.name)
        parcel.writeString(content)
    }

    override fun describeContents(): Int = 0

    companion object {

        /** @since 0.3 */
        @JvmField
        val CREATOR: Parcelable.Creator<ModelMessage> =
            object : Parcelable.Creator<ModelMessage> {
                override fun createFromParcel(
                    parcel: Parcel,
                ): ModelMessage {
                    return ModelMessage(
                        role = ModelMessageRole.fromWireValue(
                            parcel.readString(),
                        ),
                        content = parcel.readString().orEmpty(),
                    )
                }

                override fun newArray(
                    size: Int,
                ): Array<ModelMessage?> = arrayOfNulls(size)
            }
    }
}

/**
 * Роль сообщения внутри одноразового запроса.
 *
 * [TOOL] зарезервирован для будущего API и отклоняется API 1.
 *
 * @since 0.3
 */
enum class ModelMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
    UNKNOWN,
    ;

    companion object {
        /** @since 0.3 */
        fun fromWireValue(value: String?): ModelMessageRole {
            return entries.firstOrNull { role ->
                role.name == value
            } ?: UNKNOWN
        }
    }
}

/**
 * Зарезервированные sampling-параметры IPC.
 *
 * API 1 принимает только [HOST_DEFAULT]. Любой другой набор
 * отклоняется как неподдерживаемый, пока model-session не умеет
 * применять параметры на один запрос.
 *
 * @since 0.3
 */
data class ModelSamplingParameters(
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    val maximumOutputTokens: Int = 512,
) : Parcelable {

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeFloat(temperature)
        parcel.writeFloat(topP)
        parcel.writeInt(maximumOutputTokens)
    }

    override fun describeContents(): Int = 0

    companion object {

        /**
         * Маркер того, что sampling определяет host runtime.
         *
         * @since 0.3
         */
        val HOST_DEFAULT: ModelSamplingParameters =
            ModelSamplingParameters()

        /** @since 0.3 */
        @JvmField
        val CREATOR: Parcelable.Creator<ModelSamplingParameters> =
            object : Parcelable.Creator<ModelSamplingParameters> {
                override fun createFromParcel(
                    parcel: Parcel,
                ): ModelSamplingParameters {
                    return ModelSamplingParameters(
                        temperature = parcel.readFloat(),
                        topP = parcel.readFloat(),
                        maximumOutputTokens = parcel.readInt(),
                    )
                }

                override fun newArray(
                    size: Int,
                ): Array<ModelSamplingParameters?> =
                    arrayOfNulls(size)
            }
    }
}

/**
 * Приоритет запроса, зарезервированный в IPC API.
 *
 * API 1 ещё не имеет priority scheduler и принимает только
 * [FOREGROUND_AUTOMATION].
 *
 * @since 0.3
 */
enum class ModelRequestPriority {
    USER_INTERACTIVE,
    FOREGROUND_AUTOMATION,
    BACKGROUND_BATCH,
    UNKNOWN,
    ;

    companion object {
        /** @since 0.3 */
        fun fromWireValue(value: String?): ModelRequestPriority {
            return entries.firstOrNull { priority ->
                priority.name == value
            } ?: UNKNOWN
        }
    }
}

/**
 * Первая типизированная проблема локальной проверки model-запроса.
 *
 * @since 0.3
 */
data class ModelRequestValidationIssue(
    val kind: ModelRequestValidationKind,
    val message: String,
)

/**
 * Категория отказа до Binder-вызова или model inference.
 *
 * @since 0.3
 */
enum class ModelRequestValidationKind {
    INVALID,
    UNSUPPORTED,
}

/**
 * Возвращает первую проблему wire-запроса или `null`.
 *
 * Проверка caller identity сюда не входит.
 *
 * @since 0.3
 */
fun ModelGenerationRequest.validationIssue():
        ModelRequestValidationIssue? {
    fun invalid(message: String) =
        ModelRequestValidationIssue(
            ModelRequestValidationKind.INVALID,
            message,
        )

    fun unsupported(message: String) =
        ModelRequestValidationIssue(
            ModelRequestValidationKind.UNSUPPORTED,
            message,
        )

    if (requestId.isBlank()) {
        return invalid("requestId is blank")
    }

    if (
        requestId.length >
        ModelProviderContract.MAX_REQUEST_ID_CHARACTERS
    ) {
        return invalid("requestId is too long")
    }

    if (
        addonId.length >
        ModelProviderContract.MAX_IDENTIFIER_CHARACTERS ||
        runCatching { AddonId(addonId) }.isFailure
    ) {
        return invalid("addonId is invalid")
    }

    if (
        capabilityId.length >
        ModelProviderContract.MAX_IDENTIFIER_CHARACTERS ||
        runCatching {
            HostCapabilityId(capabilityId)
        }.isFailure
    ) {
        return invalid("capabilityId is invalid")
    }

    if (messages.isEmpty()) {
        return invalid("messages are empty")
    }

    if (
        messages.size >
        ModelProviderContract.MAX_MESSAGES_PER_REQUEST
    ) {
        return invalid("too many messages")
    }

    if (messages.any { message -> message.content.isBlank() }) {
        return invalid("message content is blank")
    }

    if (messages.any { message -> message.role == ModelMessageRole.UNKNOWN }) {
        return invalid("message role is unknown")
    }

    if (
        messages.none { message ->
            message.role != ModelMessageRole.SYSTEM
        }
    ) {
        return invalid("request has no generation input")
    }

    val totalCharacters =
        requestId.length +
                addonId.length +
                capabilityId.length +
                systemInstruction.orEmpty().length +
                outputSchemaJson.orEmpty().length +
                messages.sumOf { message ->
                    message.content.length
                }

    if (
        totalCharacters >
        ModelProviderContract.MAX_REQUEST_CHARACTERS
    ) {
        return invalid("request exceeds IPC size limit")
    }

    if (
        timeoutMillis !in
        ModelProviderContract.MIN_TIMEOUT_MILLIS..
        ModelProviderContract.MAX_TIMEOUT_MILLIS
    ) {
        return invalid("invalid timeoutMillis")
    }

    if (outputSchemaJson != null) {
        return unsupported(
            "outputSchemaJson is not supported by provider API 1",
        )
    }

    if (
        messages.any { message ->
            message.role == ModelMessageRole.TOOL
        }
    ) {
        return unsupported(
            "TOOL messages are not supported by provider API 1",
        )
    }

    if (sampling != ModelSamplingParameters.HOST_DEFAULT) {
        return unsupported(
            "custom sampling is not supported by provider API 1",
        )
    }

    if (
        priority !=
        ModelRequestPriority.FOREGROUND_AUTOMATION
    ) {
        return unsupported(
            "custom request priority is not supported by provider API 1",
        )
    }

    return null
}
