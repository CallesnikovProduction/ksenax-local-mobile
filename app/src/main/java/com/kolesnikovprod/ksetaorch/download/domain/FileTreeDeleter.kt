package com.kolesnikovprod.ksetaorch.download.domain

import java.io.File
import java.nio.file.Files

/**
 * Рекурсивно удаляет дерево, не переходя по symbolic link.
 *
 * @since 0.4
 */
internal object FileTreeDeleter {

    fun deleteIfExists(file: File): Boolean {
        val path = file.toPath()
        if (Files.isSymbolicLink(path)) {
            return Files.deleteIfExists(path)
        }
        if (!file.exists()) return true
        if (!file.isDirectory) return file.delete()

        val children = file.listFiles() ?: return false
        val childrenDeleted = children.all(::deleteIfExists)

        return childrenDeleted && file.delete()
    }
}
