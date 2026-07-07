package com.kolesnikovprod.ksetaorch.communication.model

import com.kolesnikovprod.ksetaorch.communication.model.internal.litert.LiteRtModelSessionEngine

/**
 * Публичный LiteRT-LM фасад [KsenaxModelSession].
 *
 * Фасад сохраняет стабильное имя для composition root и скрывает внутреннее
 * устройство LiteRT engine. Parser, policy, tools и Android UI сюда не входят.
 *
 * @param modelPath путь к `.litertlm` модели.
 * @param cacheDirPath директория runtime-кэша LiteRT-LM.
 * @param audioBackend backend для multimodal audio input. Передайте `null` для
 *        text-only моделей, включая FunctionGemma: такая сессия не поднимает
 *        ненужный audio backend и отклоняет [KsenaxModelSession.transcribe] до
 *        обращения к runtime.
 * @param runtimeConfig начальные параметры engine, включая общий бюджет
 *        input + output tokens. Конфигурацию можно заменить позднее через
 *        [KsenaxModelSession.configureRuntime].
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class LiteRtKsenaxModelSession(
    modelPath: String,
    cacheDirPath: String,
    audioBackend: KsenaxLiteRtAudioBackend?,
    runtimeConfig: KsenaxModelRuntimeConfig = KsenaxModelRuntimeConfig.DEFAULT,
) : KsenaxModelSession by LiteRtModelSessionEngine(
    modelPath = modelPath,
    cacheDirPath = cacheDirPath,
    audioBackend = audioBackend,
    initialRuntimeConfig = runtimeConfig,
)
