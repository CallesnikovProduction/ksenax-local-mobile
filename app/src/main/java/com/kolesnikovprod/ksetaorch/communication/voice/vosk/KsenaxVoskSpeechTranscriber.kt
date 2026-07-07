package com.kolesnikovprod.ksetaorch.communication.voice.vosk

import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoskRecordedVoiceFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Результат локальной Vosk-транскрибации.
 *
 * @property text очищенный текст распознавания.
 * @property rawSegmentsJson JSON-сегменты, которые вернул Vosk во время чтения.
 * @property finalJson финальный JSON Vosk после конца PCM-stream.
 * @property modelDirectoryPath директория установленной Vosk-модели.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxVoskTranscriptionResult(
    val text:               String,
    val rawSegmentsJson:    List<String>,
    val finalJson:          String,
    val modelDirectoryPath: String,
)

/**
 * Локальный transcriber для Vosk.
 *
 * Класс принимает путь к уже установленной модели, например результат
 * `KsenaxVoskRuSmallInstallUseCase.getInstalledPath()`, и WAV-файл из
 * [com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoskVoiceFileRecorder].
 *
 * Он не управляет скачиванием модели и не выбирает UI-состояние. Его задача:
 * открыть Vosk runtime, прочитать PCM-часть файла и вернуть текст.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxVoskSpeechTranscriber(
    private val modelDirectoryPath: String,
    private val grammarJson:        String? = null,
) {

    /**
     * Расшифровывает Vosk-ready WAV.
     *
     * Метод выполняет работу на [Dispatchers.IO], потому что открывает модель,
     * читает файл и вызывает native Vosk recognizer.
     *
     * @since 0.2
     */
    suspend fun transcribe(
        recordedVoice: KsenaxVoskRecordedVoiceFile,
    ): KsenaxVoskTranscriptionResult {
        return withContext(Dispatchers.IO) {
            val modelDirectory = File(modelDirectoryPath)

            require(modelDirectory.isDirectory) {
                "Vosk model directory does not exist: $modelDirectoryPath"
            }

            var model: Model? = null
            var recognizer: Recognizer? = null

            try {
                model = Model(modelDirectory.absolutePath)
                recognizer = createRecognizer(
                    model = model,
                    sampleRateHz = recordedVoice.sampleRateHz,
                )

                val rawSegments = mutableListOf<String>()
                val segmentTexts = mutableListOf<String>()
                val buffer = ByteArray(READ_BUFFER_SIZE_BYTES)

                recordedVoice.openPcmInputStream().use { inputStream ->
                    while (true) {
                        coroutineContext.ensureActive()

                        val readBytes = inputStream.read(buffer)

                        if (readBytes == END_OF_STREAM) {
                            break
                        }

                        if (readBytes > 0 && recognizer.acceptWaveForm(buffer, readBytes)) {
                            val segmentJson = recognizer.result
                            rawSegments += segmentJson
                            extractText(segmentJson)
                                .takeIf(String::isNotBlank)
                                ?.let(segmentTexts::add)
                        }
                    }
                }

                val finalJson = recognizer.finalResult
                val finalText = extractText(finalJson)
                val text = (segmentTexts + finalText)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString(separator = " ")

                KsenaxVoskTranscriptionResult(
                    text               = text,
                    rawSegmentsJson    = rawSegments,
                    finalJson          = finalJson,
                    modelDirectoryPath = modelDirectory.absolutePath,
                )
            } catch (exception: CancellationException) {
                throw exception
            } finally {
                recognizer?.close()
                model?.close()
            }
        }
    }

    private fun createRecognizer(
        model:        Model,
        sampleRateHz: Int,
    ): Recognizer {
        return if (grammarJson.isNullOrBlank()) {
            Recognizer(model, sampleRateHz.toFloat())
        } else {
            Recognizer(model, sampleRateHz.toFloat(), grammarJson)
        }
    }

    private fun extractText(json: String): String {
        return runCatching {
            JSONObject(json).optString(VOSK_TEXT_FIELD)
        }.getOrDefault("")
            .trim()
    }

    companion object {
        private const val END_OF_STREAM = -1
        private const val READ_BUFFER_SIZE_BYTES = 4_096
        private const val VOSK_TEXT_FIELD = "text"

        /**
         * Собирает grammar JSON для командного режима Vosk.
         *
         * Такой режим полезен для коротких агентных команд: recognizer выбирает
         * из ожидаемых фраз и `[unk]`, а не пытается диктовать весь русский язык.
         *
         * @since 0.2
         */
        fun buildGrammarJson(
            phrases:        List<String>,
            includeUnknown: Boolean = true,
        ): String {
            val normalizedPhrases = phrases
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
            val grammarItems = if (includeUnknown) {
                normalizedPhrases + VOSK_UNKNOWN_TOKEN
            } else {
                normalizedPhrases
            }

            return JSONArray(grammarItems).toString()
        }

        private const val VOSK_UNKNOWN_TOKEN = "[unk]"
    }
}
