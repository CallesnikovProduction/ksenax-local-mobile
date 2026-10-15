package com.kolesnikovprod.ksetaorch.download.domain

import java.io.File
import java.io.IOException
import java.io.EOFException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipException

/**
 * Extracts an archive into an exclusively owned, new or empty staging directory.
 * Never promotes the tree. Failed or cancelled extraction removes extracted content;
 * an existing nonempty output is rejected without modification.
 *
 * @since 0.4
 */
internal object SafeZipExtractor {
    private const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 4096
    private const val BUFFER_SIZE = 128 * 1024

    /**
     * Enforces canonical containment, entry count and actual (not advertised) byte limits.
     * [checkActive] is called before each entry and buffer read; cancellation propagates.
     * Callers must prevent concurrent modifications of the archive and staging paths.
     *
     * @since 0.4
     */
    fun extract(archive: File, output: File, checkActive: () -> Unit = {}): Boolean {
        var ownsOutput = false
        var outputExisted = false
        var complete = false
        try {
            checkActive()
            val outputPath = output.toPath().toAbsolutePath()
            if (generateSequence(outputPath) { it.parent }.any { Files.isSymbolicLink(it) } ||
                !Files.isRegularFile(archive.toPath(), NOFOLLOW_LINKS)
            ) return false

            val root = output.canonicalFile
            if (archive.canonicalFile.toPath().startsWith(root.toPath())) return false
            outputExisted = Files.exists(outputPath, NOFOLLOW_LINKS)
            if (outputExisted && (!output.isDirectory || output.list()?.isEmpty() != true)) {
                return false
            }

            // ZipInputStream alone accepts garbage and archives missing their central directory.
            val expectedEntries = ZipFile(archive).use { it.size() }
            if (expectedEntries > MAX_ENTRIES) return false
            if (!outputExisted && !output.mkdirs()) return false
            ownsOutput = true

            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0L
            var entries = 0
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    checkActive()
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return false
                    val name = entry.name
                    if (name.isEmpty() || name.startsWith('/') || name.contains('\\') ||
                        name.contains(':')
                    ) return false
                    val destination = File(root, name).canonicalFile
                    if (destination == root || !destination.toPath().startsWith(root.toPath())) {
                        return false
                    }

                    if (entry.isDirectory) {
                        if (!destination.isDirectory && !destination.mkdirs()) return false
                    } else {
                        val parent = destination.parentFile ?: return false
                        if (!parent.isDirectory && !parent.mkdirs()) return false
                        if (!destination.createNewFile()) return false
                    }

                    // Read directory entries too: malformed entries may contain payload bytes.
                    val sink = if (entry.isDirectory) null else destination.outputStream()
                    sink.use {
                        while (true) {
                            checkActive()
                            val read = zip.read(buffer)
                            if (read == -1) break
                            if (read.toLong() > MAX_EXTRACTED_BYTES - totalBytes) return false
                            totalBytes += read
                            if (entry.isDirectory && read > 0) return false
                            it?.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }
            checkActive()
            complete = entries == expectedEntries
            return complete
        } catch (_: ZipException) {
            return false
        } catch (_: EOFException) {
            return false
        } catch (exception: IOException) {
            // ENOSPC / revoked storage is a storage failure, not a corrupt model.
            throw exception
        } catch (_: IllegalArgumentException) {
            // ZIP name decoding and invalid filesystem paths may throw unchecked errors.
            return false
        } catch (_: SecurityException) {
            return false
        } finally {
            if (ownsOutput && !complete) {
                // Cleanup must not replace a cancellation exception with an IO failure.
                try {
                    if (outputExisted) {
                        output.listFiles()?.forEach { FileTreeDeleter.deleteIfExists(it) }
                    } else {
                        FileTreeDeleter.deleteIfExists(output)
                    }
                } catch (_: IOException) {
                    // The caller still receives the original failure/cancellation.
                } catch (_: SecurityException) {
                    // Cleanup is best effort when filesystem access is revoked.
                }
            }
        }
    }
}
