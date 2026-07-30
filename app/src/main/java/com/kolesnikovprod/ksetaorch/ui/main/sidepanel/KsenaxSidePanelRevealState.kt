package com.kolesnikovprod.ksetaorch.ui.main.sidepanel

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SidePanelSettleMillis = 180
private const val SidePanelOpenThreshold = 0.32f

/**
 * Управляет положением левой панели во время drag и после отпускания пальца.
 *
 * Состояние хранится на уровне экрана: pointer detector передаёт сюда
 * горизонтальные delta, а [KsenaxSidePanel] только отображает готовый
 * [revealProgress].
 *
 * @since 0.3
 */
@Stable
internal class KsenaxSidePanelRevealState(
    private val animationScope: CoroutineScope,
) {
    var isOpen by mutableStateOf(false)
        private set

    var revealProgress by mutableFloatStateOf(0f)
        private set

    private var wasOpenBeforeDrag = false
    private var settleJob: Job? = null

    fun onDragStarted() {
        settleJob?.cancel()
        wasOpenBeforeDrag = isOpen
    }

    fun onDragDelta(
        dragDeltaX: Float,
        panelWidthPx: Float,
    ) {
        if (panelWidthPx <= 0f) return
        revealProgress = (
            revealProgress + dragDeltaX / panelWidthPx
        ).coerceIn(0f, 1f)
    }

    fun onDragFinished() {
        settle(open = revealProgress >= SidePanelOpenThreshold)
    }

    fun onDragCancelled() {
        settle(open = wasOpenBeforeDrag)
    }

    fun open() {
        settle(open = true)
    }

    fun close() {
        settle(open = false)
    }

    fun toggle() {
        settle(open = !isOpen)
    }

    fun snapClosed() {
        settleJob?.cancel()
        revealProgress = 0f
        isOpen = false
    }

    private fun settle(open: Boolean) {
        settleJob?.cancel()
        if (open) {
            isOpen = true
        }
        settleJob = animationScope.launch {
            animate(
                initialValue = revealProgress,
                targetValue = if (open) 1f else 0f,
                animationSpec = tween(SidePanelSettleMillis),
            ) { value, _ ->
                revealProgress = value.coerceIn(0f, 1f)
            }
            if (!open) {
                isOpen = false
            }
        }
    }
}

/**
 * Создаёт привязанное к текущему экрану состояние раскрытия левой панели.
 *
 * @since 0.3
 */
@Composable
internal fun rememberKsenaxSidePanelRevealState(): KsenaxSidePanelRevealState {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        KsenaxSidePanelRevealState(animationScope = scope)
    }
}
