package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.main.overlays.PixelSteppedCornerFrame
import com.kolesnikovprod.ksetaorch.ui.theme.LocalKsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ERROR_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_INACTIVE_PIXEL_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_NEGATIVE_BRUSH

/**
 * Некликабельная LOCAL FIRST / ON DEVICE / STAY SAFE плашка.
 *
 * @since 0.3
 */
@Composable
internal fun KsenaxLocalTrustStrip(
    modifier: Modifier = Modifier,
) {
    val theme = LocalKsenaxThemeVisuals.current

    Box(
        modifier = modifier.height(46.dp),
    ) {
        PixelSteppedCornerFrame(
            brush = theme.overlayMainBrush,
            backgroundColor = VerificationPanelBackground,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrustStripItem(
                icon = R.drawable.olay_verify_badge_localfirst,
                title = "LOCAL FIRST",
                subtitle = "YOUR DATA STAYS HERE",
                activeColour = theme.markerColors[0],
                lowColour = theme.markerColors[0].copy(alpha = 0.54f),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(1.dp))
            TrustStripDivider()
            Spacer(Modifier.size(3.dp))
            TrustStripItem(
                icon = R.drawable.olay_verify_badge_ondevice,
                title = "ON DEVICE",
                subtitle = "NO CLOUD. FULL CONTROL.",
                activeColour = theme.markerColors[1],
                lowColour = theme.markerColors[1].copy(alpha = 0.54f),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(1.dp))
            TrustStripDivider()
            Spacer(Modifier.size(3.dp))
            TrustStripItem(
                icon = R.drawable.olay_verify_badge_staysafe,
                title = "STAY SAFE",
                subtitle = "PRIVATE BY DESIGN",
                activeColour = theme.markerColors[3],
                lowColour = theme.markerColors[3].copy(alpha = 0.54f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.TrustStripDivider() {
    val theme = LocalKsenaxThemeVisuals.current

    Canvas(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(width = 1.dp, height = 20.dp),
    ) {
        drawRect(color = theme.mutedColor.copy(alpha = 0.42f))
    }
}

@Composable
private fun TrustStripItem(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    activeColour: Color,
    lowColour: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PixelVerificationBrushIcon(
            drawableId = icon,
            brush = SolidColor(activeColour),
            accessibilityDescription = null,
            modifier = Modifier.size(26.dp),
        )

        Spacer(modifier = Modifier.width(5.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = activeColour,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = lowColour,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                maxLines = 2,
            )
        }
    }
}

/**
 * Единственная интерактивная кнопка внутри validation-card.
 *
 * @since 0.3
 */
@Composable
internal fun KsenaxVerificationCancelButton(
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val disabledColour =
        OVERLAY_INACTIVE_PIXEL_COLOUR.copy(alpha = 0.88f)

    KsenaxPressableBox(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        PixelSteppedCornerFrame(
            brush = if (isEnabled) {
                OVERLAY_NEGATIVE_BRUSH.whileKsenaxPressed(pressed)
            } else {
                SolidColor(disabledColour)
            },
            backgroundColor = VerificationPanelBackground,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "ПРЕРВАТЬ",
            color = if (isEnabled) {
                OVERLAY_ERROR_COLOUR.whileKsenaxPressed(pressed)
            } else {
                disabledColour
            },
            fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            fontSize = 14.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}
