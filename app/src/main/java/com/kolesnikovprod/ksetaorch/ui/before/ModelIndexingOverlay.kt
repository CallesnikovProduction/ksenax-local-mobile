package com.kolesnikovprod.ksetaorch.ui.before

import android.graphics.drawable.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Затемненный моадльный блокирующий оверлей для проверки локальной модели.
 *
 * Компонент перехватывает все события и кнопки, чтобы пользователь не мог нажимать
 * нижележащие элементы, пока приложение сверяет размер и SHA256 модели.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun ModelIndexingOverlay(
    modifier: Modifier = Modifier,
    hasGemmaCandidateFile: OverlayState,
    isValidGemmaFile: OverlayState
) {
    // Так как Modifier приходит как matchParentSize(), то коробка перекрывает downstream-UI.
    Box(
        modifier = modifier
            .background(Color.Black)

            // Обработчик касаний, создающийся один раз, пока overlay жив.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        // Любое нажатие съедается
                        event.changes.forEach { pointerInputChange ->
                            pointerInputChange.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Переливающийся текст "Ksenax"
            KsenaxIndexingLogo()

            // Пробел
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Row {
                    OverlayParagraphVisual(hasGemmaCandidateFile)

                    // Пробел
                    Spacer(modifier = Modifier.width(6.dp))

                    // Поясняющий текст, чё происходит
                    Text(
                        text = "Поиск файла установленной модели",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 10.sp
                    )
                }


                // Пробел
                Spacer(modifier = Modifier.height(6.dp))

                Row {
                    OverlayParagraphVisual(isValidGemmaFile)

                    // Пробел
                    Spacer(modifier = Modifier.width(6.dp))

                    // Поясняющий текст
                    Text(
                        text = "Проверка размера и хэша файла модели",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Простая функция отрисовки иконки ОДНОГО состояния
 *
 * @since 0.2
 * @author Stepan Kolesnikov
 */
@Composable
private fun OverlayParagraphVisual(state: OverlayState) {
    when (state.paragraph) {
        OverlayLoadingState.SUCCESS -> {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = state.paragraph.icon,
                    contentDescription = null,
                    tint = state.paragraph.tint
                )
            }
        }

        OverlayLoadingState.FAILURE -> {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = state.paragraph.icon,
                    contentDescription = null,
                    tint = state.paragraph.tint
                )
            }
        }

        OverlayLoadingState.NON_CONFIRMED -> {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = state.paragraph.icon,
                    contentDescription = null,
                    tint = state.paragraph.tint
                )
            }
        }

        OverlayLoadingState.LOADING -> {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}