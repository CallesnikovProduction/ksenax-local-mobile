package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRuntimeConfig
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Применяет сохранённые настройки model runtime ко всем response-сессиям.
 *
 * UI передаёт сюда только числовой размер контекстного окна. Controller не
 * переносит UI-типы в communication/model и не знает деталей LiteRT-LM.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxModelRuntimeSettingsController(
    private val responseModelSessions: List<KsenaxModelSession>,
) {

    private val configurationMutex = Mutex()

    init {
        require(responseModelSessions.isNotEmpty()) {
            "At least one response model session is required."
        }
    }

    /**
     * Передаёт выбранный общий бюджет input + output tokens каждой response-сессии.
     *
     * Вызовы сериализуются, чтобы два сохранения настроек не пересобирали один
     * native engine одновременно. Каждая session сама решает, нужно ли закрывать
     * уже поднятый engine: повтор текущей конфигурации является no-op.
     *
     * @since 0.2
     */
    suspend fun applyMaxContextTokens(maxContextTokens: Int) {
        val runtimeConfig = KsenaxModelRuntimeConfig(
            maxContextTokens = maxContextTokens,
        )

        configurationMutex.withLock {
            coroutineScope {
                responseModelSessions
                    .map { session ->
                        async {
                            session.configureRuntime(runtimeConfig)
                        }
                    }
                    .awaitAll()
            }
        }
    }
}
