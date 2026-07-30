package com.kolesnikovprod.ksetaorch.addons.catalog.cache

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Файловый кэш registry JSON во внутреннем storage OKx.
 *
 * Запись выполняется через временный файл,
 * чтобы не оставить частично записанный JSON.
 *
 * @since 0.3
 */
class FileAddonCatalogCache(
    context: Context,
    fileName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonCatalogCache {

    init {
        require(fileName.isNotBlank()) {
            "fileName must not be blank"
        }
        require(fileName == File(fileName).name) {
            "fileName must be a file name, not a path"
        }
    }

    private val cacheDirectory = File(
        context.filesDir,
        "addon-catalog",
    )

    private val catalogFile = File(
        cacheDirectory,
        fileName,
    )

    override suspend fun read(): CachedAddonCatalog? {
        return withContext(ioDispatcher) {
            if (!catalogFile.exists()) {
                return@withContext null
            }

            CachedAddonCatalog(
                rawDocument = catalogFile.readText(Charsets.UTF_8),
                storedAtEpochMillis = catalogFile.lastModified(),
            )
        }
    }

    override suspend fun write(rawDocument: String) {
        withContext(ioDispatcher) {
            Files.createDirectories(cacheDirectory.toPath())

            val temporaryFile = File.createTempFile(
                "catalog-${catalogFile.name}.",
                ".tmp",
                cacheDirectory,
            )

            try {
                temporaryFile.writeText(
                    text = rawDocument,
                    charset = Charsets.UTF_8,
                )

                try {
                    Files.move(
                        temporaryFile.toPath(),
                        catalogFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporaryFile.toPath(),
                        catalogFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                temporaryFile.delete()
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            Files.deleteIfExists(catalogFile.toPath())
        }
    }
}
