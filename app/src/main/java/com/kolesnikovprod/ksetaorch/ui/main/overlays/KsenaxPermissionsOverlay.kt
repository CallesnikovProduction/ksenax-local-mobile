package com.kolesnikovprod.ksetaorch.ui.main.overlays

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kolesnikovprod.ksetaorch.ui.components.PixelToggleIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasCameraPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.hasRecordAudioPermission
import com.kolesnikovprod.ksetaorch.ui.helpers.permissions.openApplicationPermissionSettings
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.inactiveGradientBrush

private val PermissionsOverlayScrim = Color.Black.copy(alpha = 0.88f)
private val PermissionLabelColor = Color(0xFFD5DBE7)
private val PermissionPurposeColor = Color(0xFF748091)

/**
 * Overlay управления runtime-разрешениями приложения.
 *
 * Выключенный toggle запускает системный запрос permission. Для уже выданного
 * разрешения Android не предоставляет API прямого отзыва, поэтому нажатие
 * открывает системные настройки приложения.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Composable
fun KsenaxPermissionsOverlay(
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
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .drawWithCache {
                    onDrawBehind {
                        drawRect(color = PermissionsOverlayScrim)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 336.dp)
                    .height(278.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                PixelWideFrame(
                    brush = inactiveGradientBrush,
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = Color(0xF2050710),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Permissions",
                        color = Color.White,
                        fontFamily = KsenaxFontFamily.jersey10,
                        fontSize = 36.sp,
                        lineHeight = 34.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    PermissionToggleRow(
                        title = "Камера",
                        purpose = "Фонарик",
                        isGranted = hasCameraPermission,
                        onToggle = {
                            if (hasCameraPermission) {
                                context.openApplicationPermissionSettings()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionToggleRow(
                        title = "Микрофон",
                        purpose = "Голосовой ввод",
                        isGranted = hasMicrophonePermission,
                        onToggle = {
                            if (hasMicrophonePermission) {
                                context.openApplicationPermissionSettings()
                            } else {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    purpose: String,
    isGranted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .toggleable(
                value = isGranted,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = PermissionLabelColor,
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 14.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = purpose,
                color = PermissionPurposeColor,
                fontFamily = KsenaxFontFamily.tiny5,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
        }

        PixelToggleIcon(
            isEnabled = isGranted,
            contentDescription =
                if (isGranted) "$title: разрешено" else "$title: запрещено",
            modifier = Modifier.size(width = 58.dp, height = 34.dp),
        )
    }
}
