package com.kolesnikovprod.ksetaorch.download.domain

import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Promotes a prepared artifact without deleting the previous installation first.
 * Callers must exclusively own both paths and their parent directories during promotion.
 *
 * @since 0.4
 */
internal object ArtifactFileInstaller {

    /**
     * Atomically moves a file or directory on the same filesystem. Unsupported atomic
     * replacement fails closed; no copy/delete fallback is attempted. Links, aliases,
     * special files and overlapping directory trees are rejected.
     *
     * @since 0.4
     */
    fun promote(candidate: File, installed: File): Boolean = try {
        val source = candidate.toPath().toAbsolutePath()
        val target = installed.toPath().toAbsolutePath()
        val normalizedSource = source.normalize()
        val normalizedTarget = target.normalize()
        val parent = target.parent

        if (parent == null || hasLinkedAncestor(source) || hasLinkedAncestor(target) ||
            normalizedSource.startsWith(normalizedTarget) ||
            normalizedTarget.startsWith(normalizedSource) ||
            !Files.isDirectory(parent, NOFOLLOW_LINKS) ||
            !isArtifact(source) ||
            (Files.exists(target, NOFOLLOW_LINKS) &&
                (!isArtifact(target) || Files.isSameFile(source, target)))
        ) {
            false
        } else {
            // ATOMIC_MOVE itself rejects cross-filesystem moves. FileStore queries
            // are unsupported on some Android providers and are not a safe preflight.
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            true
        }
    } catch (_: IOException) {
        false
    } catch (_: UncheckedIOException) {
        false
    } catch (_: InvalidPathException) {
        false
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun hasLinkedAncestor(path: Path): Boolean =
        generateSequence(path) { it.parent }.any { Files.isSymbolicLink(it) }

    private fun isArtifact(path: Path): Boolean {
        if (Files.isRegularFile(path, NOFOLLOW_LINKS)) return true
        if (!Files.isDirectory(path, NOFOLLOW_LINKS)) return false
        Files.walk(path).use { paths ->
            return paths.allMatch {
                Files.isRegularFile(it, NOFOLLOW_LINKS) || Files.isDirectory(it, NOFOLLOW_LINKS)
            }
        }
    }
}
