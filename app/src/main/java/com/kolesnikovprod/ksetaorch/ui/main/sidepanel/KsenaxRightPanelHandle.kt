package com.kolesnikovprod.ksetaorch.ui.main.sidepanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import kotlin.math.abs

/**
 * Темозависимый хлястик правой панели аддонов.
 *
 * Хлястик движется вместе с левым краем правой панели. Тап не открывает экран:
 * раскрытие начинается только после горизонтального drag непосредственно по
 * этой области.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxRightPanelHandle(
    brush: Brush,
    revealProgress: Float,
    screenWidthPx: Float,
    gestureEnabled: Boolean,
    onClick: () -> Unit,
    onDragStarted: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHandleDragging by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .width(48.dp)
            .height(88.dp)
            .graphicsLayer {
                translationX = -screenWidthPx * revealProgress
                alpha = if (isHandleDragging) {
                    1f
                } else {
                    (
                        1f - revealProgress / HANDLE_DOCK_FADE_PROGRESS
                    ).coerceIn(0f, 1f)
                }
            }
            .dragOrTapRightPanelHandle(
                enabled = gestureEnabled,
                onTap = {
                    isHandleDragging = false
                    onClick()
                },
                onDragStarted = {
                    isHandleDragging = true
                    onDragStarted()
                },
                onDragDelta = onDragDelta,
                onDragFinished = {
                    isHandleDragging = false
                    onDragFinished()
                },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(68.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val pixel = 2.dp.toPx()
                val left = pixel * 4f
                val right = size.width

                drawRect(
                    color = Color(0xF2050810),
                    topLeft = Offset(left, pixel * 2f),
                    size = Size(
                        width = right - left,
                        height = size.height - pixel * 4f,
                    ),
                )
                drawRect(
                    color = Color(0xF2050810),
                    topLeft = Offset(left - pixel * 2f, pixel * 4f),
                    size = Size(
                        width = right - left + pixel * 2f,
                        height = size.height - pixel * 8f,
                    ),
                )
                drawRect(
                    color = Color(0xF2050810),
                    topLeft = Offset(0f, pixel * 6f),
                    size = Size(
                        width = right,
                        height = size.height - pixel * 12f,
                    ),
                )

                drawRect(
                    brush = brush,
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, pixel),
                )
                drawRect(
                    brush = brush,
                    topLeft = Offset(left, size.height - pixel),
                    size = Size(right - left, pixel),
                )
                drawRect(
                    brush = brush,
                    topLeft = Offset(left - pixel * 2f, pixel * 2f),
                    size = Size(pixel * 2f, pixel),
                )
                drawRect(
                    brush = brush,
                    topLeft = Offset(
                        left - pixel * 2f,
                        size.height - pixel * 3f,
                    ),
                    size = Size(pixel * 2f, pixel),
                )
                drawRect(
                    brush = brush,
                    topLeft = Offset(0f, pixel * 4f),
                    size = Size(pixel * 2f, pixel),
                )
                drawRect(
                    brush = brush,
                    topLeft = Offset(0f, size.height - pixel * 5f),
                    size = Size(pixel * 2f, pixel),
                )
                drawRect(
                    brush = brush,
                    topLeft = Offset(0f, pixel * 5f),
                    size = Size(pixel, size.height - pixel * 10f),
                )
            }

            GradientIcon(
                drawableId = R.drawable.sidepanel_right_label,
                contentDescription = "Открыть панель аддонов",
                brush = brush,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private const val HANDLE_DOCK_FADE_PROGRESS = 0.24f

private fun Modifier.dragOrTapRightPanelHandle(
    enabled: Boolean,
    onTap: () -> Unit,
    onDragStarted: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragFinished: () -> Unit,
): Modifier {
    if (!enabled) return this

    return semantics {
        role = Role.Button
        onClick {
            onTap()
            true
        }
    }.pointerInput(enabled) {
        val dragActivationDistance = 1.dp.toPx()
        val tapFallbackDistance = 8.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var previousX = down.position.x
            var totalDeltaX = 0f
            var maxAbsDeltaX = 0f
            var isDragging = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { pointer ->
                    pointer.id == down.id
                } ?: break

                if (!change.pressed) {
                    if (isDragging && maxAbsDeltaX >= tapFallbackDistance) {
                        onDragFinished()
                    } else {
                        onTap()
                    }
                    break
                }

                val deltaX = change.position.x - previousX
                previousX = change.position.x
                totalDeltaX += deltaX
                maxAbsDeltaX = maxOf(maxAbsDeltaX, abs(totalDeltaX))

                if (!isDragging) {
                    if (abs(totalDeltaX) >= dragActivationDistance) {
                        isDragging = true
                        onDragStarted()
                        onDragDelta(totalDeltaX)
                        change.consume()
                    }
                } else if (deltaX != 0f) {
                    onDragDelta(deltaX)
                    change.consume()
                }
            }
        }
    }
}
