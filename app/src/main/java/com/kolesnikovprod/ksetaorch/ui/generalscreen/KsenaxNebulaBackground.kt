package com.kolesnikovprod.ksetaorch.ui.generalscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Декоративный космический фон главного экрана Ksenax.
 *
 * Фон рисуется через Canvas и не содержит интерактивной логики.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun KsenaxNebulaBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF050712),
                    Color(0xFF090B1D),
                    Color(0xFF04050D),
                ),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width * 0.5f, height * 0.47f)
            val orbRadius = width * 0.19f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1F245C).copy(alpha = 0.72f),
                        Color.Transparent,
                    ),
                    center = Offset(width * 0.5f, height * 0.45f),
                    radius = width * 0.75f,
                ),
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8A55FF).copy(alpha = 0.30f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = width * 0.52f,
                ),
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF36C7FF).copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                    center = Offset(width * 0.66f, height * 0.40f),
                    radius = width * 0.42f,
                ),
            )

            drawOrbitField(center = center, width = width, height = height)
            drawStarField(width = width, height = height)
            drawAgentCore(center = center, radius = orbRadius)
        }
    }
}

/**
 * Рисует орбитальные линии вокруг центрального ядра.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
private fun DrawScope.drawOrbitField(center: Offset, width: Float, height: Float) {
    val orbitWidth = width * 0.92f
    val orbitHeight = height * 0.18f
    val topLeft = Offset(center.x - orbitWidth / 2f, center.y - orbitHeight / 2f)
    val orbitSize = Size(orbitWidth, orbitHeight)
    val strokeWidth = 1.dp.toPx()

    listOf(-18f, 8f, 31f).forEachIndexed { index, degrees ->
        rotate(degrees = degrees, pivot = center) {
            drawOval(
                color = Color(0xFF7C58FF).copy(alpha = 0.16f - index * 0.03f),
                topLeft = topLeft,
                size = orbitSize,
                style = Stroke(width = strokeWidth),
            )
        }
    }

    listOf(
        Offset(width * 0.15f, height * 0.51f),
        Offset(width * 0.72f, height * 0.58f),
        Offset(width * 0.88f, height * 0.39f),
    ).forEach { dotCenter ->
        drawCircle(
            color = Color(0xFF9C70FF).copy(alpha = 0.74f),
            radius = 2.1.dp.toPx(),
            center = dotCenter,
        )
    }
}

/**
 * Рисует статичное поле световых точек.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
private fun DrawScope.drawStarField(width: Float, height: Float) {
    listOf(
        0.11f to 0.42f,
        0.25f to 0.32f,
        0.38f to 0.47f,
        0.70f to 0.32f,
        0.78f to 0.50f,
        0.92f to 0.45f,
        0.57f to 0.58f,
        0.31f to 0.61f,
    ).forEachIndexed { index, point ->
        val alpha = if (index % 2 == 0) 0.72f else 0.42f
        drawCircle(
            color = Color(0xFF8C67FF).copy(alpha = alpha),
            radius = (0.9f + index % 3).dp.toPx(),
            center = Offset(width * point.first, height * point.second),
        )
    }
}

/**
 * Рисует центральное светящееся ядро агента.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
private fun DrawScope.drawAgentCore(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF8A58FF).copy(alpha = 0.26f),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 2.4f,
        ),
        radius = radius * 2.4f,
        center = center,
    )

    listOf(0.94f, 1.08f, 1.22f).forEachIndexed { index, scale ->
        drawCircle(
            color = Color(0xFF806BFF).copy(alpha = 0.28f - index * 0.06f),
            radius = radius * scale,
            center = center,
            style = Stroke(width = (1.2f + index).dp.toPx()),
        )
    }

    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFF9E67FF),
                Color(0xFF5BC9FF),
                Color(0xFFE0D4FF),
                Color(0xFF9E67FF),
            ),
            center = center,
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 4.dp.toPx()),
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF7862FF).copy(alpha = 0.22f),
                Color(0xFF3EAFFF).copy(alpha = 0.10f),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 0.95f,
        ),
        radius = radius * 0.95f,
        center = center,
    )

    drawSparkle(
        center = center,
        radius = radius * 0.24f,
        color = Color.White.copy(alpha = 0.92f),
    )
}

/**
 * Рисует маленький ромбовидный блик внутри ядра.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val shortRadius = radius * 0.34f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + shortRadius, center.y - shortRadius, center.x + radius, center.y)
        quadraticTo(center.x + shortRadius, center.y + shortRadius, center.x, center.y + radius)
        quadraticTo(center.x - shortRadius, center.y + shortRadius, center.x - radius, center.y)
        quadraticTo(center.x - shortRadius, center.y - shortRadius, center.x, center.y - radius)
        close()
    }

    drawPath(path = path, color = color)
}
