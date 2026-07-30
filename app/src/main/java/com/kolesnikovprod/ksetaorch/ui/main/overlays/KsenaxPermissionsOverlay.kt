package com.kolesnikovprod.ksetaorch.ui.main.overlays

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelSquareFrame
import com.kolesnikovprod.ksetaorch.ui.components.PixelToggleIcon
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasCameraPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasRecordAudioPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.openApplicationPermissionSettings
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CARD_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_DIM_BACKGROUND_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_PRIMARY_TEXT_COLOUR

private val PermissionsPanelBackground = Color(0xF7070A13)

/**
 * Overlay управления реальными runtime-разрешениями OpenKsenax.
 *
 * Выключенный toggle запускает системный запрос permission. Для уже выданного
 * разрешения Android не предоставляет API прямого отзыва, поэтому нажатие
 * открывает системные настройки приложения.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxPermissionsOverlay(
    theme: KsenaxThemeVisuals,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember(context) {
        mutableStateOf(context.hasCameraPermission())
    }
    var hasMicrophonePermission by remember(context) {
        mutableStateOf(context.hasRecordAudioPermission())
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            hasCameraPermission = context.hasCameraPermission()
        },
    )
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            hasMicrophonePermission = context.hasRecordAudioPermission()
        },
    )

    LaunchedEffect(isVisible, context) {
        if (isVisible) {
            hasCameraPermission = context.hasCameraPermission()
            hasMicrophonePermission = context.hasRecordAudioPermission()
        }
    }

    DisposableEffect(isVisible, context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (isVisible && event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = context.hasCameraPermission()
                hasMicrophonePermission = context.hasRecordAudioPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 190)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OVERLAY_DIM_BACKGROUND_COLOUR)
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = onDismiss,
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            PermissionsCard(
                theme = theme,
                hasCameraPermission = hasCameraPermission,
                hasMicrophonePermission = hasMicrophonePermission,
                onCameraToggle = {
                    if (hasCameraPermission) {
                        context.openApplicationPermissionSettings()
                    } else {
                        cameraPermissionLauncher.launch(
                            Manifest.permission.CAMERA,
                        )
                    }
                },
                onMicrophoneToggle = {
                    if (hasMicrophonePermission) {
                        context.openApplicationPermissionSettings()
                    } else {
                        microphonePermissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    theme: KsenaxThemeVisuals,
    hasCameraPermission: Boolean,
    hasMicrophonePermission: Boolean,
    onCameraToggle: () -> Unit,
    onMicrophoneToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .widthIn(max = 382.dp)
            .heightIn(max = 680.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            },
    ) {
        PixelSteppedCornerFrame(
            brush = theme.overlayMainBrush,
            backgroundColor = OVERLAY_CARD_BACKGROUND_COLOUR,
            strokeWidth = 3.dp,
            modifier = Modifier.matchParentSize(),
        )
        PixelSteppedCornerFrame(
            brush = theme.overlayMiniBrush,
            backgroundColor = Color.Transparent,
            strokeWidth = 1.dp,
            modifier = Modifier
                .matchParentSize()
                .padding(7.dp),
        )
        PermissionsCornerAccents(
            theme = theme,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 25.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PermissionsBrand(theme = theme)

            Spacer(modifier = Modifier.height(8.dp))

            DownloadGradientText(
                text = "РАЗРЕШЕНИЯ",
                brush = theme.overlayMainBrush,
                fontSize = 31.sp,
                lineHeight = 34.sp,
                fontFamily = KsenaxFontFamily.TITLES_COMIC_SANS_PIXEL,
            )

            Text(
                text = "Для работы нужны доступы",
                color = theme.mutedColor,
                fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(17.dp))
            PermissionTitleDivider(theme = theme)
            Spacer(modifier = Modifier.height(13.dp))

            PermissionToggleRow(
                theme = theme,
                iconRes = R.drawable.settings_permissions_camera,
                title = "Камера",
                purpose = "Для управления фонариком",
                isGranted = hasCameraPermission,
                onToggle = onCameraToggle,
            )

            PermissionDottedDivider(
                theme = theme,
                modifier = Modifier.padding(vertical = 10.dp),
            )

            PermissionToggleRow(
                theme = theme,
                iconRes = R.drawable.settings_permissions_microphone,
                title = "Микрофон",
                purpose = "Для голосового ввода",
                isGranted = hasMicrophonePermission,
                onToggle = onMicrophoneToggle,
            )

            Spacer(modifier = Modifier.height(18.dp))
            PermissionPrivacyNote(theme = theme)
        }
    }
}

@Composable
private fun PermissionsBrand(theme: KsenaxThemeVisuals) {
    Box(
        modifier = Modifier.size(width = 128.dp, height = 82.dp),
        contentAlignment = Alignment.Center,
    ) {
        GradientIcon(
            drawableId = R.drawable.settings_permissions_particle1,
            contentDescription = null,
            brush = theme.overlayMiniBrush,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 9.dp, top = 19.dp)
                .size(17.dp),
        )
        GradientIcon(
            drawableId = R.drawable.settings_permissions_particle2,
            contentDescription = null,
            brush = theme.overlayMainBrush,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 16.dp)
                .size(18.dp),
        )
        GradientIcon(
            drawableId = R.drawable.settings_permissions_brand,
            contentDescription = null,
            brush = theme.overlayMainBrush,
            modifier = Modifier.size(67.dp),
        )
    }
}

@Composable
private fun PermissionToggleRow(
    theme: KsenaxThemeVisuals,
    iconRes: Int,
    title: String,
    purpose: String,
    isGranted: Boolean,
    onToggle: () -> Unit,
) {
    KsenaxPressableBox(
        onClick = onToggle,
        role = Role.Switch,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) { pressed ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                PixelSquareFrame(
                    brush = theme.overlayMiniBrush.whileKsenaxPressed(pressed),
                    backgroundColor = PermissionsPanelBackground,
                    modifier = Modifier.matchParentSize(),
                )
                GradientIcon(
                    drawableId = iconRes,
                    contentDescription = null,
                    brush = theme.overlayMainBrush.whileKsenaxPressed(pressed),
                    modifier = Modifier.size(36.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = OVERLAY_PRIMARY_TEXT_COLOUR.whileKsenaxPressed(pressed),
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS_BOLD,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = purpose,
                    color = theme.mutedColor.whileKsenaxPressed(pressed),
                    fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }

            PixelToggleIcon(
                isEnabled = isGranted,
                enabledBrush = theme.selectedBrush.whileKsenaxPressed(pressed),
                disabledBrush = theme.inactiveBrush.whileKsenaxPressed(pressed),
                contentDescription =
                    if (isGranted) "$title: разрешено" else "$title: запрещено",
                modifier = Modifier.size(width = 58.dp, height = 34.dp),
            )
        }
    }
}

@Composable
private fun PermissionTitleDivider(theme: KsenaxThemeVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PermissionDottedDivider(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
        Canvas(modifier = Modifier.size(13.dp)) {
            val pixel = 2.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawRect(
                brush = theme.overlayMainBrush,
                topLeft = Offset(center.x - pixel / 2f, 0f),
                size = Size(pixel, size.height),
            )
            drawRect(
                brush = theme.overlayMainBrush,
                topLeft = Offset(0f, center.y - pixel / 2f),
                size = Size(size.width, pixel),
            )
        }
        PermissionDottedDivider(
            theme = theme,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PermissionDottedDivider(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        val dashWidth = 5.dp.toPx()
        val gap = 4.dp.toPx()
        val height = 1.dp.toPx()
        repeat((size.width / (dashWidth + gap)).toInt()) { index ->
            drawRect(
                color = theme.markerColors[
                    index % theme.markerColors.size
                ].copy(alpha = 0.48f),
                topLeft = Offset(
                    x = index * (dashWidth + gap),
                    y = (size.height - height) / 2f,
                ),
                size = Size(dashWidth, height),
            )
        }
    }
}

@Composable
private fun PermissionPrivacyNote(theme: KsenaxThemeVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIcon(
            drawableId = R.drawable.settings_permissions_info,
            contentDescription = null,
            brush = theme.overlayMiniBrush,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Разрешения используются только на устройстве. " +
                "OpenKsenax не передаёт эти данные третьим лицам.",
            color = theme.mutedColor,
            fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PermissionsCornerAccents(
    theme: KsenaxThemeVisuals,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(5.dp)) {
        val pixel = 3.dp.toPx()
        val colour = theme.accentColor.copy(alpha = 0.72f)

        fun corner(x: Float, y: Float, xDirection: Float, yDirection: Float) {
            drawRect(
                color = colour,
                topLeft = Offset(
                    x = x + if (xDirection < 0f) -pixel * 3f else 0f,
                    y = y,
                ),
                size = Size(pixel * 3f, pixel),
            )
            drawRect(
                color = colour,
                topLeft = Offset(
                    x = x,
                    y = y + if (yDirection < 0f) -pixel * 3f else 0f,
                ),
                size = Size(pixel, pixel * 3f),
            )
        }

        corner(pixel * 4f, pixel * 4f, 1f, 1f)
        corner(size.width - pixel * 5f, pixel * 4f, -1f, 1f)
        corner(pixel * 4f, size.height - pixel * 5f, 1f, -1f)
        corner(
            size.width - pixel * 5f,
            size.height - pixel * 5f,
            -1f,
            -1f,
        )
    }
}
