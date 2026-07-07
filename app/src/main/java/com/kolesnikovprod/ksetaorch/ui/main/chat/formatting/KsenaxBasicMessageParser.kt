package com.kolesnikovprod.ksetaorch.ui.main.chat.formatting

internal sealed interface KsenaxBasicMessageBlock {
    data class Prose(val text: String) : KsenaxBasicMessageBlock

    data class Code(
        val code: String,
        val language: KsenaxCodeLanguage,
        val languageLabel: String,
    ) : KsenaxBasicMessageBlock
}

internal enum class KsenaxCodeLanguage {
    Kotlin,
    Java,
    PlainText,
}

internal fun parseBasicMessageBlocks(rawText: String): List<KsenaxBasicMessageBlock> {
    if (!rawText.contains(CodeFence)) {
        return listOf(KsenaxBasicMessageBlock.Prose(rawText))
    }

    val blocks = mutableListOf<KsenaxBasicMessageBlock>()
    var cursor = 0

    while (cursor < rawText.length) {
        val fenceStart = rawText.indexOf(CodeFence, startIndex = cursor)
        if (fenceStart < 0) {
            rawText.substring(cursor)
                .trimBlockBoundary()
                .takeIf(String::isNotEmpty)
                ?.let { blocks += KsenaxBasicMessageBlock.Prose(it) }
            break
        }

        rawText.substring(cursor, fenceStart)
            .trimBlockBoundary()
            .takeIf(String::isNotEmpty)
            ?.let { blocks += KsenaxBasicMessageBlock.Prose(it) }

        val languageLineEnd = rawText.indexOf('\n', startIndex = fenceStart + CodeFence.length)
        if (languageLineEnd < 0) {
            rawText.substring(fenceStart)
                .takeIf(String::isNotEmpty)
                ?.let { blocks += KsenaxBasicMessageBlock.Prose(it) }
            break
        }

        val rawLanguage = rawText
            .substring(fenceStart + CodeFence.length, languageLineEnd)
            .trim()
        val language = rawLanguage.toCodeLanguage()
        val codeStart = languageLineEnd + 1
        val closingFence = rawText.indexOf(CodeFence, startIndex = codeStart)
        val codeEnd = closingFence.takeIf { it >= 0 } ?: rawText.length
        val code = rawText
            .substring(codeStart, codeEnd)
            .trimEnd('\r', '\n')

        blocks += KsenaxBasicMessageBlock.Code(
            code = code,
            language = language,
            languageLabel = language.displayLabel(rawLanguage),
        )

        if (closingFence < 0) {
            break
        }

        cursor = closingFence + CodeFence.length
        while (cursor < rawText.length && rawText[cursor] in charArrayOf('\r', '\n')) {
            cursor++
        }
    }

    return blocks.ifEmpty {
        listOf(KsenaxBasicMessageBlock.Prose(rawText))
    }
}

internal fun String.containsCodeFence(): Boolean = contains(CodeFence)

private fun String.toCodeLanguage(): KsenaxCodeLanguage {
    return when (lowercase()) {
        "kotlin", "kt", "kts" -> KsenaxCodeLanguage.Kotlin
        "java" -> KsenaxCodeLanguage.Java
        else -> KsenaxCodeLanguage.PlainText
    }
}

private fun KsenaxCodeLanguage.displayLabel(rawLanguage: String): String {
    return when (this) {
        KsenaxCodeLanguage.Kotlin -> "KOTLIN"
        KsenaxCodeLanguage.Java -> "JAVA"
        KsenaxCodeLanguage.PlainText -> rawLanguage
            .takeIf(String::isNotBlank)
            ?.uppercase()
            ?: "CODE"
    }
}

private fun String.trimBlockBoundary(): String = trim('\r', '\n')

private const val CodeFence = "```"
