package com.kolesnikovprod.ksetaorch.ui.main.chat.formatting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

internal val CodeDefaultColor = Color(0xFFE8EDF6)
internal val CodeKeywordColor = Color(0xFFFF8FB8)
internal val CodeTypeColor = Color(0xFFFFD66B)
internal val CodeMethodColor = Color(0xFFB7F774)
internal val CodePrimitiveColor = Color(0xFFFFA45B)
internal val CodeCommentColor = Color(0xFF7F8796)

internal fun highlightJvmCode(
    code: String,
    language: KsenaxCodeLanguage,
): AnnotatedString {
    if (language == KsenaxCodeLanguage.PlainText) {
        return AnnotatedString(code)
    }

    val keywords = when (language) {
        KsenaxCodeLanguage.Kotlin -> KotlinKeywords
        KsenaxCodeLanguage.Java -> JavaKeywords
        KsenaxCodeLanguage.PlainText -> emptySet()
    }
    val primitives = when (language) {
        KsenaxCodeLanguage.Kotlin -> KotlinPrimitives
        KsenaxCodeLanguage.Java -> JavaPrimitives
        KsenaxCodeLanguage.PlainText -> emptySet()
    }

    return buildAnnotatedString {
        var index = 0
        var expectsDeclaredTypeName = false

        while (index < code.length) {
            when {
                code.startsWith("//", startIndex = index) -> {
                    val end = code.indexOf('\n', startIndex = index)
                        .takeIf { it >= 0 }
                        ?: code.length
                    appendStyled(code.substring(index, end), CodeCommentColor)
                    index = end
                }

                code.startsWith("/*", startIndex = index) -> {
                    val markerEnd = code.indexOf("*/", startIndex = index + 2)
                    val end = if (markerEnd >= 0) markerEnd + 2 else code.length
                    appendStyled(code.substring(index, end), CodeCommentColor)
                    index = end
                }

                code[index] == '"' || code[index] == '\'' -> {
                    val end = code.findQuotedLiteralEnd(index)
                    append(code.substring(index, end))
                    index = end
                }

                code[index].isDigit() -> {
                    val end = code.findNumberEnd(index)
                    appendStyled(code.substring(index, end), CodePrimitiveColor)
                    index = end
                }

                Character.isJavaIdentifierStart(code[index]) -> {
                    val end = code.findIdentifierEnd(index)
                    val identifier = code.substring(index, end)
                    val nextCharacter = code.nextNonWhitespaceCharacter(end)
                    val color = when {
                        identifier in primitives -> CodePrimitiveColor
                        identifier in keywords -> CodeKeywordColor
                        expectsDeclaredTypeName -> CodeTypeColor
                        identifier.firstOrNull()?.isUpperCase() == true -> CodeTypeColor
                        nextCharacter == '(' -> CodeMethodColor
                        else -> CodeDefaultColor
                    }

                    appendStyled(identifier, color)
                    expectsDeclaredTypeName = identifier in TypeDeclarationKeywords
                    index = end
                }

                else -> {
                    append(code[index])
                    index++
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendStyled(
    text: String,
    color: Color,
) {
    withStyle(SpanStyle(color = color)) {
        append(text)
    }
}

private fun String.findIdentifierEnd(startIndex: Int): Int {
    var index = startIndex + 1
    while (index < length && Character.isJavaIdentifierPart(this[index])) {
        index++
    }
    return index
}

private fun String.findQuotedLiteralEnd(startIndex: Int): Int {
    val quote = this[startIndex]
    var index = startIndex + 1
    var escaped = false

    while (index < length) {
        val character = this[index]
        if (character == quote && !escaped) {
            return index + 1
        }
        escaped = character == '\\' && !escaped
        if (character != '\\') {
            escaped = false
        }
        index++
    }
    return length
}

private fun String.findNumberEnd(startIndex: Int): Int {
    var index = startIndex + 1
    while (
        index < length &&
        (this[index].isLetterOrDigit() || this[index] in NumberTokenCharacters)
    ) {
        index++
    }
    return index
}

private fun String.nextNonWhitespaceCharacter(startIndex: Int): Char? {
    var index = startIndex
    while (index < length && this[index].isWhitespace()) {
        index++
    }
    return getOrNull(index)
}

private val TypeDeclarationKeywords = setOf(
    "class",
    "interface",
    "object",
    "enum",
    "record",
)

private val KotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic",
    "field", "file", "finally", "get", "import", "init", "param", "property",
    "receiver", "set", "setparam", "where", "actual", "abstract", "annotation",
    "companion", "const", "crossinline", "data", "enum", "expect", "external",
    "final", "infix", "inline", "inner", "internal", "lateinit", "noinline",
    "open", "operator", "out", "override", "private", "protected", "public",
    "reified", "sealed", "suspend", "tailrec", "vararg",
)

private val JavaKeywords = setOf(
    "abstract", "assert", "break", "case", "catch", "class", "const",
    "continue", "default", "do", "else", "enum", "exports", "extends", "final",
    "finally", "for", "goto", "if", "implements", "import", "instanceof",
    "interface", "module", "native", "new", "non-sealed", "package", "private",
    "protected", "public", "record", "requires", "return", "sealed", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "to", "transient", "transitive", "try", "uses", "volatile", "while", "with",
    "yield", "true", "false", "null",
)

private val KotlinPrimitives = setOf(
    "Any", "Boolean", "Byte", "Char", "Double", "Float", "Int", "Long",
    "Nothing", "Short", "String", "UByte", "UInt", "ULong", "UShort", "Unit",
)

private val JavaPrimitives = setOf(
    "boolean", "byte", "char", "double", "float", "int", "long", "short",
    "void",
)

private val NumberTokenCharacters = charArrayOf('.', '_')
