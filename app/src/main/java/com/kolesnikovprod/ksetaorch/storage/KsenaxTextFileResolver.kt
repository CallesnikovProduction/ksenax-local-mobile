package com.kolesnikovprod.ksetaorch.storage

import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileReadResult
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileWriteResult
import java.io.File
import java.io.InputStream

/**
 * Контракт границы ответственности за работу с текстовыми артефактами,
 * которые создаются или читаются агентными инструментами (tools).
 *
 * Инструмент не должен знать, куда именно попадёт файл: в приватную папку
 * приложения, в видимую системную коллекцию, в выбранную пользователем SAF-папку
 * или в другой разрешённый Android-хранилищем backend. Его задача - передать
 * намерение и данные, а затем показать пользователю результат из
 * [KsenaxTextFileWriteResult].
 *
 * Реализация менеджера отвечает за Android-реализм: разрешения, URI-доступ,
 * создание директорий, выбор ContentResolver/File API и честное описание
 * результата. Контракт не предполагает root-like доступ к файловой системе и не
 * должен обходить системные ограничения обычного приложения.
 *
 * Ожидания к реализациям:
 * - `appContext` должен использоваться как безопасный долгоживущий контекст.
 * - `fileName` считается именем файла, а не произвольным путём.
 * - директория, в которую backend пишет артефакты, должна иметь marker-файл
 *   [WORKSPACE_ZONE_FILE_NAME].
 * - существующий пользовательский файл нельзя усекать до успешной записи нового
 *   содержимого во временный sibling-файл или temporary-документ.
 * - ожидаемые ошибки хранилища возвращаются через
 *   [KsenaxTextFileWriteResult.Failure], а не пробрасываются наружу.
 * - медленные операции выполняются как suspend-контракт: реализация может
 *   переключить dispatcher внутри себя либо вызываться из подходящей корутины.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxTextFileResolver {

    companion object {
        /**
         * Служебный marker-файл рабочей области Ksenax.
         *
         * Backend создаёт этот файл внутри директории, куда пишет текстовые
         * артефакты. По нему пользователь и UI могут понять, что папка
         * инициализирована как рабочая зона Ksenax.
         *
         * @since 0.2
         */
        const val WORKSPACE_ZONE_FILE_NAME = "there.ksenaxzone"

        /**
         * MIME type marker-файла рабочей области.
         *
         * Содержимое marker-а остаётся UTF-8 текстом, но Android storage API
         * получает generic binary MIME. Для неизвестного расширения
         * `.ksenaxzone` значение `text/plain` заставляет системный provider
         * дописать `.txt`, после чего повторные инициализации создают имена
         * `there.ksenaxzone (1).txt`, `there.ksenaxzone (2).txt` и так далее.
         * `application/octet-stream` сохраняет переданное display name без
         * автоматической подстановки текстового расширения.
         *
         * @since 0.2
         */
        const val WORKSPACE_ZONE_MIME_TYPE = "application/octet-stream"

        /**
         * Общий process-local lock для инициализации marker-а.
         *
         * Разные Agentic-чаты могут создать отдельные экземпляры одного backend-а
         * для общей директории. Lock не даёт им одновременно пройти проверку
         * отсутствия marker-а и создать два документа внутри одного процесса.
         */
        internal val workspaceZoneInitializationLock = Any()

        private val workspaceZoneDisplayNamePattern = Regex(
            """^there\.ksenaxzone(?: \([1-9]\d*\))?(?:\.txt)?$"""
        )

        /**
         * Текст, который записывается в [WORKSPACE_ZONE_FILE_NAME].
         *
         * Это не конфиг и не секрет. Файл нужен как маленькая документация для
         * пользователя: папка выбрана рабочей областью Ksenax, приложение может
         * создавать и читать здесь поддерживаемые текстовые артефакты.
         *
         * @since 0.2
         */
        val WORKSPACE_ZONE_DOCUMENT_TEXT = """
            Ksenax workspace

            Эта папка используется как рабочая область Ksenax.

            Ksenax будет складывать сюда заметки и другие текстовые файлы,
            которые создаёт локальный агент. Сейчас поддерживаются Markdown и
            обычный plain text.

            В рамках agentic-чата приложение работает только через resolver
            этой директории. Другая рабочая папка получает отдельный resolver и
            не должна давать доступ к файлам этой области.

            Файлы лежат рядом с этим документом, чтобы ты мог открыть папку в
            файловом менеджере, редакторе или Obsidian и читать содержимое без
            приложения.

            there.ksenaxzone не хранит переписку, ключи, настройки модели или
            личные данные. Это метка папки и короткая памятка для человека,
            который открыл директорию и заметил файл с нестандартным расширением.

            Можно удалить этот файл. При следующей инициализации Ksenax создаст
            его снова. Если переносишь рабочую область в другое место, перенеси
            всю папку целиком.
        """.trimIndent()

        private const val WORKSPACE_DIRECTORY_LABEL = "Рабочая директория:"

        /**
         * Собирает полный текст marker-файла для новой рабочей области.
         *
         * @since 0.2
         */
        fun workspaceZoneDocumentText(destinationDescription: String): String {
            return WORKSPACE_ZONE_DOCUMENT_TEXT +
                    "\n\n" +
                    workspaceDirectoryEntry(destinationDescription) +
                    "\n"
        }

        /**
         * Дописывает директорию в существующий marker, сохраняя его содержимое.
         *
         * Повторная инициализация с той же директорией не меняет файл.
         *
         * @since 0.2
         */
        fun ensureWorkspaceDirectoryEntry(
            existingText: String,
            destinationDescription: String,
        ): String {
            val entry = workspaceDirectoryEntry(destinationDescription)
            if (existingText.contains(entry)) {
                return existingText
            }
            if (existingText.isBlank()) {
                return workspaceZoneDocumentText(destinationDescription)
            }
            return existingText.trimEnd() + "\n\n" + entry + "\n"
        }

        /**
         * Проверяет, относится ли display name к marker-у рабочей области.
         *
         * Помимо канонического [WORKSPACE_ZONE_FILE_NAME], метод распознаёт
         * legacy-имена `there.ksenaxzone.txt` и
         * `there.ksenaxzone (N).txt`. Их создавали MediaStore и SAF, когда marker
         * передавался provider-у как `text/plain`. Совпадение намеренно узкое,
         * чтобы cleanup не удалял обычные пользовательские файлы.
         *
         * @since 0.2
         */
        fun isWorkspaceZoneMarkerDisplayName(displayName: String): Boolean {
            return workspaceZoneDisplayNamePattern.matches(displayName)
        }

        private fun workspaceDirectoryEntry(destinationDescription: String): String {
            val normalizedDestination = destinationDescription.trim()
            require(normalizedDestination.isNotBlank()) {
                "Workspace destination description must not be blank."
            }
            return "$WORKSPACE_DIRECTORY_LABEL\n$normalizedDestination"
        }

        /**
         * Создаёт marker-файл рабочей области в обычной [File]-директории.
         *
         * Для существующего marker-а метод сохраняет текущий текст и дописывает
         * отсутствующую строку физической директории. Ошибки записи
         * пробрасываются вызывающей реализации.
         *
         * @since 0.2
         */
        fun File.ensureWorkspaceZoneMarkerFile() {
            val markerFile = File(this, WORKSPACE_ZONE_FILE_NAME)
            val markerText = if (markerFile.isFile) {
                ensureWorkspaceDirectoryEntry(
                    existingText = markerFile.readText(Charsets.UTF_8),
                    destinationDescription = absolutePath,
                )
            } else {
                workspaceZoneDocumentText(absolutePath)
            }

            if (!markerFile.isFile || markerFile.readText(Charsets.UTF_8) != markerText) {
                markerFile.writeText(
                    text = markerText,
                    charset = Charsets.UTF_8,
                )
            }
        }
    }

    /**
     * Человекочитаемое описание текущего назначения сохранения.
     *
     * Строка предназначена для UI, логов и объяснений пользователю. Она не должна
     * использоваться как машинный идентификатор backend-а; для этого есть
     * [KsenaxTextFileWriteResult.Success.location].
     *
     * @since 0.2
     */
    val destinationDescription: String

    /**
     * Читает или проверяет доступность текстового артефакта по имени файла.
     *
     * Содержимое файла возвращается только через [KsenaxTextFileReadResult.Success].
     * Отсутствие файла лучше выражать через [KsenaxTextFileReadResult.NotFound],
     * а не через общий failure.
     *
     * @return честный ответ в формате DTO ([KsenaxTextFileReadResult])
     * @since 0.2
     */
    suspend fun readText(
        fileName:   String
    ): KsenaxTextFileReadResult

    /**
     * Записывает текстовый артефакт в разрешённое текущей реализацией хранилище.
     *
     * [format] передаётся явно, чтобы контракт не зависел от сырого MIME string.
     * Реализация может сверить формат с расширением имени файла, подставить
     * каноническое расширение или отказать, если формат не поддерживается.
     *
     * @return честный ответ в формате DTO ([KsenaxTextFileWriteResult])
     * @since 0.2
     */
    suspend fun writeText(
        fileName:   String,
        text:       String,
        format:     KsenaxTextFileFormat,
    ): KsenaxTextFileWriteResult

    /**
     * Проверяет, что входная строка является только именем файла, а не путём.
     *
     * Это общая safety-проверка для всех backend-ов: app-private, MediaStore и
     * будущего SAF-resolver-а. Метод не проверяет, поддерживается ли расширение
     * файла как текстовый формат; за это отвечает
     * [KsenaxTextFileFormat.resolveFileName]. Здесь проверяется только то, что
     * строка безопасна как одиночное имя файла внутри уже выбранной директории.
     *
     * Пройдут проверку:
     * - `note`
     * - `note.md`
     * - `note.markdown`
     * - `note.txt`
     * - `daily-note.v1.md`
     * - `2026-06-19 meeting notes.md`
     *
     * Не пройдут проверку:
     * - пустая строка или строка из пробелов
     * - `.`
     * - `..`
     * - `note.`
     * - `folder/note.md`
     * - `folder\note.md`
     *
     * Имена вроде `note.pdf` проходят эту safety-проверку как одиночное имя
     * файла, но затем должны быть отклонены на уровне
     * [KsenaxTextFileFormat.resolveFileName] как неподдерживаемое расширение.
     *
     * @since 0.2
     */
    fun String.isSafeSingleFileName(): Boolean {
        return isNotBlank() &&
                this != "." &&
                this != ".." &&
                !this.endsWith('.') &&
                !this.contains('/') &&
                !this.contains('\\')
    }
}

enum class KsenaxTextFileFormat(
    val canonicalExtension: String,
    val mimeType:           String,
    val extensions:         Set<String>
) {
    MARKDOWN(
        canonicalExtension = "md",
        mimeType           = "text/markdown",
        extensions         = setOf("md", "markdown")
    ),

    PLAIN_TEXT(
        canonicalExtension = "txt",
        mimeType           = "text/plain",
        extensions         = setOf("txt")
    );

    /**
     * Результат распознавания формата по имени файла.
     *
     * Здесь важно различать два разных случая:
     * - у имени вообще нет расширения, и при записи можно добавить extension из
     *   явно переданного [KsenaxTextFileFormat];
     * - расширение есть, но оно не входит в поддерживаемые текстовые форматы, и
     *   его нельзя молча превращать в `name.pdf.md`.
     *
     * @since 0.2
     */
    sealed interface FileNameFormatResolution {

        /**
         * Имя файла не содержит расширение.
         */
        object MissingExtension : FileNameFormatResolution

        /**
         * Расширение найдено и соответствует одному из поддерживаемых форматов.
         */
        data class Supported(
            val format:    KsenaxTextFileFormat,
            val extension: String,
        ) : FileNameFormatResolution

        /**
         * Расширение найдено, но storage-контракт пока не считает его текстовым форматом.
         */
        data class UnsupportedExtension(
            val extension: String,
        ) : FileNameFormatResolution
    }

    companion object {
        /**
         * Определяет текстовый формат по расширению имени файла.
         *
         * Метод не проверяет безопасность имени как пути; перед записью или чтением
         * его нужно использовать вместе с [KsenaxTextFileResolver.isSafeSingleFileName].
         *
         * Результаты для типичных имён:
         * - `note` -> [FileNameFormatResolution.MissingExtension]
         * - `note.md` -> [FileNameFormatResolution.Supported] с [MARKDOWN]
         * - `note.markdown` -> [FileNameFormatResolution.Supported] с [MARKDOWN]
         * - `note.txt` -> [FileNameFormatResolution.Supported] с [PLAIN_TEXT]
         * - `note.pdf` -> [FileNameFormatResolution.UnsupportedExtension]
         *
         * Имя `note.` тоже будет распознано как [FileNameFormatResolution.MissingExtension],
         * но safety-проверка [KsenaxTextFileResolver.isSafeSingleFileName] должна
         * отклонить его раньше как имя с завершающей точкой.
         *
         * @since 0.2
         */
        fun resolveFileName(fileName: String): FileNameFormatResolution {
            val extension = fileName.extensionOrNull()
                ?: return FileNameFormatResolution.MissingExtension
            val format = entries.firstOrNull { extension in it.extensions }

            return if (format != null) {
                FileNameFormatResolution.Supported(
                    format    = format,
                    extension = extension,
                )
            } else {
                FileNameFormatResolution.UnsupportedExtension(extension)
            }
        }

        /**
         * Читает файл через buffered reader без ручной пересборки строк.
         *
         * В отличие от схемы `readLine()` + `appendLine()`, этот вариант не добавляет
         * лишний перенос в конец и не нормализует переносы строк вручную. Результат
         * всё равно возвращается целиком как [String], но I/O-часть проходит через
         * явный буфер.
         *
         * @since 0.2
         */
        fun File.readBufferedUtf8Text(): String {
            return bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }

        /**
         * Читает файл через buffered reader без ручной пересборки строк.
         *
         * В отличие от схемы `readLine()` + `appendLine()`, этот вариант не добавляет
         * лишний перенос в конец и не нормализует переносы строк вручную. Результат
         * всё равно возвращается целиком как [String], но I/O-часть проходит через
         * явный буфер.
         *
         * @since 0.2
         */
        fun InputStream.readBufferedUtf8Text(): String {
            return bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }

        private fun String.extensionOrNull(): String? {
            val extension = substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()

            return extension.ifBlank { null }
        }
    }
}
