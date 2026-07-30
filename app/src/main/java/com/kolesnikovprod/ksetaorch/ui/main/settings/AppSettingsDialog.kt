package com.kolesnikovprod.ksetaorch.ui.main.settings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily

@Composable
internal fun SettingsExitConfirmationDialog(
    theme: KsenaxThemeVisuals,
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
                brush = theme.settingsBrush,
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
                    brush = theme.settingsBrush,
                    fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Сохранить изменения перед возвращением?",
                    color = Color(0xFFB3BAC8),
                    fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
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
                        color = theme.accentColor,
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
    KsenaxPressableBox(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 5.dp),
    ) { pressed ->
        Text(
            text = text,
            color = color.whileKsenaxPressed(pressed),
            fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
            fontSize = 9.sp,
        )
    }
}
