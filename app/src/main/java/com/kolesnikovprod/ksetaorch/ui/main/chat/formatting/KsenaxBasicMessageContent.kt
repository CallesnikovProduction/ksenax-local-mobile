package com.kolesnikovprod.ksetaorch.ui.main.chat.formatting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import kotlinx.coroutines.delay

private const val CopyFeedbackMillis = 1_200L

@Composable
internal fun KsenaxBasicMessageContent(
    rawText: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(rawText) {
        parseBasicMessageBlocks(rawText)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is KsenaxBasicMessageBlock.Prose -> {
                    KsenaxBasicProseContent(
                        text = block.text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is KsenaxBasicMessageBlock.Code -> {
                    KsenaxCodeBlock(
                        code = block.code,
                        language = block.language,
                        languageLabel = block.languageLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun KsenaxBasicProseContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    val elements = remember(text) {
        parseBasicProseElements(text)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        elements.forEach { element ->
            when (element) {
                is KsenaxBasicProseElement.Paragraph -> {
                    KsenaxInlineCodeText(
                        text = remember(element.text) {
                            element.text.toBasicInlineAnnotatedString()
                        },
                        color = Color(0xFFF4F8FF),
                        fontFamily = KsenaxFontFamily.epilepsySansForBasicFont,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                    )
                }

                is KsenaxBasicProseElement.Heading -> {
                    KsenaxInlineCodeText(
                        text = remember(element.text, element.level) {
                            element.text.toBasicInlineAnnotatedString(forceBold = true)
                        },
                        color = Color(0xFFF4F8FF),
                        fontFamily = KsenaxFontFamily.epilepsySansBoldForBasicFont,
                        fontSize = element.level.fontSize,
                        lineHeight = element.level.lineHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun KsenaxCodeBlock(
    code: String,
    language: KsenaxCodeLanguage,
    languageLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    val highlightedCode = remember(code, language) {
        highlightJvmCode(code, language)
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(CopyFeedbackMillis)
            copied = false
        }
    }

    Box(
        modifier = modifier,
    ) {
        PixelWideFrame(
            brush = inactiveGradientBrush,
            backgroundColor = Color(0xFF050811),
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = languageLabel,
                    color = Color(0xFF8F98A8),
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 11.sp,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (copied) "COPIED" else "COPY",
                    color = if (copied) CodeMethodColor else Color(0xFFB7C0F8),
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                context.copyCodeBlock(code)
                                copied = true
                            },
                        )
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                )
            }

            Text(
                text = highlightedCode,
                color = CodeDefaultColor,
                fontFamily = KsenaxFontFamily.epilepsySansForBasicFont,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        start = 10.dp,
                        top = 7.dp,
                        end = 10.dp,
                        bottom = 11.dp,
                    ),
            )
        }
    }
}

private fun Context.copyCodeBlock(code: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(
        ClipData.newPlainText("Ksenax code block", code),
    )
}
