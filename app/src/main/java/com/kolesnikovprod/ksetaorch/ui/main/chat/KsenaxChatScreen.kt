package com.kolesnikovprod.ksetaorch.ui.main.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxMessage
import com.kolesnikovprod.ksetaorch.ui.main.chat.formatting.KsenaxBasicMessageContent
import com.kolesnikovprod.ksetaorch.ui.main.chat.formatting.containsCodeFence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ChatAutoScrollDelayMillis = 16L
private const val BasicMessageAppearMillis = 260
private const val ThinkingIndicatorMotionMillis = 240
private const val ThinkingShimmerPauseMillis = 3_200L
private const val ThinkingShimmerSweepMillis = 760
private val ChatDefaultBottomBarClearance = 116.dp
private val ChatKeyboardBottomBarClearance = 84.dp
private val ChatTailClearance = 36.dp

@Composable
fun KsenaxChatScreen(
    chat: KsenaxChat,
    showThinkingIndicator: Boolean = false,
    bottomBarHeight: Dp = 152.dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var shouldFollowStreamingTail by remember(chat.id) {
        mutableStateOf(true)
    }
    val density = LocalDensity.current
    val imeBottomPadding = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val measuredBottomBarClearance =
        (bottomBarHeight - ChatTailClearance).coerceAtLeast(0.dp)
    val bottomBarClearance = if (bottomBarHeight > 0.dp) {
        measuredBottomBarClearance
    } else if (imeBottomPadding > 0.dp) {
        ChatKeyboardBottomBarClearance
    } else {
        ChatDefaultBottomBarClearance
    }
    val tailItemIndex = chat.messages.size + 1
    val isTailVisible by remember(chat.id, listState) {
        derivedStateOf {
            val tailIndex = listState.layoutInfo.totalItemsCount - 1
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            tailIndex >= 0 && lastVisibleIndex == tailIndex
        }
    }
    val showScrollToBottomButton =
        !shouldFollowStreamingTail && !isTailVisible

    LaunchedEffect(chat.id) {
        if (chat.messages.isNotEmpty()) {
            listState.scrollToItem(tailItemIndex)
        }
    }

    LaunchedEffect(chat.id, listState) {
        snapshotFlow {
            listState.isScrollInProgress to listState.lastScrolledBackward
        }.collect { (isScrolling, isScrollingBackward) ->
            if (isScrolling && isScrollingBackward) {
                shouldFollowStreamingTail = false
            } else if (!isScrolling) {
                val tailIndex = listState.layoutInfo.totalItemsCount - 1
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                if (lastVisibleIndex == tailIndex) {
                    shouldFollowStreamingTail = true
                }
            }
        }
    }

    LaunchedEffect(
        chat.id,
        chat.messages.size,
        chat.messages.lastOrNull()?.text?.length,
        chat.messages.lastOrNull()?.generationDurationMillis,
        showThinkingIndicator,
    ) {
        if (chat.messages.isNotEmpty() && shouldFollowStreamingTail) {
            delay(ChatAutoScrollDelayMillis)
            listState.scrollToItem(tailItemIndex)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 12.dp,
                end = 18.dp,
                bottom = bottomBarClearance + imeBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = chat.messages,
                key = { index, _ -> "${chat.id}-$index" },
            ) { _, message ->
                ChatMessageItem(
                    message = message,
                    mode = chat.mode,
                    brush = chat.mode.activeGradient,
                )
            }

            item(key = "${chat.id}-thinking") {
                AnimatedVisibility(
                    visible = showThinkingIndicator,
                    enter = slideInVertically(
                        initialOffsetY = { height -> -height },
                        animationSpec = tween(durationMillis = ThinkingIndicatorMotionMillis),
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = ThinkingIndicatorMotionMillis),
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { height -> -height },
                        animationSpec = tween(durationMillis = ThinkingIndicatorMotionMillis),
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = ThinkingIndicatorMotionMillis),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ThinkingIndicator(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }

            item(key = "${chat.id}-tail") {
                Spacer(modifier = Modifier.height(ChatTailClearance))
            }
        }

        ChatTopFade(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(58.dp)
                .offset(y = (-3).dp),
        )

        ChatBottomFade(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp),
        )

        AnimatedVisibility(
            visible = showScrollToBottomButton,
            enter = fadeIn(tween(140)) + slideInVertically(
                initialOffsetY = { height -> height / 2 },
                animationSpec = tween(180),
            ),
            exit = fadeOut(tween(110)) + slideOutVertically(
                targetOffsetY = { height -> height / 2 },
                animationSpec = tween(140),
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 22.dp,
                    bottom = bottomBarClearance + imeBottomPadding + 10.dp,
                ),
        ) {
            ChatScrollToBottomButton(
                brush = chat.mode.activeGradient,
                onClick = {
                    shouldFollowStreamingTail = true
                    coroutineScope.launch {
                        listState.animateScrollToItem(tailItemIndex)
                    }
                },
            )
        }
    }
}

@Composable
private fun ThinkingIndicator(
    modifier: Modifier = Modifier,
) {
    val shimmerProgress = remember { Animatable(-0.5f) }
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(420L)
            dotCount = dotCount % 3 + 1
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            shimmerProgress.snapTo(-0.5f)
            delay(ThinkingShimmerPauseMillis)
            shimmerProgress.animateTo(
                targetValue = 1.5f,
                animationSpec = tween(
                    durationMillis = ThinkingShimmerSweepMillis,
                    easing = LinearEasing,
                ),
            )
        }
    }

    val shimmerPosition = shimmerProgress.value
    Text(
        text = "Немного думаю${".".repeat(dotCount)}",
        color = Color.White,
        fontFamily = KsenaxFontFamily.epilepsySansForBasicFont,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val shimmerCenterX = shimmerPosition * size.width
                val shimmerHalfWidth = size.width * 0.18f
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.78f),
                        Color.Transparent,
                    ),
                    start = Offset(
                        x = shimmerCenterX - shimmerHalfWidth,
                        y = 0f,
                    ),
                    end = Offset(
                        x = shimmerCenterX + shimmerHalfWidth,
                        y = size.height,
                    ),
                )

                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = sunsetBottomBarGradientBrush,
                        blendMode = BlendMode.SrcAtop,
                    )
                    if (shimmerPosition in 0f..1f) {
                        drawRect(
                            brush = shimmerBrush,
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                }
            },
    )
}

@Composable
private fun ChatScrollToBottomButton(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(38.dp)
            .semantics {
                contentDescription = "Перейти к последнему сообщению"
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val pixel = 2.dp.toPx()

        drawRect(
            color = Color(0xEE03070D),
            topLeft = Offset(pixel * 2f, 0f),
            size = Size(size.width - pixel * 4f, size.height),
        )
        drawRect(
            color = Color(0xEE03070D),
            topLeft = Offset(0f, pixel * 2f),
            size = Size(size.width, size.height - pixel * 4f),
        )

        drawRect(brush, Offset(pixel * 2f, 0f), Size(size.width - pixel * 4f, pixel))
        drawRect(
            brush,
            Offset(pixel * 2f, size.height - pixel),
            Size(size.width - pixel * 4f, pixel),
        )
        drawRect(brush, Offset(0f, pixel * 2f), Size(pixel, size.height - pixel * 4f))
        drawRect(
            brush,
            Offset(size.width - pixel, pixel * 2f),
            Size(pixel, size.height - pixel * 4f),
        )

        val arrowPixels = listOf(
            3 to 2,
            3 to 3,
            3 to 4,
            0 to 4,
            1 to 5,
            2 to 6,
            3 to 7,
            4 to 6,
            5 to 5,
            6 to 4,
        )
        val arrowPixel = 3.dp.toPx()
        val arrowLeft = (size.width - arrowPixel * 7f) / 2f
        val arrowTop = (size.height - arrowPixel * 8f) / 2f
        arrowPixels.forEach { (x, y) ->
            drawRect(
                brush = brush,
                topLeft = Offset(
                    x = arrowLeft + x * arrowPixel,
                    y = arrowTop + y * arrowPixel,
                ),
                size = Size(arrowPixel, arrowPixel),
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: KsenaxMessage,
    mode: ChatMode,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    val horizontalAlignment = if (message.isUser) {
        Alignment.End
    } else {
        Alignment.Start
    }
    val usesBasicPresentation = mode.usesBasicChatPresentation
    val shouldAnimateBasicAssistant = usesBasicPresentation && !message.isUser
    val canCopyMessage = message.isUser || usesBasicPresentation
    var isBasicMessageVisible by remember { mutableStateOf(!shouldAnimateBasicAssistant) }
    val basicAppearProgress by animateFloatAsState(
        targetValue = if (isBasicMessageVisible) 1f else 0f,
        animationSpec = tween(durationMillis = BasicMessageAppearMillis),
        label = "basic_message_appear_progress",
    )

    LaunchedEffect(shouldAnimateBasicAssistant) {
        if (shouldAnimateBasicAssistant) {
            isBasicMessageVisible = true
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        KsenaxMessageBubble(
            message = message,
            mode = mode,
            brush = brush,
            modifier = Modifier
                .align(horizontalAlignment)
                .copyMessageOnLongPress(
                    text = message.text,
                    enabled = canCopyMessage,
                )
                .graphicsLayer {
                    if (shouldAnimateBasicAssistant) {
                        alpha = basicAppearProgress
                        translationY = (1f - basicAppearProgress) * 18f
                    }
                },
        )

        if (mode == ChatMode.Agentic && message.isUser) {
            AgenticActionSeparator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
        }

        if (
            usesBasicPresentation &&
            !message.isUser &&
            !message.isStreaming &&
            message.generationDurationMillis != null
        ) {
            Text(
                text = message.generationDurationMillis.toGenerationTimeLabel(),
                color = Color(0xFF6F7785),
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 8.dp, top = 4.dp),
            )
        }
    }
}

private fun Long.toGenerationTimeLabel(): String {
    val seconds = this / 1_000.0
    return if (seconds < 10.0) {
        String.format(java.util.Locale.US, "%.1f с", seconds)
    } else {
        "${seconds.toLong()} с"
    }
}

@Composable
private fun Modifier.copyMessageOnLongPress(
    text: String,
    enabled: Boolean,
): Modifier {
    if (!enabled) {
        return this
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    return pointerInput(text) {
        detectTapGestures(
            onLongPress = {
                clipboardManager.setText(AnnotatedString(text))
                Toast.makeText(
                    context,
                    "Сообщение скопировано",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}

@Composable
private fun KsenaxMessageBubble(
    message: KsenaxMessage,
    mode: ChatMode,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    if (!message.isUser && mode == ChatMode.Agentic) {
        AgenticPipelineMessage(
            text = message.text,
            isComplete = message.isFinalAgenticStep,
            brush = brush,
            modifier = modifier,
        )
        return
    }

    val textColor = when {
        message.isUser && mode == ChatMode.Temporaric -> Color(0xFF2A1B08)
        else                                          -> Color(0xFFF4F8FF)
    }
    val isBasicAssistant = !message.isUser && mode.usesBasicChatPresentation
    val hasCodeBlock = isBasicAssistant && message.text.containsCodeFence()

    Box(
        modifier = modifier
            .then(
                if (hasCodeBlock) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.widthIn(max = 280.dp)
                },
            )
            .padding(vertical = 1.dp),
    ) {
        if (message.isUser) {
            FilledPixelMessageBackground(
                brush = brush,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            ThinPixelMessageFrame(
                brush = brush,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (isBasicAssistant) {
            KsenaxBasicMessageContent(
                rawText = message.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            )
        } else {
            Text(
                text = message.text,
                color = textColor,
                fontFamily = if (
                    message.isUser && mode.usesBasicChatPresentation
                ) {
                    KsenaxFontFamily.epilepsySansForBasicFont
                } else {
                    KsenaxFontFamily.tiny5
                },
                fontSize = 15.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            )
        }
    }
}

private val ChatMode.usesBasicChatPresentation: Boolean
    get() = this == ChatMode.Basic || this == ChatMode.Temporaric

@Composable
private fun ChatTopFade(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black.copy(alpha = 0.90f),
                    0.48f to Color.Black.copy(alpha = 0.42f),
                    1.00f to Color.Transparent,
                ),
                startY = 0f,
                endY = size.height,
            ),
            size = size,
        )
    }
}

@Composable
private fun ChatBottomFade(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.52f to Color.Black.copy(alpha = 0.46f),
                    1.00f to Color.Black.copy(alpha = 0.92f),
                ),
                startY = 0f,
                endY = size.height,
            ),
            size = size,
        )
    }
}

@Composable
private fun AgenticPipelineMessage(
    text: String,
    isComplete: Boolean,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 292.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgenticPipelineMarker(
            isComplete = isComplete,
            brush = brush,
            modifier = Modifier.size(13.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            color = Color.White,
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier
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
}

@Composable
private fun AgenticPipelineMarker(
    isComplete: Boolean,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 2.dp.toPx()

        if (!isComplete) {
            val dot = 5.dp.toPx()
            drawRect(
                brush = brush,
                topLeft = Offset(
                    x = (size.width - dot) / 2f,
                    y = (size.height - dot) / 2f,
                ),
                size = Size(dot, dot),
            )
            return@Canvas
        }

        val checkPixels = listOf(
            0 to 3,
            1 to 4,
            2 to 5,
            3 to 4,
            4 to 3,
            5 to 2,
            6 to 1,
        )
        val left = (size.width - pixel * 7f) / 2f
        val top = (size.height - pixel * 7f) / 2f

        checkPixels.forEach { (x, y) ->
            drawRect(
                brush = brush,
                topLeft = Offset(
                    x = left + x * pixel,
                    y = top + y * pixel,
                ),
                size = Size(pixel, pixel),
            )
        }
    }
}

@Composable
private fun AgenticActionSeparator(
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "agentic_action_separator_progress",
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .padding(horizontal = 48.dp),
    ) {
        val lineHeight = 2.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f - lineHeight / 2f
        val maxSegmentWidth = size.width / 2f - 6.dp.toPx()
        val segmentWidth = maxSegmentWidth * progress

        drawRect(
            brush = inactiveGradientBrush,
            topLeft = Offset(centerX - segmentWidth, centerY),
            size = Size(segmentWidth, lineHeight),
        )
        drawRect(
            brush = inactiveGradientBrush,
            topLeft = Offset(centerX, centerY),
            size = Size(segmentWidth, lineHeight),
        )

        if (progress > 0f) {
            val centerPixel = 3.dp.toPx()
            drawRect(
                brush = inactiveGradientBrush,
                topLeft = Offset(
                    x = centerX - centerPixel / 2f,
                    y = size.height / 2f - centerPixel / 2f,
                ),
                size = Size(centerPixel, centerPixel),
            )
        }
    }
}

@Composable
private fun FilledPixelMessageBackground(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 2.dp.toPx()

        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, 0f),
            size = Size(size.width - pixel * 4f, size.height),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(0f, pixel * 2f),
            size = Size(size.width, size.height - pixel * 4f),
        )
        drawRect(brush, Offset(pixel, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(pixel, size.height - pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, size.height - pixel * 2f), Size(pixel, pixel))
    }
}

@Composable
private fun ThinPixelMessageFrame(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixel = 1.dp.toPx()
        val bg = Color(0xDD03070D)

        drawRect(
            color = bg,
            topLeft = Offset(pixel * 2f, pixel),
            size = Size(size.width - pixel * 4f, size.height - pixel * 2f),
        )
        drawRect(
            color = bg,
            topLeft = Offset(pixel, pixel * 2f),
            size = Size(size.width - pixel * 2f, size.height - pixel * 4f),
        )

        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, 0f),
            size = Size(size.width - pixel * 4f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(pixel * 2f, size.height - pixel),
            size = Size(size.width - pixel * 4f, pixel),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(0f, pixel * 2f),
            size = Size(pixel, size.height - pixel * 4f),
        )
        drawRect(
            brush = brush,
            topLeft = Offset(size.width - pixel, pixel * 2f),
            size = Size(pixel, size.height - pixel * 4f),
        )

        drawRect(brush, Offset(pixel, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(pixel, size.height - pixel * 2f), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, pixel), Size(pixel, pixel))
        drawRect(brush, Offset(size.width - pixel * 2f, size.height - pixel * 2f), Size(pixel, pixel))
    }
}
