package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal object ObsidianNoteNaming {
    /**
     * Заголовок заметки, если модель не передала `title`.
     *
     * @since 0.2
     */
    const val DEFAULT_NOTE_TITLE = "untitled_obsidian_note"

    /**
     * Максимальная длина части имени файла, которую берём из заголовка заметки.
     *
     * @since 0.2
     */
    private const val MAX_FILENAME_TITLE_LENGTH = 80

    /**
     * Формат даты в имени markdown-файла.
     *
     * @since 0.2
     */
    private val NOTE_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd-MM-uuuu")

    /**
     * Формат времени внутри markdown-блока заметки.
     *
     * @since 0.2
     */
    private val NOTE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Символы, которые нельзя безопасно использовать в имени файла.
     *
     * Наглядный разбор:
     * ```
     * [ ... ]    набор символов: найти любой из них
     * \\         обратный слэш (экранирование)
     * /
     * :
     * *
     * ?
     * "
     * <
     * >
     * |
     * \p{Cntrl}  управляющие: табы, переносы, невидимые
     * ```
     *
     * Иными словами строка `План: Android / Obsidian?`
     * превратится в: `План  Android   Obsidian `.
     *
     * @since 0.2
     * @see toSafeNoteTitle
     */
    private val INVALID_FILENAME_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /**
     * Последовательности пробелов, которые схлопываются при очистке заголовка.
     *
     * Наглядный разбор:
     * ```
     * \s     любой whitespace: пробел, tab, перенос строки и т.д.
     * +      один или больше раз подряд
     * ```
     *
     * Иными словами строка, внутри которой больше одного пробела становится строкой,
     * в которой пробелов между словами не больше *одного*.
     *
     * @since 0.2
     * @see toSafeNoteTitle
     */
    private val MULTIPLE_SPACES = Regex("""\s+""")

    /**
     * Строит имя markdown-файла из даты и очищенного заголовка.
     *
     * @since 0.2
     */
    fun buildNoteFileName(
        createdAt: ZonedDateTime,
        safeTitle: String,
    ): String {
        val datePrefix = NOTE_DATE_FORMATTER.format(createdAt)
        return "$datePrefix. $safeTitle.md"
    }

    /**
     * Форматирует время для заголовка markdown-блока.
     *
     * @since 0.2
     */
    fun formatBlockTime(createdAt: ZonedDateTime): String =
        NOTE_TIME_FORMATTER.format(createdAt)

    /**
     * Превращает произвольную строку в безопасный заголовок файла.
     *
     * Функция удаляет запрещённые символы, схлопывает пробелы, режет слишком
     * длинный текст и возвращает запасной заголовок, если после очистки ничего не
     * осталось.
     *
     * @since 0.2
     */
    fun String.toSafeNoteTitle(): String {
        val withoutInvalidCharacters = replace(INVALID_FILENAME_CHARS, " ")
        val collapsedSpaces = withoutInvalidCharacters
            .replace(MULTIPLE_SPACES, " ")
            .trim()
            .trim('.')
            .trim()

        val safeTitle = collapsedSpaces.ifBlank { DEFAULT_NOTE_TITLE }
            .take(MAX_FILENAME_TITLE_LENGTH)
            .trim()
            .trim('.')
            .trim()

        return safeTitle.ifBlank { DEFAULT_NOTE_TITLE }
    }
}
