package com.kolesnikovprod.ksetaorch.ui.generalscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.kolesnikovprod.ksetaorch.ui.theme.ksenaxTextGradient

/**
 * Заголовок главного экрана Ksenax.
 *
 * Показывает брендовый текст и короткое описание модели/оркестратора.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun KsenaxHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ksenax",
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                brush = ksenaxTextGradient(),
                fontSize = 72.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            ),
        )

        HeaderDivider()

        Text(
            text = buildAnnotatedString {
                append("Agentic orchestrator powered by ")
                withStyle(
                    SpanStyle(
                        brush = ksenaxTextGradient(),
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    append("Gemma-4")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFA4A6C4).copy(alpha = 0.82f),
            fontSize = 10.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Является визуальным разделителем красивым.
 * Представляется в виде: --- ✦ ---
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun HeaderDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF8F6BFF).copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )

        Text(
            text = "✦",
            modifier = Modifier.padding(horizontal = 18.dp),
            color = Color(0xFF9A68FF).copy(alpha = 0.90f),
            fontSize = 20.sp,
            lineHeight = 20.sp,
        )

        Box(
            modifier = Modifier
                .width(58.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF8F6BFF).copy(alpha = 0.34f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}
