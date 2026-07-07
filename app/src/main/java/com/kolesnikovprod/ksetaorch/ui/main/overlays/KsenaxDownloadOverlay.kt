package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.components.PixelToggleIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.mainGradient
import com.kolesnikovprod.ksetaorch.ui.theme.design.mintLoaderGradientBrush
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxInstallOverlayTarget
import com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxModelDownloadOverlayState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val ModelCardWidth = 302.dp
private val DialogButtonBrush = Brush.linearGradient(
    listOf(
        Color(0xFFD8DBE6),
        Color(0xFF9AA0AE),
        Color(0xFF6F7684),
    ),
)
private val PathGray = Color(0xFF747C89)
private val FilledButtonColor = Color(0xFFC8CAD4)
private val OverlayScrim = Color.Black.copy(alpha = 0.86f)

@Composable
fun KsenaxDownloadOverlay(
    state: KsenaxModelDownloadOverlayState,
    target: KsenaxInstallOverlayTarget?,
    progress: Float,
    allowOverMeteredNetwork: Boolean,
    allowOverRoaming: Boolean,
    isCancelConfirmationVisible: Boolean,
    onAllowOverMeteredNetworkChange: (Boolean) -> Unit,
    onAllowOverRoamingChange: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    onConfirmCancelClick: () -> Unit,
    onKeepDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state != KsenaxModelDownloadOverlayState.Hidden && target != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .drawWithCache {
                        onDrawBehind {
                            drawRect(color = OverlayScrim)
                        }
                    },
            )

            when (state) {
                KsenaxModelDownloadOverlayState.Hidden -> Unit
                KsenaxModelDownloadOverlayState.ModelOffer -> ModelOfferWindow(
                    title = target?.overlayTitle.orEmpty(),
                    description = target?.overlayDescription.orEmpty(),
                    allowOverMeteredNetwork = allowOverMeteredNetwork,
                    allowOverRoaming = allowOverRoaming,
                    onAllowOverMeteredNetworkChange = onAllowOverMeteredNetworkChange,
                    onAllowOverRoamingChange = onAllowOverRoamingChange,
                    onInstallClick = onInstallClick,
                    onBackClick = onBackClick,
                )
                KsenaxModelDownloadOverlayState.Downloading -> DownloadProgressWindow(
                    progress = progress,
                    onCancelClick = onCancelClick,
                )
                KsenaxModelDownloadOverlayState.Unpacking -> ModelUnpackingWindow()
            }

            AnimatedVisibility(
                visible = isCancelConfirmationVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 140)),
                exit = fadeOut(animationSpec = tween(durationMillis = 110)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                CancelConfirmationWindow(
                    onConfirmCancelClick = onConfirmCancelClick,
                    onKeepDownloadClick = onKeepDownloadClick,
                )
            }
        }
    }
}

@Composable
private fun ModelUnpackingWindow(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(ModelCardWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelLoaderFace(
            progress = 0.5f,
            modifier = Modifier.size(142.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        GradientText(
            text = "Распаковка модели...",
            brush = mintLoaderGradientBrush,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontFamily = KsenaxFontFamily.minecraftFont,
        )
    }
}

@Composable
private fun ModelOfferWindow(
    title: String,
    description: String,
    allowOverMeteredNetwork: Boolean,
    allowOverRoaming: Boolean,
    onAllowOverMeteredNetworkChange: (Boolean) -> Unit,
    onAllowOverRoamingChange: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelDialogFrame(
        modifier = modifier
            .width(ModelCardWidth)
            .height(294.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GradientText(
                text = title,
                brush = mainGradient,
                fontSize = 30.sp,
                lineHeight = 26.sp,
                fontFamily = KsenaxFontFamily.jersey10,
            )

            Spacer(modifier = Modifier.height(11.dp))

            Text(
                text = description,
                color = PathGray,
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            NetworkPolicyToggleRow(
                text = "Сеть с лимитом",
                isEnabled = allowOverMeteredNetwork,
                onClick = {
                    onAllowOverMeteredNetworkChange(!allowOverMeteredNetwork)
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            NetworkPolicyToggleRow(
                text = "Роуминг",
                isEnabled = allowOverRoaming,
                onClick = {
                    onAllowOverRoamingChange(!allowOverRoaming)
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelDialogButton(
                    text = "Установить",
                    onClick = onInstallClick,
                    filled = true,
                    modifier = Modifier.size(width = 128.dp, height = 42.dp),
                )

                PixelDialogButton(
                    text = "Назад",
                    onClick = onBackClick,
                    filled = false,
                    modifier = Modifier.size(width = 96.dp, height = 42.dp),
                )
            }
        }
    }
}

@Composable
private fun NetworkPolicyToggleRow(
    text: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = PathGray,
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize = 13.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )

        PixelToggleIcon(
            isEnabled = isEnabled,
            contentDescription =
                if (isEnabled) "$text разрешено" else "$text запрещено",
            modifier = Modifier.size(width = 52.dp, height = 25.dp),
        )
    }
}

@Composable
private fun DownloadProgressWindow(
    progress: Float,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = (progress.coerceIn(0f, 1f) * 100f)
        .roundToInt()
        .coerceIn(0, 100)

    Column(
        modifier = modifier.width(ModelCardWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelLoaderFace(
            progress = progress,
            modifier = Modifier.size(142.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        GradientText(
            text = "$percent%",
            brush = mintLoaderGradientBrush,
            fontSize = 20.sp,
            lineHeight = 20.sp,
            fontFamily = KsenaxFontFamily.minecraftFont,
        )

        Spacer(modifier = Modifier.height(24.dp))

        PixelDialogButton(
            text = "Отменить",
            onClick = onCancelClick,
            filled = false,
            modifier = Modifier.size(width = 122.dp, height = 42.dp),
        )
    }
}

@Composable
private fun CancelConfirmationWindow(
    onConfirmCancelClick: () -> Unit,
    onKeepDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelDialogFrame(
        modifier = modifier
            .width(294.dp)
            .height(156.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Точно отменить\nзагрузку модели?",
                color = Color(0xFFE5E7EF),
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelDialogButton(
                    text = "Да",
                    onClick = onConfirmCancelClick,
                    filled = true,
                    modifier = Modifier.size(width = 82.dp, height = 40.dp),
                )

                PixelDialogButton(
                    text = "Нет",
                    onClick = onKeepDownloadClick,
                    filled = false,
                    modifier = Modifier.size(width = 82.dp, height = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun PixelDialogFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        PixelWideFrame(
            brush = inactiveGradientBrush,
            modifier = Modifier.matchParentSize(),
            backgroundColor = Color(0xF2050710),
        )

        content()
    }
}

@Composable
private fun PixelDialogButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = if (filled) Color(0xFF111620) else Color(0xFFD7DAE4)

    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        PixelWideFrame(
            brush = DialogButtonBrush,
            modifier = Modifier.matchParentSize(),
            backgroundColor = if (filled) FilledButtonColor else Color(0xCC050710),
        )

        Text(
            text = text,
            color = textColor,
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PixelLoaderFace(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_loader_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2_400,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pixel_loader_rotation_value",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        PixelProgressRing(
            progress = progress,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation },
        )

        PixelProgressFace(
            progress = progress,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun PixelProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 7.dp.toPx()
        val radius = size.minDimension / 2f - pixel * 1.4f
        val center = Offset(size.width / 2f, size.height / 2f)
        val totalPixels = 32
        val activePixels = (totalPixels * progress.coerceIn(0f, 1f))
            .roundToInt()
            .coerceIn(1, totalPixels)

        repeat(totalPixels) { index ->
            val angle = Math.toRadians((360.0 / totalPixels) * index)
            val topLeft = Offset(
                x = center.x + cos(angle).toFloat() * radius - pixel / 2f,
                y = center.y + sin(angle).toFloat() * radius - pixel / 2f,
            )

            drawRect(
                brush = if (index < activePixels) mintLoaderGradientBrush else inactiveGradientBrush,
                topLeft = topLeft,
                size = Size(pixel, pixel),
                alpha = if (index < activePixels) 1f else 0.42f,
            )
        }
    }
}

@Composable
private fun PixelProgressFace(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cell = size.minDimension / 9f
        val left = (size.width - cell * 9f) / 2f
        val top = (size.height - cell * 9f) / 2f

        fun drawPixel(x: Int, y: Int) {
            drawRect(
                brush = mintLoaderGradientBrush,
                topLeft = Offset(
                    x = left + x * cell,
                    y = top + y * cell,
                ),
                size = Size(cell, cell),
            )
        }

        drawPixel(2, 2)
        drawPixel(6, 2)

        val mouth = when {
            progress < 0.4f -> listOf(
                2 to 6,
                3 to 5,
                4 to 5,
                5 to 5,
                6 to 6,
            )
            progress < 0.7f -> listOf(
                2 to 5,
                3 to 5,
                4 to 5,
                5 to 5,
                6 to 5,
            )
            else -> listOf(
                2 to 5,
                3 to 6,
                4 to 6,
                5 to 6,
                6 to 5,
            )
        }

        mouth.forEach { (x, y) -> drawPixel(x, y) }
    }
}

@Composable
private fun GradientText(
    text: String,
    brush: Brush,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        textAlign = TextAlign.Center,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = brush,
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    )
}
