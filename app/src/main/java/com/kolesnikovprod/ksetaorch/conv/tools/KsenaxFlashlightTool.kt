package com.kolesnikovprod.ksetaorch.conv.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

/**
 * Tool для управления фонариком устройства.
 *
 * Класс не запрашивает runtime-permission сам. Он только проверяет, можно ли сейчас
 * выполнить действие, и возвращает понятный результат. Запрос разрешения камеры должен
 * остаться на UI/permission-слое приложения.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class KsenaxFlashlightTool(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Включает фонарик, если устройство и разрешения это позволяют.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun turnFlashlightOn(): KsenaxToolResult {
        return setFlashlightEnabled(enabled = true)
    }

    /**
     * Выключает фонарик, если устройство и разрешения это позволяют.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun turnFlashlightOff(): KsenaxToolResult {
        return setFlashlightEnabled(enabled = false)
    }

    /**
     * Общий вход для будущего tool-calling слоя.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun setFlashlightEnabled(enabled: Boolean): KsenaxToolResult {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return KsenaxToolResult.Failure("На устройстве не найден фонарик.")
        }

        if (!hasCameraPermission()) {
            return KsenaxToolResult.PermissionRequired(
                permission = Manifest.permission.CAMERA,
                message = "Для управления фонариком нужно разрешение камеры.",
            )
        }

        val cameraId = findFlashlightCameraId()
            ?: return KsenaxToolResult.Failure("Не удалось найти камеру с доступным фонариком.")

        return try {
            cameraManager.setTorchMode(cameraId, enabled)

            KsenaxToolResult.Success(
                message = if (enabled) {
                    "Фонарик включен."
                } else {
                    "Фонарик выключен."
                },
            )
        } catch (_: SecurityException) {
            KsenaxToolResult.PermissionRequired(
                permission = Manifest.permission.CAMERA,
                message = "Android не разрешил управлять фонариком без доступа к камере.",
            )
        } catch (_: CameraAccessException) {
            KsenaxToolResult.Failure("Камера сейчас недоступна для управления фонариком.")
        } catch (_: IllegalArgumentException) {
            KsenaxToolResult.Failure("Выбранная камера не поддерживает режим фонарика.")
        }
    }

    /**
     * Проверяет, выдано ли приложению разрешение камеры.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Ищет id камеры, которая может работать в torch mode.
     *
     * Предпочитает заднюю камеру, но оставляет fallback на любую камеру с flash unit.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun findFlashlightCameraId(): String? {
        val fallbackCameraId = cameraManager.cameraIdList.firstOrNull { cameraId ->
            hasFlashUnit(cameraId)
        }

        return cameraManager.cameraIdList.firstOrNull { cameraId ->
            hasFlashUnit(cameraId) && isBackFacingCamera(cameraId)
        } ?: fallbackCameraId
    }

    /**
     * Проверяет наличие flash unit у конкретной камеры.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun hasFlashUnit(cameraId: String): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        return characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    /**
     * Проверяет, является ли камера задней.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun isBackFacingCamera(cameraId: String): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        return characteristics.get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_BACK
    }
}

/**
 * Результат выполнения локального Android-tool.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
sealed interface KsenaxToolResult {
    val message: String

    /**
     * Успешный результат выполнения tool.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    data class Success(
        override val message: String,
    ) : KsenaxToolResult

    /**
     * Ошибка выполнения tool без запроса дополнительных разрешений.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    data class Failure(
        override val message: String,
    ) : KsenaxToolResult

    /**
     * Результат, который сообщает UI-слою о необходимости runtime permission.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    data class PermissionRequired(
        val permission: String,
        override val message: String,
    ) : KsenaxToolResult
}
