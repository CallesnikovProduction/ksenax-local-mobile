package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.utils

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal object ObsidianNoteProperties {

    private const val AGENT_PROPERTY        = "agent"
    private const val AGENT_NAME            = "OpenKsenax (Gemma-based)"

    private const val CREATED_AT_PROPERTY   = "created_at"

    private const val ACTION_PROPERTY       = "action"
    private const val PERFORMED_AT_PROPERTY = "performed_at"

    /**
     * Собирает блок свойств Obsidian, который приложение пишет само.
     *
     * Модель не управляет этими полями. Исполнитель выставляет автора действия,
     * дату создания, последний момент выполнения и тип операции.
     *
     * @since 0.2
     */
    fun buildObsidianPropertiesBlock(
        createdAt:   ZonedDateTime,
        action:      ObsidianNoteAction,
        performedAt: ZonedDateTime,
    ): String = buildString {
        appendLine("---")
        appendLine("$AGENT_PROPERTY: ${AGENT_NAME.toYamlString()}")
        appendLine("$CREATED_AT_PROPERTY: ${createdAt.toObsidianDateTime()}")
        appendLine("$ACTION_PROPERTY: ${action.propertyValue.toYamlString()}")
        appendLine("$PERFORMED_AT_PROPERTY: ${performedAt.toObsidianDateTime()}")
        append("---")
    }

    /**
     * Собирает markdown-документ с YAML-свойствами Obsidian.
     *
     * @since 0.2
     */
    fun buildMarkdownDocument(
        propertiesBlock: String,
        markdownContent: String,
    ): String =
        "$propertiesBlock\n\n$markdownContent"

    /**
     * Обновляет служебные свойства существующей Obsidian-заметки.
     *
     * Если YAML-свойства уже есть, метод сохраняет чужие поля и меняет только
     * поля агента, действия и последнего выполнения. `created_at` сохраняется,
     * если он уже был в файле.
     *
     * @since 0.2
     */
    fun updateObsidianProperties(
        markdown:    String,
        performedAt: ZonedDateTime,
    ): String {
        val frontmatter = markdown.extractObsidianFrontmatter()
            ?: return buildMarkdownDocument(
                propertiesBlock = buildObsidianPropertiesBlock(
                    createdAt   = performedAt,
                    action      = ObsidianNoteAction.EDITED,
                    performedAt = performedAt,
                ),
                markdownContent = markdown.trimStart(),
            )

        val propertyLines = frontmatter.properties
            .lines()
            .toMutableList()

        propertyLines.upsertObsidianProperty(
            key   = AGENT_PROPERTY,
            value = AGENT_NAME.toYamlString(),
        )
        if (!propertyLines.hasObsidianProperty(CREATED_AT_PROPERTY)) {
            propertyLines.upsertObsidianProperty(
                key   = CREATED_AT_PROPERTY,
                value = performedAt.toObsidianDateTime(),
            )
        }
        propertyLines.upsertObsidianProperty(
            key   = ACTION_PROPERTY,
            value = ObsidianNoteAction.EDITED.propertyValue.toYamlString(),
        )
        propertyLines.upsertObsidianProperty(
            key   = PERFORMED_AT_PROPERTY,
            value = performedAt.toObsidianDateTime(),
        )

        return buildString {
            appendLine("---")
            propertyLines.forEach { line -> appendLine(line) }
            appendLine("---")
            append(frontmatter.body.trimStart())
        }
    }

    private fun String.extractObsidianFrontmatter(): ObsidianFrontmatter? {
        val markdown = replace("\r\n", "\n")
        if (!markdown.startsWith("---\n")) {
            return null
        }

        val closingMarkerStart = markdown.indexOf("\n---", startIndex = 4)
        if (closingMarkerStart < 0) {
            return null
        }

        val closingMarkerEnd = closingMarkerStart + "\n---".length
        val bodyStart = if (markdown.getOrNull(closingMarkerEnd) == '\n') {
            closingMarkerEnd + 1
        } else {
            closingMarkerEnd
        }

        return ObsidianFrontmatter(
            properties = markdown.substring(4, closingMarkerStart).trimEnd('\n'),
            body       = markdown.substring(bodyStart),
        )
    }

    private fun MutableList<String>.hasObsidianProperty(key: String): Boolean =
        any { line -> line.trimStart().startsWith("$key:") }

    private fun MutableList<String>.upsertObsidianProperty(
        key:   String,
        value: String,
    ) {
        val propertyIndex = indexOfFirst { line -> line.trimStart().startsWith("$key:") }
        val propertyLine = "$key: $value"

        if (propertyIndex >= 0) {
            this[propertyIndex] = propertyLine
        } else {
            add(propertyLine)
        }
    }

    private fun String.toYamlString(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun ZonedDateTime.toObsidianDateTime(): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(this)
}

internal enum class ObsidianNoteAction(val propertyValue: String) {
    CREATED("created"),
    EDITED("edited"),
}

private data class ObsidianFrontmatter(
    val properties: String,
    val body:       String,
)
