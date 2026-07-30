package com.kolesnikovprod.ksetaorch.addons.modelprovider

import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId
import dev.openksenax.addons.contract.model.ModelGenerationRequest
import dev.openksenax.addons.contract.model.ModelMessageRole
import dev.openksenax.addons.contract.model.validationIssue

/**
 * Проверенная и авторизованная host-команда model runtime.
 *
 * В отличие от Parcelable wire DTO, здесь идентификаторы типизированы, а
 * неподдерживаемые поля API уже отклонены service-слоем. Конструктор закрыт
 * внутри модуля: внешнее APK не может само присвоить запросу статус trusted.
 *
 * @since 0.3
 */
@ConsistentCopyVisibility
data class AuthorizedModelGenerationCommand internal constructor(
    val requestId: String,
    val addonId: AddonId,
    val capabilityId: HostCapabilityId,
    val systemInstruction: String?,
    val messages: List<AuthorizedModelMessage>,
    val timeoutMillis: Long,
)

/**
 * Host-domain сообщение после проверки IPC-запроса.
 *
 * @since 0.3
 */
@ConsistentCopyVisibility
data class AuthorizedModelMessage internal constructor(
    val role: AuthorizedModelMessageRole,
    val content: String,
)

/**
 * Поддерживаемые API 1 роли внутри host runtime.
 *
 * @since 0.3
 */
enum class AuthorizedModelMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

/**
 * Результат engine до преобразования в Parcelable IPC response.
 *
 * @since 0.3
 */
@ConsistentCopyVisibility
data class ModelGenerationEngineResult internal constructor(
    val text: String,
    val modelId: String?,
    val inferenceTimeMillis: Long,
    val inputTokenCount: Int? = null,
    val outputTokenCount: Int? = null,
)

internal fun ModelGenerationRequest.toAuthorizedCommand(
    addonId: AddonId,
    capabilityId: HostCapabilityId,
): AuthorizedModelGenerationCommand {
    check(validationIssue() == null) {
        "Only a validated IPC request can become an authorized command"
    }

    return AuthorizedModelGenerationCommand(
        requestId = requestId,
        addonId = addonId,
        capabilityId = capabilityId,
        systemInstruction = systemInstruction,
        messages = messages.map { message ->
            AuthorizedModelMessage(
                role = when (message.role) {
                    ModelMessageRole.SYSTEM ->
                        AuthorizedModelMessageRole.SYSTEM
                    ModelMessageRole.USER ->
                        AuthorizedModelMessageRole.USER
                    ModelMessageRole.ASSISTANT ->
                        AuthorizedModelMessageRole.ASSISTANT
                    ModelMessageRole.TOOL -> error(
                        "TOOL messages are rejected by provider API 1",
                    )
                    ModelMessageRole.UNKNOWN -> error(
                        "UNKNOWN messages are rejected by provider API 1",
                    )
                },
                content = message.content,
            )
        },
        timeoutMillis = timeoutMillis,
    )
}
