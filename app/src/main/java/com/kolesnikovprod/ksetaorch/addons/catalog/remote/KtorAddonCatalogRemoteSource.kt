package com.kolesnikovprod.ksetaorch.addons.catalog.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readRemaining
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.io.readByteArray

/**
 * Ktor-источник внешнего registry JSON.
 *
 * Класс получает общий [HttpClient] извне и не владеет его lifecycle.
 * Таймауты, redirect policy и transport engine настраиваются в едином
 * composition root OKx. Здесь остаются только правила catalog-протокола:
 * HTTPS, успешный HTTP-статус и ограниченный размер UTF-8 документа.
 *
 * @since 0.3
 */
class KtorAddonCatalogRemoteSource(
    private val httpClient: HttpClient,
    registryUrl: Url,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) : AddonCatalogRemoteSource {

    private val registryUrl: Url = validateRegistryUrl(registryUrl)

    init {
        require(maxResponseBytes > 0) {
            "maxResponseBytes must be greater than zero"
        }
    }

    /**
     * Создаёт источник из строкового URL с немедленной проверкой схемы.
     *
     * @since 0.3
     */
    constructor(
        httpClient: HttpClient,
        registryUrl: String,
        maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    ) : this(
        httpClient = httpClient,
        registryUrl = parseRegistryUrl(registryUrl),
        maxResponseBytes = maxResponseBytes,
    )

    override suspend fun fetchRawDocument(): String {
        return httpClient.prepareGet(registryUrl) {
            accept(ContentType.Application.Json)
            header(
                key = HttpHeaders.UserAgent,
                value = USER_AGENT,
            )
        }.execute { response ->
            val statusCode = response.status.value
            if (statusCode !in SUCCESSFUL_STATUS_RANGE) {
                throw AddonCatalogHttpStatusException(statusCode)
            }

            if (!isSafeRegistryUrl(response.call.request.url)) {
                throw AddonCatalogRemoteResponseException(
                    "Addon registry redirected to an unsafe URL",
                )
            }

            val declaredLength = response.headers[
                HttpHeaders.ContentLength
            ]?.toLongOrNull()

            if (
                declaredLength != null &&
                declaredLength > maxResponseBytes
            ) {
                throw AddonCatalogResponseTooLargeException(
                    maximumBytes = maxResponseBytes,
                )
            }

            val bodyBytes = response.bodyAsChannel()
                .readRemaining(maxResponseBytes.toLong() + 1L)
                .readByteArray()

            if (bodyBytes.size > maxResponseBytes) {
                throw AddonCatalogResponseTooLargeException(
                    maximumBytes = maxResponseBytes,
                )
            }

            try {
                decodeStrictUtf8(bodyBytes)
            } catch (error: Exception) {
                throw AddonCatalogRemoteResponseException(
                    message =
                        "Addon registry response is not valid UTF-8",
                    cause = error,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES: Int = 1_048_576
        const val USER_AGENT: String =
            "OpenKsenax-Addon-Catalog/0.4"
        val SUCCESSFUL_STATUS_RANGE: IntRange = 200..299

        fun parseRegistryUrl(rawUrl: String): Url {
            require(rawUrl.isNotBlank()) {
                "registryUrl must not be blank"
            }

            return try {
                validateRegistryUrl(Url(rawUrl))
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "registryUrl must be a valid absolute HTTPS URL",
                    error,
                )
            }
        }

        fun validateRegistryUrl(url: Url): Url {
            require(isSafeRegistryUrl(url)) {
                "registryUrl must be an absolute HTTPS URL " +
                        "without credentials or fragment"
            }
            return url
        }

        fun isSafeRegistryUrl(url: Url): Boolean {
            return url.protocol == URLProtocol.HTTPS &&
                    url.host.isNotBlank() &&
                    url.user == null &&
                    url.password == null &&
                    url.fragment.isEmpty()
        }

        fun decodeStrictUtf8(bytes: ByteArray): String {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }
    }
}

internal open class AddonCatalogRemoteResponseException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private class AddonCatalogHttpStatusException(
    statusCode: Int,
) : AddonCatalogRemoteResponseException(
    "Addon registry request failed with HTTP $statusCode",
)

private class AddonCatalogResponseTooLargeException(
    maximumBytes: Int,
) : AddonCatalogRemoteResponseException(
    "Addon registry response exceeds $maximumBytes bytes",
)
