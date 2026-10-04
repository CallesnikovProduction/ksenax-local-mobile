package com.kolesnikovprod.ksetaorch.ui.main.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_DIM_BACKGROUND_COLOUR

/**
 * Показывает тематический about-overlay OpenKsenax.
 *
 * Окно не содержит интерактивных элементов: повторное нажатие в любой точке
 * экрана закрывает пасхалку. Палитра рамок, декора, заголовка и иконок
 * разрешается из активной [theme].
 *
 * @since 0.3
 */
@Composable
fun KsenaxProductInfoOverlay(
    theme: KsenaxThemeVisuals,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    currentVersionOfApplication: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 210)),
        exit = fadeOut(animationSpec = tween(durationMillis = 170)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OVERLAY_DIM_BACKGROUND_COLOUR)
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = onDismiss,
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            KsenaxAboutCard(
                theme = theme,
                versionLabel = "v.$currentVersionOfApplication",
            )
        }
    }
}
