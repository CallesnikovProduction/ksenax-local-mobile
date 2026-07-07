package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import android.content.Context
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxRawToolArgumentsObject
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolResult
import org.json.JSONObject

/**
 * Исполнитель коротких FunctionGemma-вызовов `torch_on{}`, `torch_off{}` и
 * `torch_toggle{}`.
 *
 * Новый протокол не передаёт boolean-аргумент: направление действия уже
 * находится в имени функции. Android-операция пока делегируется проверенному
 * [FlashlightToolExecutor], чтобы тест one-shot prompt-а не создавал вторую
 * реализацию CameraManager, permission checks и обработки ошибок.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class TorchExecutor(context: Context) : KsenaxToolExecutor {

    private val flashlightToolExecutor = FlashlightToolExecutor(context)

    override suspend fun execute(call: KsenaxToolCall): KsenaxToolResult {
        val hasNoArguments = runCatching {
            JSONObject(call.arguments.JSONtoString()).length() == 0
        }.getOrDefault(false)
        if (!hasNoArguments) {
            return KsenaxToolResult.Failure(
                callId    = call.id,
                toolName  = call.name,
                reason    = "One-shot torch functions accept only an empty arguments object.",
                errorCode = "INVALID_ARGUMENTS",
            )
        }

        val enabled = when (call.name) {
            TorchToolOneShot.On.codeName  -> true
            TorchToolOneShot.Off.codeName -> false
            TorchToolOneShot.Toggle.codeName -> !lastRequestedTorchEnabled
            else                         -> {
                return KsenaxToolResult.Failure(
                    callId    = call.id,
                    toolName  = call.name,
                    reason    = "TorchExecutor cannot execute tool: ${call.name}.",
                    errorCode = "INVALID_TOOL",
                )
            }
        }

        val legacyCall = call.copy(
            name      = FlashlightToolExecutor.TOOL_NAME,
            arguments = KsenaxRawToolArgumentsObject(
                if (enabled) """{"enabled":true}""" else """{"enabled":false}"""
            ),
        )

        return flashlightToolExecutor.execute(legacyCall)
            .also { result ->
                if (result is KsenaxToolResult.Success) {
                    lastRequestedTorchEnabled = enabled
                }
            }
            .withToolName(call.name)
    }

    /**
     * Возвращает результат Android-операции под именем one-shot функции.
     *
     * Старый executor не знает о `torch_on`/`torch_off` и закономерно пишет в
     * result имя `torch_tool`. На внешней границе сохраняем имя исходного
     * FunctionGemma call-а, чтобы call/result оставались связаны.
     */
    private fun KsenaxToolResult.withToolName(toolName: String): KsenaxToolResult =
        when (this) {
            is KsenaxToolResult.Success -> copy(toolName = toolName)
            is KsenaxToolResult.Failure -> copy(toolName = toolName)
        }

    private companion object {
        @Volatile
        private var lastRequestedTorchEnabled: Boolean = false
    }
}
