package com.kolesnikovprod.ksetaorch.conv.tools

import android.Manifest
import androidx.annotation.RequiresPermission
import org.json.JSONException
import org.json.JSONObject

const val KSENAX_TORCH_ROUTER_SYSTEM_PROMPT = """
Ты — локальный Android action-router для MVP приложения Ksenax.

Ты не выполняешь действия сам.
Ты только выбираешь tool call.

В ТЕКУЩЕМ КОНТЕКСТЕ РАЗРЕШЕНЫ ТОЛЬКО ЭТИ tools:

1. torch_on()
Описание: включает фонарик устройства.
Аргументы: пустой объект {}.
Risk: LOW.
Confirmation: false.

2. torch_off()
Описание: выключает фонарик устройства.
Аргументы: пустой объект {}.
Risk: LOW.
Confirmation: false.

3. refuse(reason: string)
Описание: используется, если команда не относится к фонарику, запрещена,
опасна, невозможна для обычного Android-приложения или требует tool,
которого нет в MVP.
Risk: LOW.
Confirmation: false.

Правила:
- Верни только JSON.
- Не добавляй Markdown, комментарии, объяснения или текст вокруг JSON.
- Не придумывай tools.
- Не используй tools кроме torch_on, torch_off и refuse.
- Если пользователь просит включить фонарик, включить свет, включить torch,
  подсветить или зажечь фонарь — выбери torch_on.
- Если пользователь просит выключить фонарик, выключить свет, погасить фонарь
  или выключить torch — выбери torch_off.
- Если команда неоднозначна, не про фонарик, про Wi-Fi, будильник, календарь,
  звонки, секреты, API keys, root-действия или управление чужими приложениями —
  выбери refuse.
- Для секретов/API keys всегда refuse.
- Если команда невозможна для обычного Android-приложения, выбери refuse.
- Для refuse поле tool_calls должно быть пустым массивом, а refusal должен
  содержать строку причины.
- Для torch_on и torch_off поле refusal должно быть null.

Строгий формат успешного ответа:
{
  "tool_calls": [
    {
      "name": "torch_on",
      "arguments": {},
      "requires_confirmation": false,
      "risk_level": "LOW"
    }
  ],
  "refusal": null
}

Строгий формат отказа:
{
  "tool_calls": [],
  "refusal": {
    "reason": "Короткая причина отказа."
  }
}
"""

/**
 * Локальное представление результата action-routing для MVP с фонариком.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
sealed interface KsenaxTorchRoute {

    /**
     * Route включения фонарика.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    data object TorchOn : KsenaxTorchRoute

    /**
     * Route выключения фонарика.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    data object TorchOff : KsenaxTorchRoute

    /**
     * Route отказа от выполнения команды.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    data class Refused(
        val reason: String,
    ) : KsenaxTorchRoute
}

/**
 * Парсер JSON, который вернула локальная модель.
 *
 * Парсер намеренно принимает только allowlist tools для MVP. Даже если модель
 * выдумает другой tool, он не будет выполнен.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
object KsenaxTorchRouteParser {

    /**
     * Разбирает текстовый ответ модели в один из разрешенных route.
     *
     * Метод принимает только JSON-ответ с allowlisted tool names. Любой неизвестный
     * tool, некорректный JSON или пустой tool call превращается в [KsenaxTorchRoute.Refused].
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun parse(modelResponse: String): KsenaxTorchRoute {
        val jsonText = extractJsonObject(modelResponse)
            ?: return KsenaxTorchRoute.Refused("Модель не вернула JSON-объект.")

        return try {
            val root = JSONObject(jsonText)

            if (!root.isNull("refusal")) {
                return KsenaxTorchRoute.Refused(
                    reason = root.optJSONObject("refusal")
                        ?.optString("reason")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Модель отказалась выполнять команду.",
                )
            }

            val toolCalls = root.optJSONArray("tool_calls")
                ?: return KsenaxTorchRoute.Refused("В ответе модели нет массива tool_calls.")

            if (toolCalls.length() == 0) {
                return KsenaxTorchRoute.Refused("Модель не выбрала tool call.")
            }

            val firstToolCall = toolCalls.optJSONObject(0)
                ?: return KsenaxTorchRoute.Refused("Первый tool call имеет неверный формат.")

            when (firstToolCall.optString("name")) {
                "torch_on" -> KsenaxTorchRoute.TorchOn
                "torch_off" -> KsenaxTorchRoute.TorchOff
                "refuse" -> KsenaxTorchRoute.Refused(
                    reason = firstToolCall.optJSONObject("arguments")
                        ?.optString("reason")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Модель выбрала отказ.",
                )
                else -> KsenaxTorchRoute.Refused("Модель выбрала неизвестный tool.")
            }
        } catch (_: JSONException) {
            KsenaxTorchRoute.Refused("JSON от модели не удалось разобрать.")
        }
    }

    /**
     * Извлекает первый JSON-объект из ответа модели.
     *
     * Нужен как защитный слой на случай, если модель случайно вернула текст вокруг JSON.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    private fun extractJsonObject(text: String): String? {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return null
        }

        return text.substring(startIndex, endIndex + 1)
    }
}

/**
 * Исполнитель allowlisted tool calls для MVP.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class KsenaxTorchToolExecutor(
    private val flashlightTool: KsenaxFlashlightTool,
) {

    /**
     * Выполняет route через локальный flashlight tool.
     *
     * Метод не принимает произвольные tool names: он работает только с sealed route,
     * который уже прошел parser/allowlist слой.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun execute(route: KsenaxTorchRoute): KsenaxToolResult {
        return when (route) {
            KsenaxTorchRoute.TorchOn -> flashlightTool.turnFlashlightOn()
            KsenaxTorchRoute.TorchOff -> flashlightTool.turnFlashlightOff()
            is KsenaxTorchRoute.Refused -> KsenaxToolResult.Failure(route.reason)
        }
    }
}
