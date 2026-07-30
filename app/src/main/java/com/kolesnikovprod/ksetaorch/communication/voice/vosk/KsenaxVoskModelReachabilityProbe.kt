package com.kolesnikovprod.ksetaorch.communication.voice.vosk

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model

/**
 * Проверяет, что установленную директорию Vosk способен открыть нативный
 * runtime.
 *
 * Probe не выполняет транскрибацию и всегда закрывает тестовый [Model] после
 * успешного или неуспешного открытия.
 *
 * @since 0.3
 */
class KsenaxVoskModelReachabilityProbe(
    private val modelDirectoryPath: String,
) {
    suspend fun verify() {
        withContext(Dispatchers.IO) {
            val modelDirectory = File(modelDirectoryPath)
            require(modelDirectory.isDirectory) {
                "Vosk model directory does not exist: $modelDirectoryPath"
            }

            var model: Model? = null
            try {
                model = Model(modelDirectory.absolutePath)
            } finally {
                model?.close()
            }
        }
    }
}
