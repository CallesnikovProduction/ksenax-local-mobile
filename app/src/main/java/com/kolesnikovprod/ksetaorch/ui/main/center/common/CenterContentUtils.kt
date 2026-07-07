package com.kolesnikovprod.ksetaorch.ui.main.center.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.mainGradient
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Отображает путь рабочей папки с эффектом посимвольной печати.
 *
 * Компонент используется как верхняя информационная подпись для agentic-режима:
 * когда [isVisible] становится `true`, значение [path] постепенно раскрывается
 * слева направо. При скрытии подписи текущий отображаемый текст сбрасывается,
 * чтобы при следующем появлении анимация началась заново.
 *
 * @since 0.2
 */
@Composable internal fun TypingAndroidPathLabel(
    path:      String,
    isVisible: Boolean,
    modifier:  Modifier = Modifier,
) {
    var visiblePath by rememberSaveable(path) {
        mutableStateOf("")
    }

    LaunchedEffect(isVisible, path) {
        if (!isVisible) {
            visiblePath = ""
            return@LaunchedEffect
        }

        if (visiblePath.length < path.length) {
            for (count in (visiblePath.length + 1)..path.length) {
                visiblePath = path.take(count)
                delay(PathTypeCharMillis.milliseconds)
            }
        }
    }

    AnimatedVisibility(
        visible  = isVisible,
        enter    = fadeIn(animationSpec = tween(durationMillis = 120)),
        exit     = fadeOut(animationSpec = tween(durationMillis = 100)),
        modifier = modifier,
    ) {
        Text(
            text       = visiblePath,
            color      = Color(0xFF747C89),
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize   = 10.sp,
            lineHeight = 12.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Отображает крупный логотипный заголовок с градиентной заливкой текста.
 *
 * Компонент сначала рисует текст белым цветом во временный offscreen-слой,
 * а затем накладывает поверх него [mainGradient] через [BlendMode.SrcAtop].
 * Благодаря этому градиент применяется только к форме букв, не затрагивая
 * область вокруг текста.
 *
 * @since 0.2
 */
@Composable internal fun GradientHeroText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text           = text,
        color          = Color.White,
        style          = TextStyle(
            fontFamily = KsenaxFontFamily.jersey10,
            fontSize   = 104.sp,
            lineHeight = 92.sp,
            textAlign  = TextAlign.Center,
        ),
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush     = mainGradient,
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    )
}

/**
 * Отображает пиксельный декоративный разделитель под hero-заголовком:
 *
 * ```
 * ...  ────────   ✦   ────────  ...
 * ```
 *
 * Компонент рисует две горизонтальные градиентные линии, набор крайних
 * пиксельных точек и центральную пиксельную звезду. Вся геометрия строится
 * относительно центра доступного [Canvas], поэтому разделитель сохраняет
 * симметрию при изменении размера контейнера.
 *
 * @since 0.2
 */
@Composable internal fun PixelHeroDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pixel        = 3.dp.toPx()
        val centerX      = size.width / 2f
        val centerY      = size.height / 2f
        val lineHeight   = pixel
        val lineY        = centerY - lineHeight / 2f
        val diamondGap   = pixel * 8f
        val dotGap       = pixel * 3f
        val dotSize      = pixel
        val segmentWidth = pixel * 22f
        val dotCount     = 3

        drawRect(
            brush   = mainGradient,
            topLeft = Offset(centerX - diamondGap - segmentWidth, lineY),
            size    = Size(segmentWidth, lineHeight),
        )
        drawRect(
            brush   = mainGradient,
            topLeft = Offset(centerX + diamondGap, lineY),
            size    = Size(segmentWidth, lineHeight),
        )

        repeat(dotCount) { index ->
            val offset = (index + 1) * (dotSize + dotGap)

            drawRect(
                brush   = mainGradient,
                topLeft = Offset(centerX - diamondGap - segmentWidth - offset, lineY),
                size    = Size(dotSize, dotSize),
            )
            drawRect(
                brush   = mainGradient,
                topLeft = Offset(centerX + diamondGap + segmentWidth + dotGap + index * (dotSize + dotGap), lineY),
                size    = Size(dotSize, dotSize),
            )
        }

        val star = listOf(
            0  to -4,
            0  to -3,
            -1 to -2,
            1  to -2,
            -2 to -1,
            2  to -1,
            -4 to 0,
            -3 to 0,
            -2 to 0,
            2  to 0,
            3  to 0,
            4  to 0,
            -2 to 1,
            2  to 1,
            -1 to 2,
            1  to 2,
            0  to 3,
            0  to 4,
        )

        star.forEach { (x, y) ->
            drawRect(
                brush   = mainGradient,
                topLeft = Offset(
                    x = centerX + x * pixel - pixel / 2f,
                    y = centerY + y * pixel - pixel / 2f,
                ),
                size    = Size(pixel, pixel),
            )
        }
    }
}

/**
 * Отображает анимированную фразу с эффектом терминальной печати.
 *
 * Компонент циклически выбирает фразы из [KsenaxTypingPhrases], печатает их
 * посимвольно, удерживает на экране, затем стирает и переходит к следующей
 * фразе. Отдельный эффект управляет мигающим курсором, который визуально
 * имитирует ввод в консоли.
 *
 * Когда [isStarted] становится `false`, анимация останавливается, текст
 * очищается, а курсор скрывается.
 *
 * @since 0.2
 */
@Composable internal fun TypingPhraseText(
    isStarted: Boolean  = true,
    modifier:  Modifier = Modifier,
) {

    /**
     * Текущая напечатанная часть фразы
     * */
    var visibleText by remember { mutableStateOf("") }

    /**
     * Показывать ли символ `_`
     */
    var cursorVisible by remember { mutableStateOf(true) }

    /*
     * Мигание курсора через отдельную корутину, отвечающую за это
     */
    LaunchedEffect(isStarted) {
        if (!isStarted) {
            cursorVisible = false
            return@LaunchedEffect
        }

        cursorVisible = true

        while (true) {
            delay(CursorBlinkMillis.milliseconds)
            cursorVisible = !cursorVisible
        }
    }

    /*
     * Печать фраз через отдельную корутину
     */
    LaunchedEffect(isStarted) {
        if (!isStarted) {
            visibleText = ""
            return@LaunchedEffect
        }

        var currentPhrase = KsenaxTypingPhrases.random()
        var phraseDeck = KsenaxTypingPhrases
            .shuffled()
            .filter { it != currentPhrase }
            .toMutableList()

        while (true) {
            // сама печать посимвольно с «живой» задержкой печати
            for (count in 1..currentPhrase.length) {
                visibleText = currentPhrase.take(count)
                delay(currentPhrase[count - 1].typingDelayMillis().milliseconds)
            }

            // удержание фразы
            delay(PhraseHoldMillis.milliseconds)

            // стирание фразы
            for (count in currentPhrase.length downTo 0) {
                visibleText = currentPhrase.take(count)
                delay(DeleteCharMillis.milliseconds)
            }

            // снова колода фраз перемешивается, когда подошла к концу
            if (phraseDeck.isEmpty()) {
                phraseDeck = KsenaxTypingPhrases
                    .shuffled()
                    .filter { it != currentPhrase }
                    .toMutableList()
            }

            val nextPhrase = phraseDeck.removeAt(0)
            delay(180L.milliseconds)

            currentPhrase = nextPhrase
        }
    }

    Text(
        text = buildAnnotatedString {
            append(visibleText)
            if (cursorVisible) {
                append("_")
            } else {
                withStyle(SpanStyle(color = Color.Transparent)) {
                    append("_")
                }
            }
        },
        color      = Color(0xFF4FA9FF),
        fontFamily = KsenaxFontFamily.minecraftFont,
        fontSize   = 12.sp,
        lineHeight = 17.sp,
        textAlign  = TextAlign.Center,
        modifier   = modifier,
    )
}

/**
 * Возвращает задержку печати для конкретного символа.
 *
 * Перенос строки печатается медленнее, знаки препинания и пробелы получают
 * небольшую паузу, а обычные символы используют стандартную скорость печати.
 * Это делает анимацию менее механической и ближе к естественному набору текста.
 *
 * @since 0.2
 */
private fun Char.typingDelayMillis(): Long {
    return when (this) {
        '\n'               -> 180L
        ' ', ',', '.', '!' -> 58L
        else               -> TypeCharMillis
    }
}