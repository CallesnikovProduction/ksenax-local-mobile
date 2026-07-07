package com.kolesnikovprod.ksetaorch.download.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Лёгкая проверка наличия локальной модели OpenKsenax.
 *
 * Исходная проблема: пользователь хочет отправить сообщение, а программа должна понять,
 * можно ли уже запускать Gemma или нет?
 * - проверять по [com.kolesnikovprod.ksetaorch.ui.viewmodels.KsenaxMainUiState] — неправильно,
 * ведь после перезапуска это состояние обновится в дефолт
 * - а вот запускать полную проверку по координатору — тяжело, так как координатор тяжелый.
 *
 * Класс отвечает только на быстрый UI-вопрос: есть ли в app-specific
 * директории `models` хотя бы один непустой файл, имя которого начинается с
 * `openksenax_`.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
class KsenaxModelFilePresenceChecker(
    context: Context,
) {
    private val appContext = context.applicationContext

    /**
     * Проверяет конкретную директорию внутри `models`.
     *
     * Используется там, где важно не перепутать разные `openksenax_` артефакты:
     * например, Gemma `.litertlm` и Vosk zip оба начинаются с общего префикса,
     * но отвечают за разные runtime-контуры.
     *
     * @since 0.3
     */
    suspend fun hasModelFileIn(
        modelDirectoryName: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val targetDirectory = File(
                File(
                    appContext.getExternalFilesDir(null),
                    MODELS_DIRECTORY_NAME
                ),
                modelDirectoryName,
            )

            targetDirectory
                .walkTopDown()
                .any { file ->
                    file.isFile &&
                        file.name.startsWith(OPEN_KSENAX_MODEL_PREFIX) &&
                        file.length() > 0L
                }
        }
    }

    private companion object {
        const val MODELS_DIRECTORY_NAME = "models"
        const val OPEN_KSENAX_MODEL_PREFIX = "openksenax_"
    }
}
