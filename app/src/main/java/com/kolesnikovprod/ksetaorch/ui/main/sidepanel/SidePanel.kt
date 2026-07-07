package com.kolesnikovprod.ksetaorch.ui.main.sidepanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.main.model.KsenaxChat
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.main.model.toChatPanelTitle
import kotlin.math.min

private const val SidePanelAnimationMillis = 220
private val SidePanelIconColor = Color(0xFF92889D)
private val RenameActionColor = Color(0xFF9299A6)
private val DeleteActionColor = Color(0xFFFF756B)
private val SidePanelIconBrush = Brush.linearGradient(
    listOf(
        SidePanelIconColor,
        SidePanelIconColor,
    ),
)

@Composable
fun KsenaxSidePanel(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    chats: List<KsenaxChat>,
    activeChatId: Long?,
    activeChatMode: ChatMode?,
    onChatSelected: (KsenaxChat) -> Unit,
    onRenameChat: (KsenaxChat, String) -> Unit,
    onDeleteChat: (KsenaxChat) -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    var renameTarget by remember { mutableStateOf<KsenaxChat?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = SidePanelAnimationMillis)),
            exit = fadeOut(animationSpec = tween(durationMillis = SidePanelAnimationMillis)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f))
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = SidePanelAnimationMillis),
            ) + fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = SidePanelAnimationMillis),
            ) + fadeOut(animationSpec = tween(durationMillis = 140)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            SidePanelContent(
                chats = chats,
                activeChatId = activeChatId,
                activeChatMode = activeChatMode,
                onChatSelected = onChatSelected,
                onRenameChat = { chat -> renameTarget = chat },
                onDeleteChat = onDeleteChat,
                onNewChatClick = onNewChatClick,
                onSettingsClick = onSettingsClick,
            )
        }

        renameTarget?.let { chat ->
            RenameChatDialog(
                chat = chat,
                onDismiss = { renameTarget = null },
                onRename = { newTitle ->
                    onRenameChat(chat, newTitle)
                    renameTarget = null
                },
            )
        }
    }
}

@Composable
private fun SidePanelContent(
    chats: List<KsenaxChat>,
    activeChatId: Long?,
    activeChatMode: ChatMode?,
    onChatSelected: (KsenaxChat) -> Unit,
    onRenameChat: (KsenaxChat) -> Unit,
    onDeleteChat: (KsenaxChat) -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelInteractionSource = remember { MutableInteractionSource() }
    val settingsInteractionSource = remember { MutableInteractionSource() }
    val newChatInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(236.dp)
            .fillMaxHeight()
            .clickable(
                interactionSource = panelInteractionSource,
                indication = null,
                onClick = {},
            ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = Color(0xFF050710),
                size = size,
            )
        }

        if (chats.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .padding(top = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PixelSadFace(
                    modifier = Modifier.size(58.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "У вас пока нет активных чатов",
                    color = Color(0xFF6F7C8A),
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            ChatHistoryList(
                chats = chats,
                activeChatId = activeChatId,
                activeChatMode = activeChatMode,
                onChatSelected = onChatSelected,
                onRenameChat = onRenameChat,
                onDeleteChat = onDeleteChat,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 124.dp)
                    .padding(horizontal = 14.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(start = 24.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.soft_ic_settings),
                contentDescription = "settings",
                tint = SidePanelIconColor,
                modifier = Modifier
                    .size(44.dp)
                    .offset(x = (-5).dp, y = 1.dp)
                    .clickable(
                        interactionSource = settingsInteractionSource,
                        indication = null,
                        onClick = onSettingsClick,
                    ),
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PixelNewChatButton(
                    onClick = onNewChatClick,
                    interactionSource = newChatInteractionSource,
                    modifier = Modifier.size(width = 86.dp, height = 32.dp)
                        .offset(y = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatHistoryList(
    chats: List<KsenaxChat>,
    activeChatId: Long?,
    activeChatMode: ChatMode?,
    onChatSelected: (KsenaxChat) -> Unit,
    onRenameChat: (KsenaxChat) -> Unit,
    onDeleteChat: (KsenaxChat) -> Unit,
    modifier: Modifier = Modifier,
) {
    var actionMenuChatKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        chats.forEach { chat ->
            val chatKey = "${chat.mode.name}:${chat.id}"
            ChatHistoryItem(
                chat = chat,
                isActive = chat.id == activeChatId && chat.mode == activeChatMode,
                onClick = { onChatSelected(chat) },
                isActionMenuVisible = actionMenuChatKey == chatKey,
                onActionMenuClick = {
                    actionMenuChatKey = if (actionMenuChatKey == chatKey) {
                        null
                    } else {
                        chatKey
                    }
                },
                onActionMenuDismiss = { actionMenuChatKey = null },
                onRenameClick = {
                    actionMenuChatKey = null
                    onRenameChat(chat)
                },
                onDeleteClick = {
                    actionMenuChatKey = null
                    onDeleteChat(chat)
                },
            )
        }
    }
}

@Composable
private fun ChatHistoryItem(
    chat: KsenaxChat,
    isActive: Boolean,
    onClick: () -> Unit,
    isActionMenuVisible: Boolean,
    onActionMenuClick: () -> Unit,
    onActionMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (isActive) {
            PixelWideFrame(
                brush = chat.mode.activeGradient,
                modifier = Modifier.matchParentSize(),
                backgroundColor = Color(0xF20A0E18),
            )
        }

        Text(
            text = chat.title.toChatPanelTitle(),
            color = Color.White,
            fontFamily = KsenaxFontFamily.tiny5,
            fontSize = 14.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .padding(start = 14.dp, end = 34.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = chat.mode.activeGradient,
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 38.dp, height = 40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onActionMenuClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PixelChatActionsDots(
                brush = chat.mode.activeGradient,
                modifier = Modifier.size(width = 4.dp, height = 18.dp),
            )

            ChatActionsMenu(
                isVisible = isActionMenuVisible,
                onDismiss = onActionMenuDismiss,
                onRenameClick = onRenameClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun ChatActionsMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    DropdownMenu(
        expanded = isVisible,
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Color(0xFF090D16),
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "Rename",
                    color = RenameActionColor,
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 15.sp,
                )
            },
            onClick = onRenameClick,
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = "Delete",
                    color = DeleteActionColor,
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 15.sp,
                )
            },
            onClick = onDeleteClick,
        )
    }
}

@Composable
private fun RenameChatDialog(
    chat: KsenaxChat,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by remember(chat.id, chat.mode, chat.title) {
        mutableStateOf(chat.title)
    }
    val canRename = title.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(292.dp)
                .height(164.dp),
        ) {
            PixelWideFrame(
                brush = chat.mode.activeGradient,
                backgroundColor = Color(0xFF080C14),
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 17.dp),
            ) {
                Text(
                    text = "Rename chat",
                    color = RenameActionColor,
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 16.sp,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    PixelWideFrame(
                        brush = chat.mode.activeGradient,
                        backgroundColor = Color(0xFF050710),
                        modifier = Modifier.fillMaxSize(),
                    )
                    BasicTextField(
                        value = title,
                        onValueChange = { value -> title = value.take(80) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = KsenaxFontFamily.tiny5,
                            fontSize = 15.sp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = "Cancel",
                        color = RenameActionColor,
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    )
                    Text(
                        text = "Rename",
                        color = if (canRename) Color.White else Color(0xFF505764),
                        fontFamily = KsenaxFontFamily.tiny5,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(
                            enabled = canRename,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onRename(title) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelChatActionsDots(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val dot = 2.dp.toPx()
        val x = (size.width - dot) / 2f
        val gap = (size.height - dot * 3f) / 2f

        repeat(3) { index ->
            drawRect(
                brush = brush,
                topLeft = Offset(x, index * (dot + gap)),
                size = Size(dot, dot),
            )
        }
    }
}

@Composable
private fun PixelNewChatButton(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        PixelWideFrame(
            brush = SidePanelIconBrush,
            modifier = Modifier.matchParentSize(),
            backgroundColor = Color(0xF2050710),
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val plusPixel = 3.dp.toPx()
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val arm = plusPixel * 2f

            drawRect(
                color = SidePanelIconColor,
                topLeft = Offset(centerX - plusPixel / 2f, centerY - arm - plusPixel / 2f),
                size = Size(plusPixel, arm * 2f + plusPixel),
            )
            drawRect(
                color = SidePanelIconColor,
                topLeft = Offset(centerX - arm - plusPixel / 2f, centerY - plusPixel / 2f),
                size = Size(arm * 2f + plusPixel, plusPixel),
            )
        }
    }
}

@Composable
private fun PixelSadFace(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cell = min(size.width / 13f, size.height / 11f)
        val left = (size.width - cell * 13f) / 2f
        val top = (size.height - cell * 11f) / 2f
        val shadowOffset = Offset(cell * 0.18f, cell * 0.18f)
        val shadowColor = Color(0xFF171420)
        val faceColor = Color(0xFF9B93A6)

        fun drawPixel(
            x: Int,
            y: Int,
            color: Color = faceColor,
            withShadow: Boolean = true,
        ) {
            val point = Offset(left + x * cell, top + y * cell)

            if (withShadow) {
                drawRect(
                    color = shadowColor,
                    topLeft = point + shadowOffset,
                    size = Size(cell, cell),
                )
            }

            drawRect(
                color = color,
                topLeft = point,
                size = Size(cell, cell),
            )
        }

        val leftEye = listOf(
            1 to 2, 3 to 2,
            2 to 3,
            1 to 4, 3 to 4,
        )
        val rightEye = listOf(
            9 to 2, 11 to 2,
            10 to 3,
            9 to 4, 11 to 4,
        )
        val frown = listOf(
            3 to 9,
            4 to 8, 5 to 8, 6 to 8, 7 to 8, 8 to 8,
            9 to 9,
        )

        (leftEye + rightEye).forEach { (x, y) ->
            drawPixel(x, y)
        }
        frown.forEach { (x, y) ->
            drawPixel(x, y)
        }
    }
}
