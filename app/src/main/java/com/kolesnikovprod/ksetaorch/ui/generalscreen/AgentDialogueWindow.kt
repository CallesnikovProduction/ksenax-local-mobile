package com.kolesnikovprod.ksetaorch.ui.generalscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.theme.ksenaxTextGradient

/**
 * Сообщение в окне диалога локального агента.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
data class AgentDialogueMessage(
    val id: Long,
    val sender: AgentDialogueSender,
    val text: String,
)

/**
 * Сторона, от имени которой показано сообщение диалога.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
enum class AgentDialogueSender {
    USER,
    AGENT,
}

/**
 * Полноэкранное окно общения с локальным агентом.
 *
 * Компонент показывает историю сообщений, busy-состояние агента и поле ввода
 * следующей команды.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun AgentDialogueWindow(
    messages: List<AgentDialogueMessage>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF04050D),
                        Color(0xFF07091A),
                        Color(0xFF03040A),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp),
    ) {
        DialogueHeader(isBusy = isBusy)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = messages,
                key = { message -> message.id },
            ) { message ->
                DialogueMessageBubble(message = message)
            }
        }

        DialoguePromptInput(
            text = inputText,
            onTextChange = onInputTextChange,
            onSend = onSend,
            isBusy = isBusy,
        )
    }
}

/**
 * Верхняя строка окна диалога с названием агента и busy-индикатором.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun DialogueHeader(isBusy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Ksenax Open Source",
                color = Color(0xFFE9E5FF),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Делайте действия (фонарик вкл./выкл.)",
                color = Color(0xFF9BA0C2).copy(alpha = 0.74f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AnimatedVisibility(
            visible = isBusy,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF161A31))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF825BFF).copy(alpha = 0.54f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color(0xFFB488FF),
                )

                Text(
                    text = "Активна",
                    color = Color(0xFFC3A6FF),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Пузырь сообщения пользователя или агента.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun DialogueMessageBubble(message: AgentDialogueMessage) {
    val isUserMessage = message.sender == AgentDialogueSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUserMessage) 20.dp else 5.dp,
                        bottomEnd = if (isUserMessage) 5.dp else 20.dp,
                    )
                )
                .background(
                    if (isUserMessage) {
                        Color(0xFF5C3BDA).copy(alpha = 0.92f)
                    } else {
                        Color(0xFF111329).copy(alpha = 0.88f)
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isUserMessage) {
                        Color(0xFFBDAAFF).copy(alpha = 0.30f)
                    } else {
                        Color(0xFF825BFF).copy(alpha = 0.36f)
                    },
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUserMessage) 20.dp else 5.dp,
                        bottomEnd = if (isUserMessage) 5.dp else 20.dp,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = if (isUserMessage) "Вы" else "Gemma-4-E2B",
                color = if (isUserMessage) {
                    Color.White.copy(alpha = 0.72f)
                } else {
                    Color(0xFFB488FF)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message.text,
                color = Color(0xFFF4F2FF),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

/**
 * Нижнее поле ввода команды в диалоговом режиме.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun DialoguePromptInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(Color(0xFF111329).copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                color = Color(0xFF825BFF).copy(alpha = 0.58f),
                shape = RoundedCornerShape(23.dp),
            )
            .padding(start = 17.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp, max = 108.dp),
            textStyle = TextStyle(
                color = Color(0xFFE6E8FF),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
            cursorBrush = ksenaxTextGradient(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = if (isBusy) {
                                "Дождитесь выполнения..."
                            } else {
                                "Введите следующее действие..."
                            },
                            color = Color(0xFF9BA0C2).copy(alpha = 0.58f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    innerTextField()
                }
            },
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (!isBusy && text.isNotBlank()) {
                        ksenaxTextGradient()
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A2D44),
                                Color(0xFF1D2034),
                            ),
                        )
                    }
                )
                .clickable(
                    enabled = !isBusy && text.isNotBlank(),
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (!isBusy && text.isNotBlank()) {
                    Color.White
                } else {
                    Color(0xFF7B7F9F)
                },
            )
        }
    }
}
