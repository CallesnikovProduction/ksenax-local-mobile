package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.main.overlays.DownloadGradientText
import com.kolesnikovprod.ksetaorch.ui.main.overlays.PixelOverlayCard
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.LocalKsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_DIM_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ERROR_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_PRIMARY_TEXT_COLOUR

/**
 * Управляет presentation-сворачиванием validation-overlay.
 *
 * Скрытие окна не отменяет выполняющуюся проверку. Когда проверка заканчивается
 * и [state] становится `null`, следующее validation-событие снова откроет
 * overlay.
 *
 * @since 0.3
 */
@Composable
fun KsenaxModelVerificationOverlayHost(
    theme: KsenaxThemeVisuals,
    state: KsenaxModelVerificationUiState?,
    modelName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMinimized by remember { mutableStateOf(false) }
    var lastVisibleState by remember {
        mutableStateOf<KsenaxModelVerificationUiState?>(state)
    }

    LaunchedEffect(state?.faceState) {
        when (state?.faceState) {
            null,
            KsenaxModelVerificationFaceState.Failure,
            -> isMinimized = false

            KsenaxModelVerificationFaceState.InProcess,
            KsenaxModelVerificationFaceState.Success,
            -> Unit
        }
    }

    LaunchedEffect(state) {
        if (state != null) {
            lastVisibleState = state
        }
    }

    val displayedState = state ?: lastVisibleState
    CompositionLocalProvider(LocalKsenaxThemeVisuals provides theme) {
        AnimatedVisibility(
            visible = state != null && !isMinimized,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 160)),
            modifier = modifier.fillMaxSize(),
        ) {
            displayedState?.let { visibleState ->
                KsenaxModelVerificationOverlay(
                    state = visibleState,
                    modelName = modelName,
                    onCancel = onCancel,
                    onMinimize = { isMinimized = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Полноэкранный пиксельный overlay проверки локальной модели.
 *
 * Пустое место вокруг карточки сворачивает presentation, не отменяя
 * verification job. Сама карточка перехватывает фоновые нажатия; единственное
 * действие внутри неё — кнопка «ПРЕРВАТЬ».
 *
 * @since 0.3
 */
@Composable
fun KsenaxModelVerificationOverlay(
    state: KsenaxModelVerificationUiState,
    modelName: String,
    onCancel: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current
    val faceState = state.faceState
    val hasFailure =
        faceState == KsenaxModelVerificationFaceState.Failure
    val isSuccessful =
        faceState == KsenaxModelVerificationFaceState.Success

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OVERLAY_DIM_BACKGROUND_COLOUR)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onMinimize,
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelOverlayCard(
            borderWidth = 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 390.dp)
                .heightIn(max = 700.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DownloadGradientText(
                    text = "ПРОВЕРКА МОДЕЛИ",
                    brush = theme.overlayMainBrush,
                    fontSize = 27.sp,
                    lineHeight = 29.sp,
                    fontFamily =
                        KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "+   local model validation   +",
                    color = theme.mutedColor,
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                KsenaxModelValidationOrb(
                    isAnimating = !hasFailure,
                    faceState = faceState,
                    modifier = Modifier.height(174.dp),
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = modelName,
                    color = OVERLAY_PRIMARY_TEXT_COLOUR,
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = state.supportingText,
                    color = if (hasFailure) {
                        OVERLAY_ERROR_COLOUR
                    } else {
                        theme.mutedColor
                    },
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(9.dp))

                KsenaxModelVerificationPipeline(
                    stages = state.stages,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                KsenaxModelVerificationCriteriaPanel(
                    stages = state.stages,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                KsenaxLocalTrustStrip(
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                KsenaxVerificationCancelButton(
                    onClick = onCancel,
                    isEnabled = !isSuccessful,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
            }
        }
    }
}
