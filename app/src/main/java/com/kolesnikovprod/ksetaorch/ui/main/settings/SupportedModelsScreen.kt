package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily

@Composable
fun SupportedModelsScreen(
    theme: KsenaxThemeVisuals,
    onBackClick: () -> Unit,
    selectedModel: KsenaxSupportedTextModel?,
    isGemmaInstalled: Boolean,
    isFunctionGemmaInstalled: Boolean,
    onModelClick: (KsenaxSupportedTextModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050710)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            SupportedModelsTopBar(
                theme = theme,
                onBackClick = onBackClick,
            )

            Spacer(modifier = Modifier.height(14.dp))

            SupportedModelOption(
                theme = theme,
                model = KsenaxSupportedTextModel.Gemma,
                isSelected = selectedModel == KsenaxSupportedTextModel.Gemma,
                isInstalled = isGemmaInstalled,
                onClick = { onModelClick(KsenaxSupportedTextModel.Gemma) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            SupportedModelOption(
                theme = theme,
                model = KsenaxSupportedTextModel.FunctionGemma,
                isSelected =
                    selectedModel == KsenaxSupportedTextModel.FunctionGemma,
                isInstalled = isFunctionGemmaInstalled,
                onClick = {
                    onModelClick(KsenaxSupportedTextModel.FunctionGemma)
                },
            )
        }
    }
}

@Composable
private fun SupportedModelsTopBar(
    theme: KsenaxThemeVisuals,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
    ) {
        PixelBackButton(
            brush = theme.controlsBrush,
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        GradientText(
            text = "Choose Response-model",
            fontSize = 14.sp,
            lineHeight = 14.sp,
            brush = theme.settingsBrush,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )

        GradientIcon(
            drawableId = R.drawable.tb_basic_mode,
            contentDescription = null,
            brush = theme.controlsBrush,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(42.dp)
                .offset(y = 1.dp),
        )
    }
}

@Composable
private fun SupportedModelOption(
    theme: KsenaxThemeVisuals,
    model: KsenaxSupportedTextModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionBrush = if (isSelected && isInstalled) {
        theme.selectedBrush
    } else {
        theme.inactiveBrush
    }

    KsenaxPressableBox(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp),
    ) { pressed ->
        val pressedBrush = optionBrush.whileKsenaxPressed(pressed)

        PixelTransparentButtonFrame(
            brush = pressedBrush,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                GradientText(
                    text = model.title,
                    fontSize = 14.sp,
                    lineHeight = 15.sp,
                    brush = pressedBrush,
                    fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = model.description,
                    color = theme.mutedColor.whileKsenaxPressed(pressed),
                    fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }

            if (!isInstalled) {
                Spacer(modifier = Modifier.width(14.dp))
                PixelDownloadButton(
                    brush = theme.controlsBrush,
                    onClick = onClick,
                )
            }
        }
    }
}
