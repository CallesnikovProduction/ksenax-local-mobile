package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.mainGradient
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush

@Composable
fun SupportedModelsScreen(
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
            SupportedModelsTopBar(onBackClick = onBackClick)

            Spacer(modifier = Modifier.height(14.dp))

            SupportedModelOption(
                model = KsenaxSupportedTextModel.Gemma,
                isSelected = selectedModel == KsenaxSupportedTextModel.Gemma,
                isInstalled = isGemmaInstalled,
                onClick = { onModelClick(KsenaxSupportedTextModel.Gemma) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            SupportedModelOption(
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
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
    ) {
        PixelBackButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        GradientText(
            text = "Choose Response-model",
            fontSize = 14.sp,
            lineHeight = 14.sp,
            brush = sunsetBottomBarGradientBrush,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )

        GradientIcon(
            drawableId = R.drawable.ic_basic_llm_chat,
            contentDescription = null,
            brush = sunsetBottomBarGradientBrush,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(42.dp)
                .offset(y = 1.dp),
        )
    }
}

@Composable
private fun SupportedModelOption(
    model: KsenaxSupportedTextModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val optionBrush = if (isSelected && isInstalled) {
        mainGradient
    } else {
        inactiveGradientBrush
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        PixelTransparentButtonFrame(
            brush = optionBrush,
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
                    brush = optionBrush,
                    fontFamily = KsenaxFontFamily.tiny5,
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = model.description,
                    color = Color(0xFF6F7C8A),
                    fontFamily = KsenaxFontFamily.tiny5,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }

            if (!isInstalled) {
                Spacer(modifier = Modifier.width(14.dp))
                PixelDownloadButton(onClick = onClick)
            }
        }
    }
}
