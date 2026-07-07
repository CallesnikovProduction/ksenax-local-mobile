package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.alternativeMainGradientBrush

@Composable
internal fun SettingsExitConfirmationDialog(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(310.dp)
                .height(190.dp),
        ) {
            PixelWideFrame(
                brush = alternativeMainGradientBrush,
                backgroundColor = Color(0xFF070B13),
                modifier = Modifier.matchParentSize(),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GradientText(
                    text = "DO YOU WANNA CHANGE SETTINGS?",
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                    brush = alternativeMainGradientBrush,
                    fontFamily = KsenaxFontFamily.jersey10,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Сохранить изменения перед возвращением?",
                    color = Color(0xFFB3BAC8),
                    fontFamily = KsenaxFontFamily.minecraftFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DialogTextAction(
                        text = "Остаться",
                        color = Color(0xFF8F98A8),
                        onClick = onDismiss,
                    )
                    DialogTextAction(
                        text = "Не сохранять",
                        color = Color(0xFFFF756B),
                        onClick = onDiscard,
                    )
                    DialogTextAction(
                        text = "Сохранить",
                        color = Color(0xFF8EF7C9),
                        onClick = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        fontFamily = KsenaxFontFamily.minecraftFont,
        fontSize = 9.sp,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 5.dp),
    )
}
