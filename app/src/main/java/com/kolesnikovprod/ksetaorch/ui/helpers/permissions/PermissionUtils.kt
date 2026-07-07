package com.kolesnikovprod.ksetaorch.ui.helpers.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kolesnikovprod.ksetaorch.storage.resolve.text.UserSelectedTextWorkspaceStorage

/**
 * Проверяет, выдано ли приложению Android-разрешение на запись звука.
 *
 * @return `true`, если выдано разрешение [Manifest.permission.RECORD_AUDIO], `false` в ином случае.
 * @since 0.2
 */
internal fun Context.hasRecordAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Проверяет доступ приложения к камере.
 *
 * Android использует [Manifest.permission.CAMERA] и для управления фонариком
 * через CameraManager.
 *
 * @since 0.2
 */
internal fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Открывает системный экран приложения, где пользователь может отозвать уже
 * выданные runtime-разрешения.
 *
 * Android не разрешает обычному приложению отзывать собственные permissions
 * программно, поэтому выключение активного permission-toggle ведёт сюда.
 *
 * @since 0.2
 */
internal fun Context.openApplicationPermissionSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun rememberMicrophonePermissionLauncher(
    context:                   Context,
    onPermissionStateChanged: (Boolean) -> Unit,
    onGranted:                () -> Unit
) : ManagedActivityResultLauncher<String, Boolean> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            val hasPermission = isGranted || context.hasRecordAudioPermission()

            onPermissionStateChanged(hasPermission)

            if (hasPermission) {
                onGranted()
            }
        },
    )
}

@Composable
fun rememberWorkingFolderLauncher(
    onFolderSelected: (KsenaxWorkingFolderSelection) -> Unit
): ManagedActivityResultLauncher<Uri?, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { folderUri ->
            if (folderUri != null) {
                val hasPersistedPermission =
                    UserSelectedTextWorkspaceStorage.takePersistableReadWritePermission(
                    context = context,
                    treeUri = folderUri,
                    resultFlags =
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                onFolderSelected(
                    KsenaxWorkingFolderSelection(
                        treeUri = folderUri.toString(),
                        displayPath = folderUri.toKsenaxDisplayPath(),
                        hasPersistedPermission = hasPersistedPermission,
                    ),
                )
            }
        }
    )
}

data class KsenaxWorkingFolderSelection(
    val treeUri: String,
    val displayPath: String,
    val hasPersistedPermission: Boolean,
)

/**
 * Преобразует SAF URI выбранной директории в короткий путь для отображения.
 *
 * Для primary storage значение вида `primary:Documents` показывается как
 * `/storage/emulated/0/Documents`. Результат предназначен только для UI: он не
 * заменяет исходный [Uri], не гарантирует доступ через [java.io.File] и не
 * сохраняет разрешение на работу с директорией.
 *
 * @since 0.2
 */
private fun Uri.toKsenaxDisplayPath(): String {
    val decodedLastSegment = lastPathSegment
        ?.let(Uri::decode)
        ?.trim()

    if (decodedLastSegment.isNullOrBlank()) {
        return toString()
    }

    if (decodedLastSegment.startsWith(PrimaryExternalStoragePrefix)) {
        val relativePath = decodedLastSegment
            .removePrefix(PrimaryExternalStoragePrefix)
            .trim('/')

        return if (relativePath.isBlank()) {
            "/storage/emulated/0"
        } else {
            "/storage/emulated/0/$relativePath"
        }
    }

    return decodedLastSegment.replace(':', '/')
}

private const val PrimaryExternalStoragePrefix = "primary:"
