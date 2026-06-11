package com.kolesnikovprod.ksetaorch.ui.generalscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.ui.theme.ksenaxTextGradient

/**
 * Нижний prompt-композер главного экрана.
 *
 * Компонент переключается между режимом ввода команды для установленной модели и
 * режимом кнопки загрузки модели, если локальный артефакт еще не готов.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun AgentPromptComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDownloadModel: () -> Unit = {},
    isDownloaded: Boolean = false,
    isSending: Boolean = false,
    isDownloading: Boolean = false,
    isDownloadInterrupted: Boolean = false,
    isDownloadCancelled: Boolean = false,
    downloadProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (isDownloaded) OnDownloaded(modifier, text, onTextChange, onSend, isSending)
    else OnNonDownloaded(
        modifier = modifier,
        onDownloadModel = onDownloadModel,
        isDownloading = isDownloading,
        isDownloadInterrupted = isDownloadInterrupted,
        isDownloadCancelled = isDownloadCancelled,
        downloadProgress = downloadProgress,
    )
}

/**
 * Состояние prompt-композера после успешной установки модели.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun OnDownloaded(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,

) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF111329).copy(alpha = 0.76f))
                .border(
                    width = 1.dp,
                    color = Color(0xFF825BFF).copy(alpha = 0.70f),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp),
                textStyle = TextStyle(
                    color = Color(0xFFE6E8FF),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                cursorBrush = ksenaxTextGradient(),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Опишите задачу для агента...",
                                color = Color(0xFF9BA0C2).copy(alpha = 0.68f),
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                            )
                        }

                        innerTextField()
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = Color(0xFFA66CFF),
                    )

                    Text(
                        text = "Agentic mode",
                        color = Color(0xFFB488FF),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }

                Text(
                    text = "${text.length}/500",
                    color = Color(0xFFB4B7D4).copy(alpha = 0.86f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(ksenaxTextGradient())
                .clickable(
                    enabled = !isSending && text.isNotBlank(),
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp),
                    tint = Color.White,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = if (isSending) {
                        "Маршрутизирую..."
                    } else {
                        "Отправить (модель активна)"
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Состояние prompt-композера до установки модели.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun OnNonDownloaded(
    modifier: Modifier = Modifier,
    onDownloadModel: () -> Unit = {},
    isDownloading: Boolean = false,
    isDownloadInterrupted: Boolean = false,
    isDownloadCancelled: Boolean = false,
    downloadProgress: Float = 0f,
) {
    Column(
        modifier = modifier.offset(y = -(50.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DownloadModelButton(
            onClick = onDownloadModel,
            isDownloading = isDownloading,
            isDownloadInterrupted = isDownloadInterrupted,
            isDownloadCancelled = isDownloadCancelled,
            downloadProgress = downloadProgress,
        )
    }
}

/**
 * Рисует прогресс загрузки по контуру кнопки скачивания.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun DownloadButtonContourProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val inset = strokeWidth / 2f
        val rect = Rect(
            offset = Offset(inset, inset),
            size = Size(
                width = size.width - strokeWidth,
                height = size.height - strokeWidth,
            ),
        )
        val radius = rect.height / 2f
        val centerX = rect.left + rect.width / 2f
        val rightArcBounds = Rect(
            offset = Offset(rect.right - radius * 2f, rect.top),
            size = Size(radius * 2f, rect.height),
        )
        val leftArcBounds = Rect(
            offset = Offset(rect.left, rect.top),
            size = Size(radius * 2f, rect.height),
        )
        val contourPath = Path().apply {
            moveTo(centerX, rect.top)
            lineTo(rect.right - radius, rect.top)
            arcTo(
                rect = rightArcBounds,
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(rect.left + radius, rect.bottom)
            arcTo(
                rect = leftArcBounds,
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(centerX, rect.top)
            close()
        }
        val progressPath = Path()
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(contourPath, forceClosed = true)
        pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = pathMeasure.length * progress.coerceIn(0f, 1f),
            destination = progressPath,
            startWithMoveTo = true,
        )

        drawPath(
            path = contourPath,
            color = color.copy(alpha = 0.18f),
            style = Stroke(width = strokeWidth),
        )

        drawPath(
            path = progressPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/**
 * Кнопка загрузки модели Gemma с состояниями прогресса, ошибки и отмены.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun DownloadModelButton(
    onClick: () -> Unit,
    isDownloading: Boolean = false,
    isDownloadInterrupted: Boolean = false,
    isDownloadCancelled: Boolean = false,
    downloadProgress: Float = 0f,
) {
    val isDownloadState = isDownloading || isDownloadInterrupted || isDownloadCancelled
    val contourColor = when {
        isDownloadCancelled -> Color(0xFFFF1744)
        isDownloadInterrupted -> Color(0xFFFFD54A)
        else -> Color(0xFF39FF14)
    }
    val contourProgress = if (isDownloadInterrupted && downloadProgress == 0f) {
        1f
    } else {
        downloadProgress
    }

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(70.dp)
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF1F172E).copy(alpha = 0.86f))
                .border(
                    width = 1.dp,
                    color = Color(0xFF825BFF).copy(alpha = 0.64f),
                    shape = RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDownloadState) {
                Text(
                    text = "${(downloadProgress * 100).toInt()}%",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = contourColor,
                    maxLines = 1,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFFA66CFF),
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color(0xFF825BFF).copy(alpha = 0.45f)),
            )

            Text(
                text = when {
                    isDownloadCancelled -> "Отменено"
                    isDownloadState -> "Закачка..."
                    else -> "Gemma-4"
                },
                color = if (isDownloadState) contourColor else Color(0xFFC3A6FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        if (isDownloadState) {
            DownloadButtonContourProgress(
                progress = contourProgress,
                color = contourColor,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
