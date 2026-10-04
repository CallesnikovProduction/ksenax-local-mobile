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

    fun matches(
        file: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
    ): Boolean {
        require(expectedSizeBytes > 0L)
        require(expectedSha256.matches(SHA256_REGEX_PATTERN))

        if (!file.isFile || file.length() != expectedSizeBytes) {
            return false
        }

        return calculateSha256(file).equals(expectedSha256, ignoreCase = true)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        file.inputStream().use { inputStream ->
            while (true) {
                val readBytes = inputStream.read(buffer)
                if (readBytes == -1) break

                digest.update(buffer, 0, readBytes)
            }
        }

        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
