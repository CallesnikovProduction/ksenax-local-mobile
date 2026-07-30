package com.kolesnikovprod.ksetaorch.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.main.model.ChatMode
import com.kolesnikovprod.ksetaorch.ui.theme.design.ArabianNight
import com.kolesnikovprod.ksetaorch.ui.theme.design.CozyCode
import com.kolesnikovprod.ksetaorch.ui.theme.design.MoonValley
import com.kolesnikovprod.ksetaorch.ui.theme.design.SurrealFantasy
import com.kolesnikovprod.ksetaorch.ui.theme.design.TokyoDrift

/**
 * Стабильный идентификатор пользовательской темы.
 *
 * В настройках сохраняется только это значение. Drawable, кисти и остальные
 * presentation-токены восстанавливаются через [visuals], поэтому они не
 * протекают в persistence-слой.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class KsenaxThemeId {
    MoonValley,
    ArabianNight,
    TokyoDrift,
    CozyCode,
    SurrealFantasy,
}

/**
 * Полный набор визуальных токенов одной темы OpenKsenax.
 *
 * Компоненты получают этот контракт готовым и не решают самостоятельно,
 * какой цвет или drawable соответствует выбранному [id].
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
class KsenaxThemeVisuals(
    val id: KsenaxThemeId,
    val displayName: String,
    @param:DrawableRes val previewDrawableRes: Int,
    @param:DrawableRes val backgroundDrawableRes: Int,
    @param:DrawableRes val foregroundDrawableRes: Int,
    val symbolDrawableRes: List<Int>,
    val heroBrush: Brush,
    val controlsBrush: Brush,
    val voiceBrush: Brush,
    val settingsBrush: Brush,
    val selectedBrush: Brush,
    val inactiveBrush: Brush,
    val basicModeBrush: Brush,
    val agenticModeBrush: Brush,
    val temporaricModeBrush: Brush,
    val accentColor: Color,
    val mutedColor: Color,
    val sparkleColors: List<Color>,
    val markerColors: List<Color>,
    val typingPhrases: List<String>,
    val overlayMainBrush: Brush,
    val overlayMiniBrush: Brush,
) {
    init {
        require(typingPhrases.isNotEmpty()) {
            "Theme $id must contain at least one typing phrase"
        }
        require(typingPhrases.none(String::isBlank)) {
            "Theme $id contains a blank typing phrase"
        }
    }

    fun modeBrush(mode: ChatMode): Brush = when (mode) {
        ChatMode.Basic -> basicModeBrush
        ChatMode.Agentic -> agenticModeBrush
        ChatMode.Temporaric -> temporaricModeBrush
    }
}

private val MoonValleyVisuals = KsenaxThemeVisuals(
    id = KsenaxThemeId.MoonValley,
    displayName = "Moon Valley",
    previewDrawableRes = R.drawable.theme_preview_moon_valley,
    backgroundDrawableRes = R.drawable.okx_background_moon_valley,
    foregroundDrawableRes = R.drawable.okx_foreground_moon_valley,
    symbolDrawableRes = emptyList(),
    heroBrush = MoonValley.heroBrush,
    controlsBrush = MoonValley.controlsBrush,
    voiceBrush = MoonValley.voiceBrush,
    settingsBrush = MoonValley.settingsBrush,
    selectedBrush = MoonValley.selectedBrush,
    inactiveBrush = MoonValley.inactiveBrush,
    basicModeBrush = MoonValley.basicModeBrush,
    agenticModeBrush = MoonValley.agenticModeBrush,
    temporaricModeBrush = MoonValley.temporaricModeBrush,
    accentColor = MoonValley.accentColor,
    mutedColor = MoonValley.mutedColor,
    sparkleColors = MoonValley.sparkleColors,
    markerColors = MoonValley.markerColors,
    typingPhrases = MoonValley.typingPhrases,
    overlayMainBrush = MoonValley.overlayMainBrush,
    overlayMiniBrush = MoonValley.overlayMiniBrush,
)

private val ArabianNightVisuals = KsenaxThemeVisuals(
    id = KsenaxThemeId.ArabianNight,
    displayName = "Arabian Night",
    previewDrawableRes = R.drawable.theme_preview_arabian_night,
    backgroundDrawableRes = R.drawable.okx_background_arabian_night,
    foregroundDrawableRes = R.drawable.okx_foreground_arabian_night,
    symbolDrawableRes = listOf(
        R.drawable.settings_theme_anight_sym_1,
        R.drawable.settings_theme_anight_sym_2,
        R.drawable.settings_theme_anight_sym_3,
        R.drawable.settings_theme_anight_sym_4,
    ),
    heroBrush = ArabianNight.heroBrush,
    controlsBrush = ArabianNight.controlsBrush,
    voiceBrush = ArabianNight.voiceBrush,
    settingsBrush = ArabianNight.settingsBrush,
    selectedBrush = ArabianNight.selectedBrush,
    inactiveBrush = ArabianNight.inactiveBrush,
    basicModeBrush = ArabianNight.basicModeBrush,
    agenticModeBrush = ArabianNight.agenticModeBrush,
    temporaricModeBrush = ArabianNight.temporaricModeBrush,
    accentColor = ArabianNight.accentColor,
    mutedColor = ArabianNight.mutedColor,
    sparkleColors = ArabianNight.sparkleColors,
    markerColors = ArabianNight.markerColors,
    typingPhrases = ArabianNight.typingPhrases,
    overlayMainBrush = ArabianNight.overlayMainBrush,
    overlayMiniBrush = ArabianNight.overlayMiniBrush,
)

private val TokyoDriftVisuals = KsenaxThemeVisuals(
    id = KsenaxThemeId.TokyoDrift,
    displayName = "Tokyo Drift",
    previewDrawableRes = R.drawable.theme_preview_tokyo_drift,
    backgroundDrawableRes = R.drawable.okx_background_tokyo_drift,
    foregroundDrawableRes = R.drawable.okx_foreground_tokyo_drift,
    symbolDrawableRes = listOf(
        R.drawable.settings_theme_edm_sym_1,
        R.drawable.settings_theme_edm_sym_2,
        R.drawable.settings_theme_edm_sym_3,
        R.drawable.settings_theme_edm_sym_4,
    ),
    heroBrush = TokyoDrift.heroBrush,
    controlsBrush = TokyoDrift.controlsBrush,
    voiceBrush = TokyoDrift.voiceBrush,
    settingsBrush = TokyoDrift.settingsBrush,
    selectedBrush = TokyoDrift.selectedBrush,
    inactiveBrush = TokyoDrift.inactiveBrush,
    basicModeBrush = TokyoDrift.basicModeBrush,
    agenticModeBrush = TokyoDrift.agenticModeBrush,
    temporaricModeBrush = TokyoDrift.temporaricModeBrush,
    accentColor = TokyoDrift.accentColor,
    mutedColor = TokyoDrift.mutedColor,
    sparkleColors = TokyoDrift.sparkleColors,
    markerColors = TokyoDrift.markerColors,
    typingPhrases = TokyoDrift.typingPhrases,
    overlayMainBrush = TokyoDrift.overlayMainBrush,
    overlayMiniBrush = TokyoDrift.overlayMiniBrush,
)

private val CozyCodeVisuals = KsenaxThemeVisuals(
    id = KsenaxThemeId.CozyCode,
    displayName = "Cozy Code",
    previewDrawableRes = R.drawable.theme_preview_cozy_code,
    backgroundDrawableRes = R.drawable.okx_background_cozy_code,
    foregroundDrawableRes = R.drawable.okx_foreground_cozy_code,
    symbolDrawableRes = listOf(
        R.drawable.settings_theme_cozy_sym_1,
        R.drawable.settings_theme_cozy_sym_2,
        R.drawable.settings_theme_cozy_sym_3,
        R.drawable.settings_theme_cozy_sym_4,
    ),
    heroBrush = CozyCode.heroBrush,
    controlsBrush = CozyCode.controlsBrush,
    voiceBrush = CozyCode.voiceBrush,
    settingsBrush = CozyCode.settingsBrush,
    selectedBrush = CozyCode.selectedBrush,
    inactiveBrush = CozyCode.inactiveBrush,
    basicModeBrush = CozyCode.basicModeBrush,
    agenticModeBrush = CozyCode.agenticModeBrush,
    temporaricModeBrush = CozyCode.temporaricModeBrush,
    accentColor = CozyCode.accentColor,
    mutedColor = CozyCode.mutedColor,
    sparkleColors = CozyCode.sparkleColors,
    markerColors = CozyCode.markerColors,
    typingPhrases = CozyCode.typingPhrases,
    overlayMainBrush = CozyCode.overlayMainBrush,
    overlayMiniBrush = CozyCode.overlayMiniBrush,
)

private val SurrealFantasyVisuals = KsenaxThemeVisuals(
    id = KsenaxThemeId.SurrealFantasy,
    displayName = "Surreal Fantasy",
    previewDrawableRes = R.drawable.theme_preview_surreal_fantasy,
    backgroundDrawableRes = R.drawable.okx_background_surreal_fantasy,
    foregroundDrawableRes = R.drawable.okx_foreground_surreal_fantasy,
    symbolDrawableRes = emptyList(),
    heroBrush = SurrealFantasy.heroBrush,
    controlsBrush = SurrealFantasy.controlsBrush,
    voiceBrush = SurrealFantasy.voiceBrush,
    settingsBrush = SurrealFantasy.settingsBrush,
    selectedBrush = SurrealFantasy.selectedBrush,
    inactiveBrush = SurrealFantasy.inactiveBrush,
    basicModeBrush = SurrealFantasy.basicModeBrush,
    agenticModeBrush = SurrealFantasy.agenticModeBrush,
    temporaricModeBrush = SurrealFantasy.temporaricModeBrush,
    accentColor = SurrealFantasy.accentColor,
    mutedColor = SurrealFantasy.mutedColor,
    sparkleColors = SurrealFantasy.sparkleColors,
    markerColors = SurrealFantasy.markerColors,
    typingPhrases = SurrealFantasy.typingPhrases,
    overlayMainBrush = SurrealFantasy.overlayMainBrush,
    overlayMiniBrush = SurrealFantasy.overlayMiniBrush,
)

/**
 * Все темы, доступные для single-selection на экране оформления.
 *
 * @since 0.3
 */
val KsenaxAvailableThemes: List<KsenaxThemeVisuals> = listOf(
    ArabianNightVisuals,
    TokyoDriftVisuals,
    CozyCodeVisuals,
    SurrealFantasyVisuals,
    MoonValleyVisuals,
)

/**
 * Разрешает сохранённый идентификатор в готовый presentation-контракт.
 *
 * @since 0.3
 */
val KsenaxThemeId.visuals: KsenaxThemeVisuals
    get() = when (this) {
        KsenaxThemeId.MoonValley -> MoonValleyVisuals
        KsenaxThemeId.ArabianNight -> ArabianNightVisuals
        KsenaxThemeId.TokyoDrift -> TokyoDriftVisuals
        KsenaxThemeId.CozyCode -> CozyCodeVisuals
        KsenaxThemeId.SurrealFantasy -> SurrealFantasyVisuals
    }

/**
 * Текущая тема внутри глубоко вложенного presentation-поддерева.
 *
 * Внешние экраны передают тему явно. CompositionLocal применяется внутри
 * составных overlay, чтобы их Canvas-примитивы не дублировали один и тот же
 * параметр через каждый служебный уровень.
 *
 * @since 0.3
 */
val LocalKsenaxThemeVisuals = staticCompositionLocalOf {
    MoonValleyVisuals
}
