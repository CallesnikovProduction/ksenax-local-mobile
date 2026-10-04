package com.kolesnikovprod.ksetaorch.addons.remote

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * Общий потоковый Ktor-механизм загрузки удалённых addon-артефактов.
 *
 * Класс отвечает только за HTTPS/redirect policy, ограничения размера и
 * атомарную запись файла. Семантические проверки APK, изображения, SHA-256
 * и package identity принадлежат вызывающим контурам.
 *
 * @since 0.3
 */
internal class KtorAddonRemoteFileDownloader(
    private val httpClient: HttpClient,
    allowedHosts: Set<String>,
) {

    private val urlPolicy = AddonRemoteUrlPolicy(allowedHosts)

    suspend fun download(
        request: AddonRemoteFileRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit =
            { _, _ -> },
    ): AddonRemoteFileResult {
        require(request.maximumBytes > 0L)
        require(
            request.expectedSizeBytes == null ||
                request.expectedSizeBytes > 0L,
        )
        require(request.requestTimeoutMillis > 0L)
        require(request.socketTimeoutMillis > 0L)

        val destination = request.destination
        val partial = File(destination.path + PARTIAL_FILE_SUFFIX)

        val initialUrl = urlPolicy.parse(request.rawUrl)
            ?: run {
                partial.delete()
                return AddonRemoteFileResult.Failed(
                    reason = AddonRemoteFileFailure.UNSAFE_URL,
                    message = "URL must use an approved HTTPS host",
                )
            }
        if (
            request.expectedSizeBytes != null &&
            request.expectedSizeBytes > request.maximumBytes
        ) {
            partial.delete()
            return AddonRemoteFileResult.Failed(
                reason = AddonRemoteFileFailure.SIZE_MISMATCH,
                message = "Published file exceeds the host size limit",
            )
        }

        return try {
            destination.parentFile?.mkdirs()
            var currentUrl = initialUrl

            repeat(MAX_REDIRECTS + 1) { redirectIndex ->
                when (
                    val outcome = executeRequest(
                        url = currentUrl,
                        partial = partial,
                        request = request,
                        onProgress = onProgress,
                    )
                ) {
                    is RequestOutcome.Redirect -> {
                        if (redirectIndex == MAX_REDIRECTS) {
                            throw IOException("Too many redirects")
                        }
                        currentUrl = urlPolicy.resolveRedirect(
                            current = currentUrl,
                            location = outcome.location,
                        ) ?: run {
                            partial.delete()
                            return AddonRemoteFileResult.Failed(
                                reason = AddonRemoteFileFailure.UNSAFE_URL,
                                message =
                                    "Redirect left the approved HTTPS hosts",
                            )
                        }
                    }

                    is RequestOutcome.Complete -> {
                        if (
                            destination.exists() &&
                            !destination.delete()
                        ) {
                            throw IOException(
                                "Cannot replace cached file",
                            )
                        }
                        if (!partial.renameTo(destination)) {
                            throw IOException(
                                "Cannot finalize downloaded file",
                            )
                        }
                        return AddonRemoteFileResult.Downloaded(
                            file = destination,
                            sizeBytes = outcome.sizeBytes,
                        )
                    }

                    RequestOutcome.SizeMismatch ->
                        throw AddonRemoteSizeMismatchException()
                }
            }

            error("Unreachable redirect state")
        } catch (cancellation: CancellationException) {
            partial.delete()
            throw cancellation
        } catch (error: AddonRemoteSizeMismatchException) {
            partial.delete()
            AddonRemoteFileResult.Failed(
                reason = AddonRemoteFileFailure.SIZE_MISMATCH,
                message = error.message,
            )
        } catch (error: Exception) {
            partial.delete()
            AddonRemoteFileResult.Failed(
                reason = if (error.isNetworkUnavailable()) {
                    AddonRemoteFileFailure.NETWORK_UNAVAILABLE
                } else {
                    AddonRemoteFileFailure.TRANSFER_FAILED
                },
                message = error.message,
            )
        }
    }

    private suspend fun executeRequest(
        url: Url,
        partial: File,
        request: AddonRemoteFileRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): RequestOutcome {
        return httpClient.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, request.acceptContentType)
            timeout {
                requestTimeoutMillis = request.requestTimeoutMillis
                socketTimeoutMillis = request.socketTimeoutMillis
            }
        }.execute { response ->
            val status = response.status.value
            if (status in REDIRECT_STATUS_CODES) {
                val location = response.headers[HttpHeaders.Location]
                    ?: throw IOException("Redirect has no Location")
                return@execute RequestOutcome.Redirect(location)
            }
            if (status !in 200..299) {
                throw IOException("Request failed with HTTP $status")
            }

            val declaredSize = response.headers[HttpHeaders.ContentLength]
                ?.toLongOrNull()
            if (
                declaredSize != null &&
                (declaredSize <= 0L ||
                    declaredSize > request.maximumBytes)
            ) {
                throw AddonRemoteSizeMismatchException(
                    "Published file has an invalid or excessive size",
                )
            }
            if (
                request.expectedSizeBytes != null &&
                declaredSize != null &&
                declaredSize != request.expectedSizeBytes
            ) {
                return@execute RequestOutcome.SizeMismatch
            }

            val total = request.expectedSizeBytes ?: declaredSize
            onProgress(0L, total)

            var received = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            FileOutputStream(partial, false).use { output ->
                val channel = response.bodyAsChannel()
                while (true) {
                    val count = channel.readAvailable(
                        buffer = buffer,
                        offset = 0,
                        length = buffer.size,
                    )
                    if (count == -1) break
                    if (count == 0) continue

                    received += count
                    if (received > request.maximumBytes) {
                        throw AddonRemoteSizeMismatchException(
                            "File exceeds the host size limit",
                        )
                    }
                    output.write(buffer, 0, count)
                    onProgress(received, total)
                }
                output.fd.sync()
            }

            if (received <= 0L) {
                throw IOException("Downloaded file is empty")
            }
            if (
                request.expectedSizeBytes != null &&
                received != request.expectedSizeBytes
            ) {
                return@execute RequestOutcome.SizeMismatch
            }

            RequestOutcome.Complete(received)
        }
    }

    private sealed interface RequestOutcome {
        data class Redirect(val location: String) : RequestOutcome
        data class Complete(val sizeBytes: Long) : RequestOutcome
        data object SizeMismatch : RequestOutcome
    }

    private companion object {
        const val PARTIAL_FILE_SUFFIX = ".part"
        const val MAX_REDIRECTS = 5
        const val BUFFER_SIZE = 64 * 1024
        const val USER_AGENT = "OpenKsenax-Addon-Asset-Downloader/0.4"
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * Параметры одной удалённой файловой загрузки.
 *
 * @since 0.3
 */
internal data class AddonRemoteFileRequest(
    val rawUrl: String,
    val destination: File,
    val maximumBytes: Long,
    val expectedSizeBytes: Long?,
    val acceptContentType: String,
    val requestTimeoutMillis: Long,
    val socketTimeoutMillis: Long,
)

/**
 * Результат общей транспортной стадии.
 *
 * @since 0.3
 */
internal sealed interface AddonRemoteFileResult {
    data class Downloaded(
        val file: File,
        val sizeBytes: Long,
    ) : AddonRemoteFileResult

    data class Failed(
        val reason: AddonRemoteFileFailure,
        val message: String?,
    ) : AddonRemoteFileResult
}

/**
 * Ошибка transport boundary без знания типа загружаемого артефакта.
 *
 * @since 0.3
 */
internal enum class AddonRemoteFileFailure {
    UNSAFE_URL,
    SIZE_MISMATCH,
    NETWORK_UNAVAILABLE,
    TRANSFER_FAILED,
}

/**
 * Проверяет только типизированные transport-причины отсутствия соединения.
 *
 * HTTP status, redirect policy, file IO и semantic verification намеренно
 * не считаются отсутствием Интернета.
 *
 * @since 0.3
 */
internal fun Throwable.isNetworkUnavailable(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this

    while (current != null && visited.add(current)) {
        if (
            current is UnknownHostException ||
            current is SocketException ||
            current is SocketTimeoutException ||
            current is UnresolvedAddressException ||
            current is ConnectTimeoutException ||
            current is HttpRequestTimeoutException
        ) {
            return true
        }
        current = current.cause
    }

    return false
}

/**
 * Общая HTTPS/redirect policy удалённых addon-артефактов.
 *
 * @since 0.3
 */
internal class AddonRemoteUrlPolicy(
    allowedHosts: Set<String>,
) {

    private val allowedHosts =
        allowedHosts.mapTo(mutableSetOf()) { host ->
            host.lowercase(Locale.ROOT)
        }

    init {
        require(this.allowedHosts.isNotEmpty())
    }

    fun parse(rawUrl: String): Url? {
        return try {
            Url(rawUrl).takeIf(::isAllowed)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun resolveRedirect(current: Url, location: String): Url? {
        return try {
            val resolved = URI(current.toString()).resolve(location)
            Url(resolved.toString()).takeIf(::isAllowed)
        } catch (_: Exception) {
            null
        }
    }

    private fun isAllowed(url: Url): Boolean {
        return url.protocol == URLProtocol.HTTPS &&
            url.user == null &&
            url.password == null &&
            url.fragment.isEmpty() &&
            url.host.lowercase(Locale.ROOT) in allowedHosts
    }
}

private class AddonRemoteSizeMismatchException(
    message: String = "Downloaded size does not match registry",
) : IOException(message)
