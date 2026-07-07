package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelFailureStage
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicModelGateState

private const val StatusSwapMillis = 160
private const val LoadingCycleMillis = 880
private val VerificationMutedColor = Color(0xFF697383)
private val VerificationSuccessColor = Color(0xFF72E6A5)
private val VerificationFailureColor = Color(0xFFFF756B)

@Composable
fun KsenaxModelVerificationOverlay(
    state: KsenaxBasicModelGateState,
    onCancel: () -> Unit,
    onOpenSupportedModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (
        state == KsenaxBasicModelGateState.Idle ||
        state == KsenaxBasicModelGateState.Ready
    ) {
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val visualState = state.toVerificationVisualState()
    val message = when (state) {
        KsenaxBasicModelGateState.CheckingPresence ->
            "Проверяю локальную установку Gemma."

        KsenaxBasicModelGateState.CheckingIntegrity ->
            "Хэш считается локально. Это может занять время."

        KsenaxBasicModelGateState.PreparingModel ->
            "Проверка пройдена. Запускаю локальную модель..."

        KsenaxBasicModelGateState.ModelPrepared ->
            "Локальная модель готова к работе."

        is KsenaxBasicModelGateState.Failure -> state.message
        KsenaxBasicModelGateState.Idle,
        KsenaxBasicModelGateState.Ready -> ""
    }
    val isFailure = state is KsenaxBasicModelGateState.Failure

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(316.dp),
        ) {
            PixelWideFrame(
                brush = sunsetBottomBarGradientBrush,
                backgroundColor = Color(0xF2070A12),
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "MODEL VERIFICATION",
                    color = Color.White,
                    fontFamily = KsenaxFontFamily.pixelOperator8Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VerificationStepRow(
                        text = "Наличие файла модели",
                        status = visualState.presence,
                    )
                    VerificationStepRow(
                        text = "Целостность файла (SHA-256)",
                        status = visualState.integrity,
                    )
                    VerificationStepRow(
                        text = "Запуск локальной модели",
                        status = visualState.modelLaunch,
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = message,
                    color = if (isFailure) {
                        VerificationFailureColor
                    } else {
                        Color(0xFF9EA8B8)
                    },
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VerificationActionButton(
                        text = if (isFailure) "Вернуться" else "Отменить",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )

                    if (isFailure) {
                        VerificationActionButton(
                            text = "Меню",
                            onClick = onOpenSupportedModels,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationStepRow(
    text: String,
    status: VerificationStepStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(tween(StatusSwapMillis)) +
                    scaleIn(tween(StatusSwapMillis), initialScale = 0.72f)) togetherWith
                    (fadeOut(tween(StatusSwapMillis)) +
                        scaleOut(tween(StatusSwapMillis), targetScale = 0.72f))
            },
            label = "verification_status_swap",
            modifier = Modifier.size(20.dp),
        ) { targetStatus ->
            PixelVerificationStatusIcon(
                status = targetStatus,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            color = when (status) {
                VerificationStepStatus.Pending -> VerificationMutedColor
                VerificationStepStatus.Loading -> Color(0xFFD6DCE7)
                VerificationStepStatus.Success -> VerificationSuccessColor
                VerificationStepStatus.Failure -> VerificationFailureColor
            },
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PixelVerificationStatusIcon(
    status: VerificationStepStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        VerificationStepStatus.Loading -> PixelLoadingIcon(modifier)
        VerificationStepStatus.Pending,
        VerificationStepStatus.Success,
        VerificationStepStatus.Failure -> {
            Canvas(modifier = modifier) {
                val cell = size.minDimension / 7f
                val pixels = when (status) {
                    VerificationStepStatus.Pending -> PendingPixels
                    VerificationStepStatus.Success -> SuccessPixels
                    VerificationStepStatus.Failure -> FailurePixels
                    VerificationStepStatus.Loading -> emptyList()
                }
                val color = when (status) {
                    VerificationStepStatus.Pending -> VerificationMutedColor
                    VerificationStepStatus.Success -> VerificationSuccessColor
                    VerificationStepStatus.Failure -> VerificationFailureColor
                    VerificationStepStatus.Loading -> Color.Transparent
                }

                pixels.forEach { (x, y) ->
                    drawRect(
                        color = color,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelLoadingIcon(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pixel_verification_loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = LoadingPixels.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = LoadingCycleMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pixel_verification_loading_phase",
    )

    Canvas(modifier = modifier) {
        val cell = size.minDimension / 5f
        val activeIndex = phase.toInt() % LoadingPixels.size

        LoadingPixels.forEachIndexed { index, (x, y) ->
            val distance = (activeIndex - index + LoadingPixels.size) % LoadingPixels.size
            val alpha = when (distance) {
                0 -> 1f
                1 -> 0.72f
                2 -> 0.46f
                else -> 0.2f
            }
            drawRect(
                brush = sunsetBottomBarGradientBrush,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell, cell),
                alpha = alpha,
            )
        }
    }
}

@Composable
private fun VerificationActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PixelWideFrame(
            brush = sunsetBottomBarGradientBrush,
            backgroundColor = Color(0xFF090D16),
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = text,
            color = Color.White,
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize = 16.sp,
        )
    }
}

private fun KsenaxBasicModelGateState.toVerificationVisualState(): VerificationVisualState {
    return when (this) {
        KsenaxBasicModelGateState.CheckingPresence -> VerificationVisualState(
            presence = VerificationStepStatus.Loading,
            integrity = VerificationStepStatus.Pending,
            modelLaunch = VerificationStepStatus.Pending,
        )

        KsenaxBasicModelGateState.CheckingIntegrity -> VerificationVisualState(
            presence = VerificationStepStatus.Success,
            integrity = VerificationStepStatus.Loading,
            modelLaunch = VerificationStepStatus.Pending,
        )

        KsenaxBasicModelGateState.PreparingModel -> VerificationVisualState(
            presence = VerificationStepStatus.Success,
            integrity = VerificationStepStatus.Success,
            modelLaunch = VerificationStepStatus.Loading,
        )

        KsenaxBasicModelGateState.ModelPrepared -> VerificationVisualState(
            presence = VerificationStepStatus.Success,
            integrity = VerificationStepStatus.Success,
            modelLaunch = VerificationStepStatus.Success,
        )

        is KsenaxBasicModelGateState.Failure -> when (stage) {
            KsenaxBasicModelFailureStage.Presence -> VerificationVisualState(
                presence = VerificationStepStatus.Failure,
                integrity = VerificationStepStatus.Pending,
                modelLaunch = VerificationStepStatus.Pending,
            )

            KsenaxBasicModelFailureStage.Integrity -> VerificationVisualState(
                presence = VerificationStepStatus.Success,
                integrity = VerificationStepStatus.Failure,
                modelLaunch = VerificationStepStatus.Pending,
            )

            KsenaxBasicModelFailureStage.Preparation -> VerificationVisualState(
                presence = VerificationStepStatus.Success,
                integrity = VerificationStepStatus.Success,
                modelLaunch = VerificationStepStatus.Failure,
            )
        }

        KsenaxBasicModelGateState.Idle,
        KsenaxBasicModelGateState.Ready -> VerificationVisualState(
            presence = VerificationStepStatus.Pending,
            integrity = VerificationStepStatus.Pending,
            modelLaunch = VerificationStepStatus.Pending,
        )
    }
}

private data class VerificationVisualState(
    val presence: VerificationStepStatus,
    val integrity: VerificationStepStatus,
    val modelLaunch: VerificationStepStatus,
)

private enum class VerificationStepStatus {
    Pending,
    Loading,
    Success,
    Failure,
}

private val PendingPixels = listOf(
    2 to 1,
    4 to 1,
    1 to 2,
    5 to 2,
    1 to 4,
    5 to 4,
    2 to 5,
    4 to 5,
)

private val SuccessPixels = listOf(
    1 to 3,
    2 to 4,
    3 to 5,
    4 to 4,
    4 to 3,
    5 to 2,
    6 to 1,
)

private val FailurePixels = listOf(
    1 to 1,
    2 to 2,
    3 to 3,
    4 to 4,
    5 to 5,
    5 to 1,
    4 to 2,
    2 to 4,
    1 to 5,
)

private val LoadingPixels = listOf(
    1 to 0,
    3 to 0,
    4 to 1,
    4 to 3,
    3 to 4,
    1 to 4,
    0 to 3,
    0 to 1,
)
