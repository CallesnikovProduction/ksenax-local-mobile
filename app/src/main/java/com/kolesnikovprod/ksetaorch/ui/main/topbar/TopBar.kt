package com.kolesnikovprod.ksetaorch.ui.main.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode

@Composable
fun PixelTopBar(
    isSidePanelOpen: Boolean = false,
    selectedMode: ChatMode?,
    activeChatMode: ChatMode?,
    activeChatTitle: String? = null,
    onModeSelected: (ChatMode) -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val menuRotation by animateFloatAsState(
        targetValue = if (isSidePanelOpen) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "top_menu_rotation",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 16.dp, bottom = 10.dp),
    ) {
        PixelMenuButton(
            rotation = menuRotation,
            onClick = onMenuClick,
            modifier = Modifier.align(Alignment.TopStart),
        )

        if (activeChatMode == null) {
            PixelChatModeSelect(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        AnimatedVisibility(
            visible = activeChatMode != null && !isSidePanelOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 150),
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = 110),
                    ),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (activeChatMode != null) {
                PixelChatModeBadge(
                    mode = activeChatMode,
                    chatTitle = activeChatTitle,
                )
            }
        }
    }
}
