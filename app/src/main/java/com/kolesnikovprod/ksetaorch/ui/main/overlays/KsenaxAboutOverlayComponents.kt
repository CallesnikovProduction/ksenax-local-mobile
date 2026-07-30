package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CARD_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_PRIMARY_TEXT_COLOUR

private val AboutInnerBackground = Color(0xE6060911)

private val AboutSections = listOf(
    "OpenKsenax — local-first оболочка для самостоятельных аддонов. " +
        "Пользователь сам выбирает и устанавливает только те возможности, " +
        "которые ему действительно нужны.",
    "Каждый аддон живёт отдельным Android-приложением: владеет своим " +
        "интерфейсом, логикой, разрешениями и жизненным циклом. OpenKsenax " +
        "проверяет совместимость и даёт контролируемый доступ к локальным моделям.",
    "Общение с ИИ и агентные действия остаются частью системы, но не её " +
        "единственной целью. Главное — расширяемая среда, где сценарии " +
        "подключаются осознанно и работают на устройстве пользователя.",
)

/**
 * Содержимое about-overlay с самостоятельной пиксельной геометрией.
 *
 * @since 0.3
 */
@Composable
internal fun KsenaxAboutCard(
    theme: KsenaxThemeVisuals,
    versionLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.96f)
            .widthIn(max = 390.dp)
            .heightIn(max = 700.dp),
    ) {
        AboutLayeredFrame(
            theme = theme,
            modifier = Modifier.matchParentSize(),
        )
        AboutBackdropPixels(
            theme = theme,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutBrandMark(theme = theme)

            Spacer(modifier = Modifier.height(7.dp))

            DownloadGradientText(
                text = "OpenKsenax $versionLabel",
                brush = theme.overlayMainBrush,
                fontSize = 29.sp,
                lineHeight = 32.sp,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            )

            Spacer(modifier = Modifier.height(14.dp))

            AboutLocalFirstCapsule(theme = theme)

            Spacer(modifier = Modifier.height(13.dp))

            AboutTextPanel(theme = theme)

            Spacer(modifier = Modifier.height(16.dp))

            AboutHeartDivider(theme = theme)
        }
    }
}

@Composable
private fun AboutLayeredFrame(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        PixelSteppedCornerFrame(
            brush = theme.overlayMainBrush,
            backgroundColor = OVERLAY_CARD_BACKGROUND_COLOUR,
            strokeWidth = 2.dp,
            modifier = Modifier.matchParentSize(),
        )
        PixelSteppedCornerFrame(
            brush = theme.overlayMiniBrush,
            backgroundColor = Color.Transparent,
            strokeWidth = 1.dp,
            modifier = Modifier
                .matchParentSize()
                .padding(6.dp),
        )
        PixelSteppedCornerFrame(
            brush = SolidColor(theme.mutedColor.copy(alpha = 0.48f)),
            backgroundColor = Color.Transparent,
            strokeWidth = 1.dp,
            modifier = Modifier
                .matchParentSize()
                .padding(11.dp),
        )
    }
}

@Composable
private fun AboutBackdropPixels(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(16.dp)) {
        val pixel = 2.dp.toPx()
        val points = listOf(
            0.10f to 0.08f,
            0.18f to 0.16f,
            0.84f to 0.10f,
            0.91f to 0.21f,
            0.08f to 0.44f,
            0.93f to 0.53f,
            0.13f to 0.78f,
            0.87f to 0.83f,
            0.22f to 0.92f,
            0.77f to 0.94f,
        )

        points.forEachIndexed { index, (xRatio, yRatio) ->
            drawRect(
                color = theme.sparkleColors[
                    index % theme.sparkleColors.size
                ].copy(alpha = 0.34f),
                topLeft = Offset(
                    x = size.width * xRatio,
                    y = size.height * yRatio,
                ),
                size = Size(pixel, pixel),
            )
        }

        listOf(
            0.06f to 0.13f,
            0.94f to 0.36f,
            0.07f to 0.66f,
            0.92f to 0.75f,
        ).forEachIndexed { index, (xRatio, yRatio) ->
            val center = Offset(
                x = size.width * xRatio,
                y = size.height * yRatio,
            )
            val colour = theme.markerColors[
                index % theme.markerColors.size
            ].copy(alpha = 0.38f)
            drawRect(
                color = colour,
                topLeft = Offset(center.x - pixel * 2f, center.y - pixel / 2f),
                size = Size(pixel * 4f, pixel),
            )
            drawRect(
                color = colour,
                topLeft = Offset(center.x - pixel / 2f, center.y - pixel * 2f),
                size = Size(pixel, pixel * 4f),
            )
        }
    }
}

@Composable
private fun AboutBrandMark(theme: KsenaxThemeVisuals) {
    Box(
        modifier = Modifier
            .width(154.dp)
            .height(96.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AboutGradientPlus(
            brush = theme.overlayMiniBrush,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 22.dp),
        )

        GradientIcon(
            drawableId = R.drawable.olay_about_info,
            contentDescription = null,
            brush = theme.overlayMainBrush,
            modifier = Modifier.size(74.dp),
        )

        AboutGradientPlus(
            brush = theme.overlayMainBrush,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 7.dp, top = 8.dp),
        )

        AboutBrandPedestal(
            theme = theme,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(128.dp)
                .height(29.dp),
        )
    }
}

/**
 * Рисует ступенчатый пиксельный пьедестал и расходящуюся вниз тень.
 *
 * Верхние полосы образуют яркую площадку под брендовой иконкой. Каждый
 * следующий слой становится шире и прозрачнее, поэтому получается ощущение
 * свечения без размытия и непиксельных blur-эффектов.
 *
 * @since 0.3
 */
@Composable
private fun AboutBrandPedestal(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 2.dp.toPx()
        val centerX = size.width / 2f

        fun drawCentredBrushBar(
            width: Float,
            top: Float,
            height: Float,
            alpha: Float,
        ) {
            drawRect(
                brush = theme.overlayMainBrush,
                topLeft = Offset(
                    x = centerX - width / 2f,
                    y = top,
                ),
                size = Size(width, height),
                alpha = alpha,
            )
        }

        fun drawCentredShadowBar(
            width: Float,
            top: Float,
            height: Float,
            alpha: Float,
        ) {
            drawRect(
                color = theme.mutedColor.copy(alpha = alpha),
                topLeft = Offset(
                    x = centerX - width / 2f,
                    y = top,
                ),
                size = Size(width, height),
            )
        }

        // Яркая верхняя площадка непосредственно под иконкой.
        drawCentredBrushBar(
            width = size.width * 0.31f,
            top = 0f,
            height = pixel,
            alpha = 1f,
        )
        drawCentredBrushBar(
            width = size.width * 0.45f,
            top = pixel * 2f,
            height = pixel * 1.5f,
            alpha = 0.82f,
        )

        // Расходящиеся ступени свечения.
        drawCentredBrushBar(
            width = size.width * 0.62f,
            top = pixel * 4.5f,
            height = pixel,
            alpha = 0.52f,
        )
        drawCentredBrushBar(
            width = size.width * 0.80f,
            top = pixel * 6.5f,
            height = pixel,
            alpha = 0.29f,
        )

        // Нижняя пиксельная тень: широкая, но без размытия.
        drawCentredShadowBar(
            width = size.width,
            top = pixel * 9f,
            height = pixel,
            alpha = 0.20f,
        )
        drawCentredShadowBar(
            width = size.width * 0.68f,
            top = pixel * 11f,
            height = pixel,
            alpha = 0.13f,
        )

        // Разрозненные боковые пиксели поддерживают форму референса.
        drawRect(
            color = theme.markerColors.first().copy(alpha = 0.42f),
            topLeft = Offset(pixel * 6f, pixel * 6.5f),
            size = Size(pixel * 2f, pixel),
        )
        drawRect(
            color = theme.markerColors.last().copy(alpha = 0.42f),
            topLeft = Offset(size.width - pixel * 8f, pixel * 6.5f),
            size = Size(pixel * 2f, pixel),
        )
    }
}

@Composable
private fun AboutLocalFirstCapsule(theme: KsenaxThemeVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AboutDashedLine(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
        AboutGradientPlus(brush = theme.overlayMiniBrush)
        Box(
            modifier = Modifier
                .width(145.dp)
                .height(31.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelSteppedCornerFrame(
                brush = theme.overlayMainBrush,
                backgroundColor = AboutInnerBackground,
                strokeWidth = 1.dp,
                modifier = Modifier.matchParentSize(),
            )
            PixelSteppedCornerFrame(
                brush = SolidColor(theme.mutedColor.copy(alpha = 0.35f)),
                backgroundColor = Color.Transparent,
                strokeWidth = 1.dp,
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp),
            )
            DownloadGradientText(
                text = "LOCAL-FIRST",
                brush = theme.overlayMainBrush,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
            )
        }
        AboutGradientPlus(brush = theme.overlayMainBrush)
        AboutDashedLine(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutTextPanel(theme: KsenaxThemeVisuals) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelSteppedCornerFrame(
            brush = theme.overlayMiniBrush,
            backgroundColor = AboutInnerBackground,
            strokeWidth = 1.dp,
            modifier = Modifier.matchParentSize(),
        )
        PixelSteppedCornerFrame(
            brush = SolidColor(theme.mutedColor.copy(alpha = 0.34f)),
            backgroundColor = Color.Transparent,
            strokeWidth = 1.dp,
            modifier = Modifier
                .matchParentSize()
                .padding(5.dp),
        )

        Column(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 17.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutSections.forEachIndexed { index, section ->
                Text(
                    text = section,
                    color = OVERLAY_PRIMARY_TEXT_COLOUR,
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )

                if (index != AboutSections.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AboutSectionDivider(theme = theme)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutSectionDivider(theme: KsenaxThemeVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AboutDashedLine(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
        AboutGradientPlus(brush = theme.overlayMainBrush)
        AboutDashedLine(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutHeartDivider(theme: KsenaxThemeVisuals) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AboutDashedLine(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
        AboutGradientPlus(brush = theme.overlayMiniBrush)
        GradientIcon(
            drawableId = R.drawable.olay_about_heart,
            contentDescription = null,
            brush = theme.overlayMainBrush,
            modifier = Modifier.size(23.dp),
        )
        AboutGradientPlus(brush = theme.overlayMainBrush)
        AboutDashedLine(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutGradientPlus(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    DownloadGradientText(
        text = "+",
        brush = brush,
        fontSize = 15.sp,
        lineHeight = 15.sp,
        fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
        modifier = modifier,
    )
}

@Composable
private fun AboutDashedLine(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .height(4.dp)
            .fillMaxWidth(),
    ) {
        val dashWidth = 5.dp.toPx()
        val dashGap = 4.dp.toPx()
        val dashHeight = 1.dp.toPx()
        val dashCount = (size.width / (dashWidth + dashGap)).toInt()

        repeat(dashCount) { index ->
            drawRect(
                color = theme.markerColors[
                    index % theme.markerColors.size
                ].copy(alpha = 0.58f),
                topLeft = Offset(
                    x = index * (dashWidth + dashGap),
                    y = (size.height - dashHeight) / 2f,
                ),
                size = Size(dashWidth, dashHeight),
            )
        }
    }
}
