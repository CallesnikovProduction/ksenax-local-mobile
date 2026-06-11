package com.kolesnikovprod.ksetaorch.ui.generalscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Затемненный блокирующий overlay для проверки локальной модели.
 *
 * Компонент перехватывает pointer events, чтобы пользователь не мог нажимать
 * нижележащие элементы, пока приложение сверяет размер и SHA256 модели.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun ModelIndexingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.76f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { pointerInputChange ->
                            pointerInputChange.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KsenaxIndexingLogo()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Сверка данных установленной модели",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Проверяю размер и SHA256...",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
