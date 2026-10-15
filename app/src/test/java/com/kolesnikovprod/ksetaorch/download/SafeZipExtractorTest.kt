package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.domain.SafeZipExtractor
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeZipExtractorTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun normalArchiveCreatesParentsAndExplicitDirectories() {
        val archive = zip {
            entry("model/empty/")
            entry("model/conf/settings.txt", "settings".toByteArray())
            entry("model/am/data", byteArrayOf(1, 2, 3))
        }
        val output = File(temporary.root, "output")

        assertTrue(SafeZipExtractor.extract(archive, output))

        assertTrue(File(output, "model/empty").isDirectory)
        assertEquals("settings", File(output, "model/conf/settings.txt").readText())
        assertEquals(listOf<Byte>(1, 2, 3), File(output, "model/am/data").readBytes().toList())
    }

    @Test
    fun directoryOnlyArchiveCreatesDirectoriesNotFiles() {
        val archive = zip { entry("model/"); entry("model/conf/") }
        val output = temporary.newFolder("output")

        assertTrue(SafeZipExtractor.extract(archive, output))

        assertTrue(File(output, "model/conf").isDirectory)
    }

    @Test
    fun emptyZipIsValidButPlainGarbageIsNot() {
        val output = File(temporary.root, "output")
        assertTrue(SafeZipExtractor.extract(zip {}, output))
        val garbage = temporary.newFile().apply { writeText("not a zip") }
        assertFalse(SafeZipExtractor.extract(garbage, output))
        assertTrue(output.list()!!.isEmpty())
    }

    @Test
    fun traversalAbsoluteAndWindowsPathsFailAndRemovePartialTree() {
        listOf("../escape", "../output-sibling/escape", "/absolute", "C:/escape", "..\\escape")
            .forEach { unsafeName ->
                val archive = zip { entry("safe", byteArrayOf(1)); entry(unsafeName, byteArrayOf(2)) }
                val output = File(temporary.root, "output")

                assertFalse(unsafeName, SafeZipExtractor.extract(archive, output))
                assertFalse(output.exists())
                assertFalse(File(temporary.root, "escape").exists())
                assertFalse(File(temporary.root, "output-sibling").exists())
            }
    }

    @Test
    fun missingCentralDirectoryIsRejected() {
        val archive = zip { entry("model", byteArrayOf(1, 2, 3)) }
        archive.writeBytes(archive.readBytes().dropLast(22).toByteArray())
        val output = File(temporary.root, "output")

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertFalse(output.exists())
    }

    @Test
    fun malformedUtf8EntryNameFailsClosed() {
        val archive = zip { entry("model", byteArrayOf(1)) }
        val bytes = archive.readBytes()
        bytes[30] = 0xff.toByte()
        archive.writeBytes(bytes)
        val output = File(temporary.root, "output")

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertFalse(output.exists())
    }

    @Test
    fun damagedPayloadCrcIsRejectedAndPartialTreeRemoved() {
        val archive = zip {
            val data = byteArrayOf(1, 2, 3)
            putNextEntry(ZipEntry("model").apply {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = size
                crc = CRC32().apply { update(data) }.value
            })
            write(data)
            closeEntry()
        }
        val bytes = archive.readBytes()
        bytes[30 + "model".length] = 9
        archive.writeBytes(bytes)
        val output = File(temporary.root, "output")

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertFalse(output.exists())
    }

    @Test
    fun cancellationWithinSingleEntryPropagatesAndCleansStagingTree() {
        val archive = zip { entry("large", ByteArray(1024 * 1024)) }
        val output = File(temporary.root, "output")
        val cancellation = CancellationException("cancel unzip")
        var buffers = 0

        val thrown = assertThrows(CancellationException::class.java) {
            SafeZipExtractor.extract(archive, output) {
                if (File(output, "large").exists() && ++buffers == 3) throw cancellation
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(3, buffers)
        assertFalse(output.exists())
    }

    @Test
    fun failureLeavesPreexistingEmptyOutputEmpty() {
        val archive = zip { entry("safe", byteArrayOf(1)); entry("../escape", byteArrayOf(2)) }
        val output = temporary.newFolder("output")

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertTrue(output.isDirectory)
        assertTrue(output.list()!!.isEmpty())
    }

    @Test
    fun existingInstallationIsNotOverwritten() {
        val archive = zip { entry("model", "new".toByteArray()) }
        val output = temporary.newFolder("output")
        val old = File(output, "model").apply { writeText("old") }

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertEquals("old", old.readText())
    }

    @Test
    fun linkedOutputCannotWriteOutsideStagingTree() {
        val outside = temporary.newFolder("outside")
        val output = File(temporary.root, "output")
        try {
            Files.createSymbolicLink(output.toPath(), outside.toPath())
        } catch (exception: Exception) {
            assumeNoException("Symbolic links require filesystem/OS support", exception)
        }

        assertFalse(SafeZipExtractor.extract(zip { entry("escape", byteArrayOf(1)) }, output))
        assertTrue(outside.list()!!.isEmpty())
    }

    @Test
    fun duplicateFilesAreRejectedRatherThanOverwritten() {
        val archive = zip { entry("a/../model", byteArrayOf(1)); entry("model", byteArrayOf(2)) }
        val output = File(temporary.root, "output")

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertFalse(output.exists())
    }

    @Test
    fun directoryEntryWithPayloadIsRejected() {
        val archive = zip { entry("model/", byteArrayOf(1)) }
        val output = File(temporary.root, "output")

        assertFalse(SafeZipExtractor.extract(archive, output))
        assertFalse(output.exists())
    }

    @Test
    fun maximumEntryCountSucceedsButOneMoreFails() {
        val valid = zip { repeat(4096) { entry("dir$it/") } }
        val output = File(temporary.root, "output")
        assertTrue(SafeZipExtractor.extract(valid, output))
        assertEquals(4096, output.list()!!.size)

        val invalid = zip { repeat(4097) { entry("dir$it/") } }
        val rejected = File(temporary.root, "rejected")
        assertFalse(SafeZipExtractor.extract(invalid, rejected))
        assertFalse(rejected.exists())
    }

    @Test
    fun exactByteLimitSucceedsAndOneExtraByteAcrossEntriesFails() {
        val archive = zip {
            putNextEntry(ZipEntry("first"))
            val buffer = ByteArray(128 * 1024)
            repeat(2048) { write(buffer) }
            closeEntry()
            putNextEntry(ZipEntry("second"))
            repeat(2048) { write(buffer) }
            closeEntry()
        }
        val output = File(temporary.root, "output")
        assertTrue(SafeZipExtractor.extract(archive, output))
        assertEquals(512L * 1024 * 1024, output.listFiles()!!.sumOf { it.length() })
        assertTrue(output.deleteRecursively())

        val oversized = zip {
            putNextEntry(ZipEntry("large"))
            val buffer = ByteArray(128 * 1024)
            repeat(4096) { write(buffer) }
            closeEntry()
            entry("extra", byteArrayOf(1))
        }
        val rejected = File(temporary.root, "rejected")
        assertFalse(SafeZipExtractor.extract(oversized, rejected))
        assertFalse(rejected.exists())
    }

    private fun zip(contents: ZipOutputStream.() -> Unit): File =
        temporary.newFile().apply {
            ZipOutputStream(outputStream().buffered()).use { it.contents() }
        }

    private fun ZipOutputStream.entry(name: String, data: ByteArray = byteArrayOf()) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }
}
