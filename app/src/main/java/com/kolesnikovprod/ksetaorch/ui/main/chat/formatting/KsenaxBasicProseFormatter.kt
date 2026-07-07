package com.kolesnikovprod.ksetaorch.ui.main.chat.formatting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily

private val InlineCodeTextColor = Color(0xFFDDE3EC)
internal const val InlineCodeAnnotationTag = "ksenax_inline_code"

internal sealed interface KsenaxBasicProseElement {
    data class Paragraph(val text: String) : KsenaxBasicProseElement

    data class Heading(
        val text: String,
        val level: HeadingLevel,
    ) : KsenaxBasicProseElement
}

internal enum class HeadingLevel(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
) {
    H1(25.sp, 30.sp),
    H2(23.sp, 28.sp),
    H3(21.sp, 26.sp),
    H4(19.sp, 24.sp),
    H5(18.sp, 22.sp),
    H6(17.sp, 21.sp),
    ;

    companion object {
        fun from(markerCount: Int): HeadingLevel {
            return entries[markerCount - 1]
        }
    }
}

internal fun parseBasicProseElements(text: String): List<KsenaxBasicProseElement> {
    val elements = mutableListOf<KsenaxBasicProseElement>()
    val paragraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        elements += KsenaxBasicProseElement.Paragraph(
            text = paragraphLines.joinToString(separator = "\n"),
        )
        paragraphLines.clear()
    }

    text.split('\n').forEach { sourceLine ->
        val line = sourceLine.removeSuffix("\r")
        val heading = line.toHeadingOrNull()

        when {
            heading != null -> {
                flushParagraph()
                elements += heading
            }

            line.isBlank() -> flushParagraph()
            else -> paragraphLines += line
        }
    }
    flushParagraph()

    return elements
}

internal fun String.toBasicInlineAnnotatedString(
    forceBold: Boolean = false,
): AnnotatedString {
    var index = 0
    var isBold = false
    var isInlineCode = false

    return buildAnnotatedString {
        while (index < this@toBasicInlineAnnotatedString.length) {
            if (
                !isInlineCode &&
                index + 1 < this@toBasicInlineAnnotatedString.length &&
                this@toBasicInlineAnnotatedString[index] == '*' &&
                this@toBasicInlineAnnotatedString[index + 1] == '*'
            ) {
                isBold = !isBold
                index += 2
                continue
            }

            if (this@toBasicInlineAnnotatedString[index] == '`') {
                isInlineCode = !isInlineCode
                index++
                continue
            }

            val chunkStart = index
            while (index < this@toBasicInlineAnnotatedString.length) {
                val isInlineCodeMarker =
                    this@toBasicInlineAnnotatedString[index] == '`'
                val isBoldMarker =
                    !isInlineCode &&
                        index + 1 < this@toBasicInlineAnnotatedString.length &&
                        this@toBasicInlineAnnotatedString[index] == '*' &&
                        this@toBasicInlineAnnotatedString[index + 1] == '*'
                if (isInlineCodeMarker || isBoldMarker) {
                    break
                }
                index++
            }

            val chunk = this@toBasicInlineAnnotatedString.substring(chunkStart, index)
            if (isInlineCode) {
                pushStringAnnotation(
                    tag = InlineCodeAnnotationTag,
                    annotation = InlineCodeAnnotationTag,
                )
                withStyle(
                    SpanStyle(
                        color = InlineCodeTextColor,
                        fontFamily = KsenaxFontFamily.epilepsySansBoldForBasicFont,
                    ),
                ) {
                    append(chunk)
                }
                pop()
            } else {
                withStyle(
                    SpanStyle(
                        fontFamily = if (forceBold || isBold) {
                            KsenaxFontFamily.epilepsySansBoldForBasicFont
                        } else {
                            KsenaxFontFamily.epilepsySansForBasicFont
                        },
                    ),
                ) {
                    append(chunk)
                }
            }
        }
    }
}

private fun String.toHeadingOrNull(): KsenaxBasicProseElement.Heading? {
    var markerCount = 0
    while (markerCount < length && markerCount < MaxHeadingLevel && this[markerCount] == '#') {
        markerCount++
    }

    val hasValidMarker =
        markerCount in 1..MaxHeadingLevel &&
            getOrNull(markerCount)?.let(Char::isWhitespace) == true
    if (!hasValidMarker) {
        return null
    }

    var contentStart = markerCount
    while (contentStart < length && this[contentStart] in charArrayOf(' ', '\t')) {
        contentStart++
    }

    return KsenaxBasicProseElement.Heading(
        text = substring(contentStart),
        level = HeadingLevel.from(markerCount),
    )
}

private const val MaxHeadingLevel = 6
