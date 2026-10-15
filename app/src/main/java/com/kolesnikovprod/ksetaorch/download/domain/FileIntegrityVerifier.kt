package com.kolesnikovprod.ksetaorch.download.domain

import com.kolesnikovprod.ksetaorch.download.domain.data.SHA256_REGEX_PATTERN
import java.io.File
import java.security.MessageDigest

/**
 * Потоковая проверка локального файла по точному размеру и SHA-256.
 *
 * @since 0.4
 */
internal object FileIntegrityVerifier {

    /**
     * Checks size and SHA-256 with a cancellation hook before each 128 KiB read.
     * Cancellation propagates to the caller; concurrent truncation/growth fails verification.
     *
     * @since 0.4
     */
    fun matches(
        file: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
        checkActive: () -> Unit = {},
    ): Boolean {
        require(expectedSizeBytes > 0L)
        require(expectedSha256.matches(SHA256_REGEX_PATTERN))

        if (!file.isFile || file.length() != expectedSizeBytes) {
            return false
        }

        return calculateSha256(file, expectedSizeBytes, checkActive)
            ?.equals(expectedSha256, ignoreCase = true) == true
    }

    private fun calculateSha256(
        file: File,
        expectedSizeBytes: Long,
        checkActive: () -> Unit,
    ): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var totalBytes = 0L

        file.inputStream().use { inputStream ->
            while (true) {
                checkActive()
                val readBytes = inputStream.read(buffer)
                if (readBytes == -1) break

                totalBytes += readBytes
                if (totalBytes > expectedSizeBytes) return null
                digest.update(buffer, 0, readBytes)
            }
        }

        if (totalBytes != expectedSizeBytes || file.length() != expectedSizeBytes) return null

        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
