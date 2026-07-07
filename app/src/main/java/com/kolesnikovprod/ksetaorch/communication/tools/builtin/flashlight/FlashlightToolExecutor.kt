package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolDefinition
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Исполнитель встроенного инструмента фонарика.
 *
 * Класс получает [KsenaxToolCall], читает поле `enabled` из `arguments` и
 * управляет torch mode через Android [CameraManager]. Модель выбирает только
 * намерение и аргументы. Android-действие выполняет этот исполнитель после
 * проверки имени инструмента, наличия flash-unit и разрешения камеры.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class FlashlightToolExecutor(context: Context) : KsenaxToolExecutor {

    /**
     * Контекст приложения для доступа к системным сервисам и проверки разрешений.
     *
     * @since 0.2
     */
    private val appContext: Context = context.applicationContext


    override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult =
        withContext(Dispatchers.Default) {
            if (call.name != TOOL_NAME) {
                return@withContext KsenaxToolResult.Failure(
                    callId    = call.id,
                    toolName  = call.name,
                    reason    = "FlashlightToolExecutor cannot execute tool: ${call.name}.",
                    errorCode = "INVALID_TOOL",
                )
            }

            val enabled = parseEnabled(call.arguments.JSONtoString())
                ?: return@withContext KsenaxToolResult.Failure(
                    callId    = call.id,
                    toolName  = call.name,
                    reason    = "Argument `enabled` must be a boolean.",
                    errorCode = "INVALID_ARGUMENTS",
                )

            turn(call = call, flashEnabled = enabled)
        }

    /**
     * Выполняет включение или выключение фонарика после разбора аргументов.
     *
     * Метод проверяет наличие flash-unit, разрешение камеры и подходящий camera
     * id. Ошибки Android API превращаются в [KsenaxToolResult.Failure], чтобы
     * UI получил понятную причину сбоя.
     *
     * @since 0.2
     */
    @SuppressLint("MissingPermission")
    private fun turn(
        call:         KsenaxToolCall,
        flashEnabled: Boolean,
    ): KsenaxToolResult {
        if (!appContext.packageManager.hasSystemFeature(
                PackageManager.FEATURE_CAMERA_FLASH
        )) {
            return KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "На устройстве не найден flash-unit для фонарика",
                errorCode = "FLASH_UNIT_NOT_FOUND",
            )
        }

        if (!hasCameraPermission()) {
            return KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Для управления фонариком нужно разрешение камеры",
                errorCode = "MISSING_CAMERA_PERMISSION",
            )
        }

        val cameraManager =
            appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraId = findFlashlightCameraId(cameraManager)
            ?: return KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Камера с доступным torch mode не найдена",
                errorCode = "FLASH_CAMERA_NOT_FOUND",
            )

        return try {
            cameraManager.setTorchMode(cameraId, flashEnabled)
            KsenaxToolResult.Success(
                callId      = call.id,
                toolName    = call.name,
                message     = if (flashEnabled) "Фонарик включен" else "Фонарик выключен",
                payloadJson = JSONObject()
                    .put("enabled", flashEnabled)
                    .put("camera_id", cameraId)
                    .toString(),
            )
        } catch (_: SecurityException) {
            KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Android не разрешил управление фонариком без доступа к камере",
                errorCode = "CAMERA_PERMISSION_DENIED",
            )
        } catch (_: CameraAccessException) {
            KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Камера сейчас недоступна для управления фонариком",
                errorCode = "CAMERA_UNAVAILABLE",
            )
        } catch (_: IllegalArgumentException) {
            KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "Выбранная камера не поддерживает torch mode",
                errorCode = "TORCH_MODE_UNSUPPORTED",
            )
        }
    }

    /**
     * Проверяет, выдал ли пользователь разрешение камеры.
     *
     * Android требует это разрешение даже для torch mode, хотя приложение не
     * делает фото и не открывает предпросмотр камеры.
     *
     * @since 0.2
     */
    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Ищет камеру, через которую можно управлять фонариком.
     *
     * Метод предпочитает заднюю камеру, потому что на телефонах именно она чаще
     * связана с LED-фонариком. Если задняя камера не найдена, используется первая
     * камера с доступным flash-unit.
     *
     * @since 0.2
     */
    private fun findFlashlightCameraId(cameraManager: CameraManager): String? {
        val fallbackCameraId = cameraManager.cameraIdList.firstOrNull { cameraId ->
            hasFlashUnit(cameraId, cameraManager)
        }

        return cameraManager.cameraIdList.firstOrNull { cameraId ->
            hasFlashUnit(cameraId, cameraManager) && isBackFacingCamera(cameraId, cameraManager)
        } ?: fallbackCameraId
    }

    /**
     * Проверяет, есть ли у камеры физический выход на flash-unit.
     *
     * @since 0.2
     */
    private fun hasFlashUnit(cameraId: String, cameraManager: CameraManager): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    /**
     * Проверяет, смотрит ли камера назад.
     *
     * @since 0.2
     */
    private fun isBackFacingCamera(cameraId: String, cameraManager: CameraManager): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
    }

    /**
     * Достаёт значение `enabled` из JSON-аргументов вызова инструмента.
     *
     * Основная схема требует boolean, но метод принимает несколько строковых и
     * числовых форм. Это снижает шанс сорвать ход из-за мелкого форматного
     * отклонения локальной модели.
     *
     * @since 0.2
     */
    private fun parseEnabled(arguments: String): Boolean? {
        val json = try {
            JSONObject(arguments)
        } catch (_: JSONException) {
            return null
        }

        if (!json.has("enabled")) {
            return null
        }

        return when (val value = json.opt("enabled")) {
            is Boolean -> value
            is Number  -> value.toInt() != 0
            is String  -> when (value.trim().lowercase()) {
                "true", "yes", "1", "on", "enable", "enabled"    -> true
                "false", "no", "0", "off", "disable", "disabled" -> false
                else                                             -> null
            }
            else -> null
        }
    }

    /**
     * Метаданные старого JSON-tool фонарика.
     *
     * Исполнитель оставлен как совместимый Android executor, но agentic режим
     * теперь идёт через [TorchExecutor] и FunctionGemma OneShot-протокол.
     *
     * @since 0.2
     */
    companion object {

        /**
         * Имя инструмента, которое модель должна вернуть в `tool_calls[0].name`.
         *
         * @since 0.2
         */
        const val TOOL_NAME = "torch_tool"

        /**
         * Возвращает описание инструмента для маршрутизирующей модели.
         *
         * @since 0.2
         */
        fun definitions(): List<KsenaxToolDefinition> =
            listOf(
                KsenaxToolDefinition(
                    name        = TOOL_NAME,
                    description = "Controls the device flashlight. " +
                                  "Set enabled=true to turn it ON, enabled=false to turn it OFF.",
                    arguments   = KsenaxRawToolArgumentsObject(FLASHLIGHT_ARGUMENT_SCHEMA),
                    riskLevel   = KsenaxToolRiskLevel.LOW,
                    requiresConfirmationByDefault = false,
                )
            )

        /**
         * JSON-схема аргументов фонарика.
         *
         * Схема требует одно поле `enabled`: `true` включает фонарик, `false`
         * выключает его.
         *
         * @since 0.2
         */
        private val FLASHLIGHT_ARGUMENT_SCHEMA: String =
            """
            {
              "type": "object",
              "properties": {
                "enabled": {
                  "type": "boolean"
                }
              },
              "required": ["enabled"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
