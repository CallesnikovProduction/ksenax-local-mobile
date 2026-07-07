package com.kolesnikovprod.ksetaorch.ui.main.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelToggleIcon
import com.kolesnikovprod.ksetaorch.ui.components.pixelToggleStateBrush
import com.kolesnikovprod.ksetaorch.ui.main.background.KsenaxMainBackground
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.alternativeMainGradientBrush
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush

@Composable
fun AppSettingsScreen(
    state: KsenaxSettingsUiState,
    onBackRequested: () -> Unit,
    onSaveClick: () -> Unit,
    onVoiceModelPickerClick: () -> Unit,
    onResponseModelPickerClick: () -> Unit,
    onContextWindowSelected: (KsenaxContextWindow) -> Unit,
    onPermissionsClick: () -> Unit,
    onLaunchAnimationChanged: (Boolean) -> Unit,
    onDismissExitConfirmation: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onSaveAndExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val shadowRevealDistancePx = with(LocalDensity.current) {
        36.dp.toPx()
    }
    val topShadowStrength = (
        scrollState.value / shadowRevealDistancePx
    ).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        KsenaxMainBackground(
            showScenicOverlay = false,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            AppSettingsTopBar(
                hasUnsavedChanges = state.hasUnsavedChanges,
                onBackClick = onBackRequested,
                onSaveClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            )

            Box(
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    GradientIcon(
                        drawableId = R.drawable.soft_ic_settings,
                        contentDescription = null,
                        brush = sunsetBottomBarGradientBrush,
                        modifier = Modifier
                            .size(80.dp)
                            .offset(y = (-16).dp),
                    )

                    Spacer(modifier = Modifier.height(1.dp))

                    GradientText(
                        text = "APP SETTINGS",
                        fontSize = 40.sp,
                        lineHeight = 35.sp,
                        brush = alternativeMainGradientBrush,
                        fontFamily = KsenaxFontFamily.jersey10,
                        modifier = Modifier.offset(y = (-4).dp),
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Основные параметры",
                        color = Color(0xFF817A9A),
                        fontFamily = KsenaxFontFamily.minecraftFont,
                        fontSize = 11.sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (state.hasUnsavedChanges) {
                            "STATUS: CHANGED"
                        } else {
                            "STATUS: SAVED"
                        },
                        color = if (state.hasUnsavedChanges) {
                            Color(0xFFFFD166)
                        } else {
                            Color(0xFF8EF7C9)
                        },
                        fontFamily = KsenaxFontFamily.minecraftFont,
                        fontSize = 9.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSectionFrame(
                        iconRes = R.drawable.ic_model_settings,
                        iconSize = 50.dp,
                        title = "MODELS",
                        iconModifier = Modifier.offset(x = (-9).dp),
                        titleOffsetX = (-19).dp,
                    ) {
                        SettingsValueRow(
                            iconRes = R.drawable.ic_model_voice,
                            label = "Войс-модель",
                            labelFontSize = 11.sp,
                        ) {
                            SettingsPickerButton(
                                value = state.draftSnapshot.transcribingModel
                                    ?.settingsLabel
                                    ?: "Не выбрано",
                                onChooseClick = onVoiceModelPickerClick,
                            )
                        }

                        SettingsSectionDivider()

                        SettingsValueRow(
                            iconRes = R.drawable.ic_model_text,
                            label = "Текстовая модель",
                            labelFontSize = 11.sp,
                        ) {
                            SettingsPickerButton(
                                value = state.draftSnapshot.responseModel
                                    ?.title
                                    ?: "Не выбрано",
                                onChooseClick = onResponseModelPickerClick,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSectionFrame(
                        iconRes = R.drawable.ic_behaviour_settings,
                        iconSize = 29.dp,
                        title = "BEHAVIOUR",
                    ) {
                        SettingsValueRow(
                            iconRes = R.drawable.ic_bh_context,
                            label = "Контекстное окно (токены)",
                            labelFontSize = 9.sp,
                        ) {
                            ContextWindowPicker(
                                selected = state.draftSnapshot.contextWindow,
                                onSelected = onContextWindowSelected,
                            )
                        }

                        SettingsSectionDivider()

                        SettingsValueRow(
                            iconRes = R.drawable.soft_ic_android_permissions,
                            label = "Android\nразрешения",
                            labelFontSize = 11.sp,
                        ) {
                            SettingsActionButton(
                                text = "Развернуть",
                                onClick = onPermissionsClick,
                            )
                        }

                        SettingsSectionDivider()

                        SettingsValueRow(
                            iconRes = R.drawable.ic_bh_animation,
                            label = "Стартовая анимация",
                            labelFontSize = 11.sp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        },
                                        indication = null,
                                        onClick = {
                                            onLaunchAnimationChanged(
                                                !state.draftSnapshot
                                                    .launchAnimationEnabled,
                                            )
                                        },
                                    )
                                    .padding(
                                        horizontal = 5.dp,
                                        vertical = 7.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PixelToggleIcon(
                                    isEnabled = state.draftSnapshot
                                        .launchAnimationEnabled,
                                    contentDescription = "launch animation",
                                    modifier = Modifier.size(
                                        width = 58.dp,
                                        height = 28.dp,
                                    ),
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                GradientText(
                                    text = if (
                                        state.draftSnapshot
                                            .launchAnimationEnabled
                                    ) {
                                        "ON"
                                    } else {
                                        "OFF"
                                    },
                                    brush = pixelToggleStateBrush(
                                        state.draftSnapshot
                                            .launchAnimationEnabled,
                                    ),
                                    fontFamily = KsenaxFontFamily.minecraftFont,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(18.dp),
                    )
                }

                SettingsTopScrollShadow(
                    strength = topShadowStrength,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(42.dp),
                )
            }
        }

        if (state.isExitConfirmationVisible) {
            SettingsExitConfirmationDialog(
                onDismiss = onDismissExitConfirmation,
                onDiscard = onDiscardAndExit,
                onSave = onSaveAndExit,
            )
        }
    }
}
