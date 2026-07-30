package com.kolesnikovprod.ksetaorch.ui.main.settings

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
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelToggleIcon
import com.kolesnikovprod.ksetaorch.ui.components.pixelToggleStateBrush
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.main.background.KsenaxMainBackground
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals

@Composable
fun AppSettingsScreen(
    theme: KsenaxThemeVisuals,
    state: KsenaxSettingsUiState,
    onBackRequested: () -> Unit,
    onSaveClick: () -> Unit,
    onThemeSwitcherClick: () -> Unit,
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
            theme = theme,
            showScenicOverlay = false,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            AppSettingsTopBar(
                theme = theme,
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
                        drawableId = R.drawable.sidepanel_settings,
                        contentDescription = null,
                        brush = theme.controlsBrush,
                        modifier = Modifier
                            .size(80.dp)
                            .offset(y = (-16).dp),
                    )

                    Spacer(modifier = Modifier.height(1.dp))

                    GradientText(
                        text = "APP SETTINGS",
                        fontSize = 40.sp,
                        lineHeight = 35.sp,
                        brush = theme.settingsBrush,
                        fontFamily = KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                        modifier = Modifier.offset(y = (-4).dp),
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Основные параметры",
                        color = theme.mutedColor,
                        fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
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
                        fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                        fontSize = 9.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsThemeSwitcherCard(
                        onClick = onThemeSwitcherClick,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSectionFrame(
                        theme = theme,
                        iconRes = R.drawable.settings_group_models,
                        iconSize = 50.dp,
                        title = "MODELS",
                        iconModifier = Modifier.offset(x = (-9).dp),
                        titleOffsetX = (-19).dp,
                    ) {
                        SettingsValueRow(
                            theme = theme,
                            iconRes = R.drawable.settings_group_models_voice,
                            label = "Войс-модель",
                            labelFontSize = 11.sp,
                        ) {
                            SettingsPickerButton(
                                theme = theme,
                                value = state.draftSnapshot.transcribingModel
                                    ?.settingsLabel
                                    ?: "Не выбрано",
                                onChooseClick = onVoiceModelPickerClick,
                            )
                        }

                        SettingsSectionDivider(color = theme.mutedColor)

                        SettingsValueRow(
                            theme = theme,
                            iconRes = R.drawable.settings_group_models_text,
                            label = "Текстовая модель",
                            labelFontSize = 11.sp,
                        ) {
                            SettingsPickerButton(
                                theme = theme,
                                value = state.draftSnapshot.responseModel
                                    ?.title
                                    ?: "Не выбрано",
                                onChooseClick = onResponseModelPickerClick,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSectionFrame(
                        theme = theme,
                        iconRes = R.drawable.settings_group_behaviour,
                        iconSize = 29.dp,
                        title = "BEHAVIOUR",
                    ) {
                        SettingsValueRow(
                            theme = theme,
                            iconRes = R.drawable.settings_group_behaviour_contextwindow,
                            label = "Контекстное окно (токены)",
                            labelFontSize = 9.sp,
                        ) {
                            ContextWindowPicker(
                                theme = theme,
                                selected = state.draftSnapshot.contextWindow,
                                onSelected = onContextWindowSelected,
                            )
                        }

                        SettingsSectionDivider(color = theme.mutedColor)

                        SettingsValueRow(
                            theme = theme,
                            iconRes = R.drawable.settings_group_behaviour_permissions,
                            label = "Android\nразрешения",
                            labelFontSize = 11.sp,
                        ) {
                            SettingsActionButton(
                                theme = theme,
                                text = "РАСКРЫТЬ",
                                onClick = onPermissionsClick,
                            )
                        }

                        SettingsSectionDivider(color = theme.mutedColor)

                        SettingsValueRow(
                            theme = theme,
                            iconRes = R.drawable.settings_group_behaviour_animation,
                            label = "Стартовая анимация",
                            labelFontSize = 11.sp,
                        ) {
                            KsenaxPressableBox(
                                onClick = {
                                    onLaunchAnimationChanged(
                                        !state.draftSnapshot
                                            .launchAnimationEnabled,
                                    )
                                },
                                modifier = Modifier
                                    .padding(
                                        horizontal = 5.dp,
                                        vertical = 7.dp,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { pressed ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PixelToggleIcon(
                                        isEnabled = state.draftSnapshot
                                            .launchAnimationEnabled,
                                        enabledBrush = theme.selectedBrush
                                            .whileKsenaxPressed(pressed),
                                        disabledBrush = theme.inactiveBrush
                                            .whileKsenaxPressed(pressed),
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
                                            isEnabled = state.draftSnapshot
                                                .launchAnimationEnabled,
                                            enabledBrush = theme.selectedBrush,
                                            disabledBrush = theme.inactiveBrush,
                                        ).whileKsenaxPressed(pressed),
                                        fontFamily = KsenaxFontFamily.SETTINGS_MINECRAFT,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                    )
                                }
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
                theme = theme,
                onDismiss = onDismissExitConfirmation,
                onDiscard = onDiscardAndExit,
                onSave = onSaveAndExit,
            )
        }
    }
}
