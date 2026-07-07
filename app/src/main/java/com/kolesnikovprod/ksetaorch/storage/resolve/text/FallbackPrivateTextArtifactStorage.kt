package com.kolesnikovprod.ksetaorch.storage.resolve.text

import android.content.Context
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat.Companion.readBufferedUtf8Text
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileFormat.FileNameFormatResolution
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver.Companion.ensureWorkspaceZoneMarkerFile
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileLocation
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileReadResult
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileStorageKind
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileWriteResult
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val DEFAULT_PRIVATE_TEXT_ARTIFACT_DIRECTORY = "tool-artifacts"

/**
 * Стабильный fallback-резольвер, конкретная реализация для app-private storage.
 *
 * Его суть — сохранять текстовые файлы, которые были сгенерированы агентными
 * инструментами, во внутреннюю папку приложения:
 * ```
 * /data/data/com.kolesnikovprod.ksetaorch/files/<directoryName>/
 * ```
 * Перед успешной записью backend также создаёт `there.ksenaxzone`, чтобы даже
 * fallback-директория сохраняла общий признак рабочей области Ksenax.
 * Перезапись существующего файла идёт через временный sibling-файл и замену
 * target-а после успешной записи нового содержимого.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class FallbackPrivateTextArtifactStorage(
    private val context: Context,
    private val directoryName: String = DEFAULT_PRIVATE_TEXT_ARTIFACT_DIRECTORY,
) : KsenaxTextFileResolver {

    private val appContext = context.applicationContext

    override val destinationDescription: String = "app-private filesDir/$directoryName"

    override suspend fun readText(
        fileName:   String
    ): KsenaxTextFileReadResult {
        val targetFileName = normalizeExistingFileName(fileName)
            ?: return KsenaxTextFileReadResult.Failure(
                message = "Неподдерживаемое или небезопасное имя текстового файла: $fileName"
            )
        val targetFile = File(resolveDirectory(appContext), targetFileName)

        return try {
            if (targetFile.isFile) {
                KsenaxTextFileReadResult.Success(
                    text     = targetFile.readBufferedUtf8Text(),
                    location = targetFile.toTextFileLocation()
                )
            } else {
                KsenaxTextFileReadResult.NotFound(
                    fileName = targetFileName
                )
            }
        } catch (exception: Exception) {
            KsenaxTextFileReadResult.Failure(
                message = exception.message ?: "Не удалось прочитать app-private файл.",
            )
        }
    }

    override suspend fun writeText(
        fileName:   String,
        text:       String,
        format:     KsenaxTextFileFormat,
    ): KsenaxTextFileWriteResult {
        val targetFileName = normalizeTargetFileName(
            fileName = fileName,
            format   = format,
        ) ?: return KsenaxTextFileWriteResult.Failure(
            message = "Неподдерживаемое или небезопасное имя текстового файла: $fileName",
        )
        val directory = resolveDirectory(appContext)
        val targetFile = File(directory, targetFileName)

        return try {
            directory.mkdirs()

            if (!directory.isDirectory) {
                return KsenaxTextFileWriteResult.Failure(
                    message = "Не удалось создать app-private директорию для файла",
                )
            }

            directory.ensureWorkspaceZoneMarkerFile()

            writeTextToTemporaryFileAndReplace(
                targetFile = targetFile,
                text       = text,
            )

            KsenaxTextFileWriteResult.Success(
                location = targetFile.toTextFileLocation()
            )
        } catch (exception: Exception) {
            KsenaxTextFileWriteResult.Failure(
                message = exception.message ?: "Не удалось записать app-private файл.",
            )
        }
    }

    /**
     * Записывает текст во временный sibling-файл и затем заменяет им target.
     *
     * Такой порядок защищает уже существующий файл от частично записанного
     * состояния: если запись во временный файл падает, старый target остаётся
     * нетронутым. Финальная замена выполняется через [Files.move] с попыткой
     * atomic move и fallback-ом на обычный replace.
     *
     * @since 0.2
     */
    private fun writeTextToTemporaryFileAndReplace(
        targetFile: File,
        text:       String,
    ) {
        val temporaryFile = File(
            targetFile.parentFile,
            targetFile.name.toTemporarySiblingFileName(),
        )

        try {
            temporaryFile.writeText(
                text = text,
                charset = Charsets.UTF_8,
            )
            moveTemporaryFileToTarget(
                temporaryFile = temporaryFile,
                targetFile    = targetFile,
            )
        } finally {
            if (temporaryFile.isFile) {
                temporaryFile.delete()
            }
        }
    }

    /**
     * Перемещает подготовленный temporary-файл на место целевого файла.
     *
     * [StandardCopyOption.ATOMIC_MOVE] работает только когда filesystem его
     * поддерживает. App-private storage обычно лежит на той же файловой системе,
     * но fallback всё равно нужен, чтобы не превращать отсутствие atomic move в
     * пользовательскую ошибку.
     *
     * @since 0.2
     */
    private fun moveTemporaryFileToTarget(
        temporaryFile: File,
        targetFile:    File,
    ) {
        try {
            Files.move(
                temporaryFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    /**
     * Создаёт уникальное имя временного sibling-файла рядом с target.
     *
     * Имя не проходит через пользовательскую нормализацию, потому что его
     * создаёт сам backend и оно живёт только внутри одной операции записи.
     *
     * @since 0.2
     */
    private fun String.toTemporarySiblingFileName(): String {
        return ".$this.tmp-${System.currentTimeMillis()}-${System.nanoTime()}"
    }

    /**
     * Вычисляет директорию хранения сгенерированных файлов в локальной папке приложения.
     *
     * Важно: здесь используется [directoryName], а не [destinationDescription].
     * [destinationDescription] нужен для UI/логов, а физическое имя папки должно
     * оставаться отдельным стабильным параметром backend-а.
     *
     * @since 0.2
     */
    private fun resolveDirectory(appContext: Context): File {
        return File(appContext.filesDir, directoryName)
    }

    /**
     * Нормализует имя файла для чтения уже существующего артефакта.
     *
     * Чтение требует явного поддерживаемого расширения: если вызвать `readText("note")`,
     * resolver не будет угадывать, читать `note.md` или `note.txt`. Это сохраняет
     * операцию чтения предсказуемой и не даёт случайно открыть не тот файл. Если
     * расширение есть, но не поддерживается, файл тоже отклоняется.
     *
     * @return безопасное имя файла или `null`, если имя похоже на путь, пустую
     * строку или неподдерживаемый текстовый формат.
     * @since 0.2
     */
    private fun normalizeExistingFileName(fileName: String): String? {
        val normalizedFileName = fileName.trim()

        return if (
            normalizedFileName.isSafeSingleFileName() &&
            KsenaxTextFileFormat.resolveFileName(normalizedFileName) is FileNameFormatResolution.Supported
        ) {
            normalizedFileName
        } else {
            null
        }
    }

    /**
     * Нормализует имя файла для записи нового или перезаписываемого артефакта.
     *
     * Если расширения нет, метод добавляет каноническое расширение из [format].
     * Если расширение уже есть, оно должно соответствовать [format]; например,
     * `note.md + MARKDOWN` проходит, а `note.txt + MARKDOWN` получает отказ.
     * Неподдерживаемое расширение тоже получает отказ, чтобы `note.pdf` не стало
     * `note.pdf.md`.
     *
     * @return итоговое безопасное имя файла или `null`, если имя небезопасно либо
     * расширение конфликтует с запрошенным форматом.
     * @since 0.2
     */
    private fun normalizeTargetFileName(
        fileName: String,
        format:   KsenaxTextFileFormat,
    ): String? {
        val normalizedFileName = fileName.trim()

        if (!normalizedFileName.isSafeSingleFileName()) {
            return null
        }

        return when (val resolution = KsenaxTextFileFormat.resolveFileName(normalizedFileName)) {
            FileNameFormatResolution.MissingExtension ->
                "$normalizedFileName.${format.canonicalExtension}"

            is FileNameFormatResolution.Supported ->
                if (resolution.format == format) normalizedFileName else null

            is FileNameFormatResolution.UnsupportedExtension ->
                null
        }
    }

    /**
     * Преобразует физический app-private [File] в публичный storage DTO.
     *
     * [KsenaxTextFileLocation.displayPath] здесь получает absolute path только
     * как человекочитаемое описание результата. Вызывающий код не должен
     * использовать его как capability или стабильный путь для последующего доступа.
     *
     * @since 0.2
     */
    private fun File.toTextFileLocation(): KsenaxTextFileLocation {
        return KsenaxTextFileLocation(
            displayPath = absolutePath,
            storageKind = KsenaxTextFileStorageKind.APP_PRIVATE,
            uri         = null,
        )
    }
}
