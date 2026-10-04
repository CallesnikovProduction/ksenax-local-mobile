package com.kolesnikovprod.ksetaorch.addons.banner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteFileFailure
import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteFileRequest
import com.kolesnikovprod.ksetaorch.addons.remote.AddonRemoteFileResult
import com.kolesnikovprod.ksetaorch.addons.remote.KtorAddonRemoteFileDownloader
import com.kolesnikovprod.ksetaorch.addons.storage.AddonFileLayout
import dev.openksenax.addons.contract.AddonId
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Порт получения проверенного presentation-баннера аддона.
 *
 * Presentation не знает, был bitmap прочитан из кэша или получен по сети.
 * Успешный результат означает совпадение SHA-256 и точных dimensions
 * [AddonBannerContract.WIDTH_PIXELS] ×
 * [AddonBannerContract.HEIGHT_PIXELS].
 *
 * @since 0.3
 */
internal fun interface AddonBannerRepository {

    suspend fun load(
        addonId: AddonId,
        packageName: String,
        isInstalled: Boolean,
        bannerUrl: String?,
        expectedSha256: String,
    ): AddonBannerLoadResult
}

/**
 * Контракт host-витрины на внешний banner asset.
 *
 * @since 0.3
 */
internal object AddonBannerContract {
    const val WIDTH_PIXELS = 1_920
    const val HEIGHT_PIXELS = 576
    const val ASPECT_RATIO =
        WIDTH_PIXELS.toFloat() / HEIGHT_PIXELS.toFloat()
}

/**
 * Реальный Ktor/cache/verifier pipeline баннера.
 *
 * Кэш индексируется ожидаемым SHA-256. Повреждённый или подменённый файл
 * удаляется и не передаётся presentation.
 *
 * @since 0.3
 */
internal class KtorVerifiedAddonBannerRepository(
    private val fileDownloader: KtorAddonRemoteFileDownloader,
    private val fileLayout: AddonFileLayout,
    private val maximumBannerBytes: Long =
        DEFAULT_MAXIMUM_BANNER_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonBannerRepository {

    init {
        require(maximumBannerBytes > 0L)
    }

    override suspend fun load(
        addonId: AddonId,
        packageName: String,
        isInstalled: Boolean,
        bannerUrl: String?,
        expectedSha256: String,
    ): AddonBannerLoadResult = withContext(ioDispatcher) {
        try {
            loadOnIo(
                addonId = addonId,
                packageName = packageName,
                isInstalled = isInstalled,
                bannerUrl = bannerUrl,
                expectedSha256 = expectedSha256,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AddonBannerLoadResult.Failed(
                AddonBannerFailure.LOCAL_ASSET_FAILURE,
            )
        }
    }

    private suspend fun loadOnIo(
        addonId: AddonId,
        packageName: String,
        isInstalled: Boolean,
        bannerUrl: String?,
        expectedSha256: String,
    ): AddonBannerLoadResult {
        val normalizedSha256 = expectedSha256.normalizeSha256()
        if (!SHA_256_REGEX.matches(normalizedSha256)) {
            return AddonBannerLoadResult.Failed(
                AddonBannerFailure.INVALID_EXPECTATION,
            )
        }

        val destination = if (isInstalled) {
            fileLayout.installedBanner(
                packageName = packageName,
                sha256 = normalizedSha256,
            )
        } else {
            fileLayout.temporaryBanner(
                addonId = addonId,
                sha256 = normalizedSha256,
            )
        }

        destination.readVerifiedBitmap(normalizedSha256)
            ?.let { bitmap ->
                return AddonBannerLoadResult.Ready(bitmap)
            }

        if (bannerUrl.isNullOrBlank()) {
            return AddonBannerLoadResult.Failed(
                AddonBannerFailure.LOCAL_ASSET_UNAVAILABLE,
            )
        }

        return when (
            val result = fileDownloader.download(
                AddonRemoteFileRequest(
                    rawUrl = bannerUrl,
                    destination = destination,
                    maximumBytes = maximumBannerBytes,
                    expectedSizeBytes = null,
                    acceptContentType = BANNER_ACCEPT,
                    requestTimeoutMillis =
                        BANNER_DOWNLOAD_TIMEOUT_MILLIS,
                    socketTimeoutMillis =
                        BANNER_SOCKET_TIMEOUT_MILLIS,
                ),
            )
        ) {
            is AddonRemoteFileResult.Failed -> {
                destination.delete()
                AddonBannerLoadResult.Failed(
                    result.reason.toBannerFailure(),
                )
            }

            is AddonRemoteFileResult.Downloaded -> {
                val bitmap = result.file.readVerifiedBitmap(
                    normalizedSha256,
                )
                if (bitmap == null) {
                    result.file.delete()
                    AddonBannerLoadResult.Failed(
                        AddonBannerFailure.VERIFICATION_FAILED,
                    )
                } else {
                    deleteObsoleteBanners(
                        addonId = addonId,
                        isInstalled = isInstalled,
                        keep = result.file,
                    )
                    AddonBannerLoadResult.Ready(bitmap)
                }
            }
        }
    }

    private fun File.readVerifiedBitmap(
        expectedSha256: String,
    ): Bitmap? {
        if (!isFile || length() !in 1L..maximumBannerBytes) {
            delete()
            return null
        }
        if (sha256() != expectedSha256) {
            delete()
            return null
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(absolutePath, bounds)
        if (
            bounds.outWidth != AddonBannerContract.WIDTH_PIXELS ||
            bounds.outHeight != AddonBannerContract.HEIGHT_PIXELS
        ) {
            delete()
            return null
        }

        return BitmapFactory.decodeFile(
            absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )?.takeIf { bitmap ->
            bitmap.width == AddonBannerContract.WIDTH_PIXELS &&
                bitmap.height == AddonBannerContract.HEIGHT_PIXELS
        } ?: run {
            delete()
            null
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02X".format(Locale.ROOT, byte.toInt() and 0xFF)
        }
    }

    private fun deleteObsoleteBanners(
        addonId: AddonId,
        isInstalled: Boolean,
        keep: File,
    ) {
        val directory = keep.parentFile ?: return
        val filePrefix = if (isInstalled) {
            "banner-"
        } else {
            val safeId = addonId.value.replace(NON_FILE_NAME, "_")
            "$safeId-"
        }
        directory.listFiles()
            .orEmpty()
            .filter { file ->
                file != keep &&
                    file.name.startsWith(filePrefix) &&
                    file.name.endsWith(
                        if (isInstalled) {
                            INSTALLED_BANNER_FILE_SUFFIX
                        } else {
                            BANNER_FILE_SUFFIX
                        },
                    )
            }
            .forEach(File::delete)
    }

    private fun AddonRemoteFileFailure.toBannerFailure():
            AddonBannerFailure {
        return when (this) {
            AddonRemoteFileFailure.UNSAFE_URL ->
                AddonBannerFailure.UNSAFE_URL

            AddonRemoteFileFailure.SIZE_MISMATCH ->
                AddonBannerFailure.SIZE_LIMIT_EXCEEDED

            AddonRemoteFileFailure.NETWORK_UNAVAILABLE,
            AddonRemoteFileFailure.TRANSFER_FAILED ->
                AddonBannerFailure.DOWNLOAD_FAILED
        }
    }

    private fun String.normalizeSha256(): String {
        return replace(":", "")
            .trim()
            .uppercase(Locale.ROOT)
    }

    private companion object {
        const val DEFAULT_MAXIMUM_BANNER_BYTES =
            16L * 1024L * 1024L
        const val BANNER_DOWNLOAD_TIMEOUT_MILLIS = 60_000L
        const val BANNER_SOCKET_TIMEOUT_MILLIS = 30_000L
        const val HASH_BUFFER_SIZE = 64 * 1024
        const val BANNER_FILE_SUFFIX = ".banner"
        const val INSTALLED_BANNER_FILE_SUFFIX = ".asset"
        const val BANNER_ACCEPT =
            "image/png,image/webp,image/jpeg," +
                "application/octet-stream;q=0.8"
        val SHA_256_REGEX = Regex("^[A-F0-9]{64}$")
        val NON_FILE_NAME = Regex("[^A-Za-z0-9._-]")
    }
}

/**
 * Результат verified banner pipeline.
 *
 * @since 0.3
 */
internal sealed interface AddonBannerLoadResult {
    data class Ready(
        val bitmap: Bitmap,
    ) : AddonBannerLoadResult

    data class Failed(
        val reason: AddonBannerFailure,
    ) : AddonBannerLoadResult
}

/**
 * Диагностическая причина перехода presentation к fallback.
 *
 * @since 0.3
 */
internal enum class AddonBannerFailure {
    INVALID_EXPECTATION,
    LOCAL_ASSET_UNAVAILABLE,
    LOCAL_ASSET_FAILURE,
    UNSAFE_URL,
    SIZE_LIMIT_EXCEEDED,
    DOWNLOAD_FAILED,
    VERIFICATION_FAILED,
}
