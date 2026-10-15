package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.domain.FileIntegrityVerifier
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileIntegrityVerifierTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun matchingSizeAndHashAcceptsUppercaseDigestAndChecksEachChunk() {
        val bytes = ByteArray(3 * 128 * 1024 + 1) { it.toByte() }
        val file = temporary.newFile().apply { writeBytes(bytes) }
        var checks = 0

        assertTrue(FileIntegrityVerifier.matches(file, bytes.size.toLong(), sha256(bytes).uppercase()) {
            checks++
        })
        assertTrue(checks >= 4)
    }

    @Test
    fun incorrectHashIsRejected() {
        val file = temporary.newFile().apply { writeText("model") }
        assertFalse(FileIntegrityVerifier.matches(file, file.length(), "0".repeat(64)))
    }

    @Test
    fun wrongSizeAndAlreadyTruncatedFileAreRejected() {
        val bytes = "complete model".toByteArray()
        val file = temporary.newFile().apply { writeBytes(bytes) }
        assertFalse(FileIntegrityVerifier.matches(file, bytes.size + 1L, sha256(bytes)))
        RandomAccessFile(file, "rw").use { it.setLength(3) }
        assertFalse(FileIntegrityVerifier.matches(file, bytes.size.toLong(), sha256(bytes)))
    }

    @Test
    fun truncationDuringReadingCannotMatchDigestOfTruncatedPrefix() {
        val prefix = ByteArray(128 * 1024) { 7 }
        val file = temporary.newFile().apply { writeBytes(prefix + prefix) }
        val originalSize = file.length()
        var checks = 0

        assertFalse(FileIntegrityVerifier.matches(file, originalSize, sha256(prefix)) {
            if (++checks == 2) RandomAccessFile(file, "rw").use { it.setLength(prefix.size.toLong()) }
        })
    }

    @Test
    fun growthDuringReadingIsRejected() {
        val bytes = ByteArray(128 * 1024) { 7 }
        val file = temporary.newFile().apply { writeBytes(bytes) }
        var checks = 0

        assertFalse(FileIntegrityVerifier.matches(file, bytes.size.toLong(), sha256(bytes)) {
            if (++checks == 2) file.appendBytes(byteArrayOf(1))
        })
    }

    @Test
    fun cancellationDuringHashingPropagatesUnchanged() {
        val bytes = ByteArray(3 * 128 * 1024)
        val file = temporary.newFile().apply { writeBytes(bytes) }
        val cancellation = CancellationException("cancel hashing")
        var checks = 0

        val thrown = assertThrows(CancellationException::class.java) {
            FileIntegrityVerifier.matches(file, file.length(), sha256(bytes)) {
                if (++checks == 2) throw cancellation
            }
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun absentFileIsRejected() {
        assertFalse(FileIntegrityVerifier.matches(
            java.io.File(temporary.root, "missing"), 1, "0".repeat(64),
        ))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
