package com.kolesnikovprod.ksetaorch.communication.voice.utils

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Метаданные WAV-файла, записанного специально под Vosk STT.
 *
 * Vosk-ветке нужен не Gemma audio adapter, а предсказуемый speech-recognition
 * файл: mono, 16 kHz, PCM 16-bit little-endian. Сам WAV хранит 44-byte header,
 * но recognizer-слой может читать PCM-часть через [openPcmInputStream].
 *
 * @property wavFile файл `.wav` с RIFF/WAVE header.
 * @property sampleRateHz частота, которую нужно передавать в Vosk recognizer.
 * @property channelCount количество каналов. Для Vosk-записи тут всегда mono.
 * @property bitsPerSample разрядность PCM sample.
 * @property durationMillis длительность записи.
 * @property wavHeaderSizeBytes размер WAV header перед PCM-данными.
 * @property voiceLevels уровни сигнала для waveform/UI.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxVoskRecordedVoiceFile(
    val wavFile:            File,
    val sampleRateHz:       Int,
    val channelCount:       Int,
    val bitsPerSample:      Int,
    val durationMillis:     Long,
    val wavHeaderSizeBytes: Int,
    val voiceLevels:        List<Float> = emptyList(),
) {

    /**
     * Открывает поток PCM-данных без WAV header.
     *
     * Это удобно для Vosk stream recognition: WAV-файл можно хранить на диске,
     * а в recognizer передавать только аудиосэмплы после первых 44 байт.
     *
     * Вызывающий код владеет возвращённым [InputStream] и должен закрыть его.
     *
     * @since 0.2
     */
    fun openPcmInputStream(): InputStream {
        val inputStream = wavFile.inputStream().buffered()
        val skippedBytes = inputStream.skip(wavHeaderSizeBytes.toLong())

        if (skippedBytes != wavHeaderSizeBytes.toLong()) {
            inputStream.close()
            error("Vosk WAV file is too short: cannot skip WAV header.")
        }

        return inputStream
    }

    /**
     * Переводит Vosk-specific результат в общий DTO текущего voice-модуля.
     *
     * Нужен только для мест, где UI уже ожидает [KsenaxRecordedVoiceMessage].
     * Для Vosk STT лучше сохранять исходный [KsenaxVoskRecordedVoiceFile],
     * потому что в нём явно указан размер WAV header.
     *
     * @since 0.2
     */
    fun toGenericRecordedVoiceMessage(): KsenaxRecordedVoiceMessage {
        return KsenaxRecordedVoiceMessage(
            file           = wavFile,
            sampleRateHz   = sampleRateHz,
            channelCount   = channelCount,
            durationMillis = durationMillis,
            voiceLevels    = voiceLevels,
        )
    }
}

/**
 * Записывает короткий WAV-файл в формате, который подходит Vosk STT.
 *
 * Этот класс специально не импортирует `org.vosk.*`: он отвечает только за
 * audio capture и файл. Vosk runtime/recognizer должен жить следующим слоем.
 *
 * Формат записи:
 *
 * ```text
 * Audio source: VOICE_RECOGNITION
 * WAV
 * mono
 * 16 kHz
 * PCM 16-bit little-endian
 * default max: 30 seconds
 * ```
 *
 * Почему это не тот же класс, что Gemma recorder: Gemma-ветка хранит общий
 * voice command WAV, а Vosk-ветка делает файл с явной семантикой STT-входа и
 * helper-ом для чтения PCM без WAV header.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxVoskVoiceFileRecorder {

    fun createVoskVoiceOutputFile(voskVoicesDirPath: String): File {
        return File(
            voskVoicesDirPath,
            "vosk-command-${System.currentTimeMillis()}-${UUID.randomUUID()}.wav",
        )
    }

    /**
     * Записывает Vosk-ready WAV-файл.
     *
     * [shouldStop] позволяет использовать push-to-talk: UI ставит `true`,
     * recorder мягко завершает цикл, пишет WAV header и освобождает микрофон.
     *
     * @param outputFile итоговый `.wav` файл.
     * @param maxDuration максимальная длительность записи.
     * @param shouldStop внешний сигнал мягкой остановки.
     * @param onVoiceLevel callback с нормализованным уровнем громкости `0f..1f`.
     *
     * @since 0.2
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun recordToVoskWav(
        outputFile:   File,
        maxDuration:  Duration = MAX_DURATION,
        shouldStop:   () -> Boolean = { false },
        onVoiceLevel: (Float) -> Unit = {},
    ): KsenaxVoskRecordedVoiceFile {
        require(maxDuration > Duration.ZERO) {
            "maxDuration must be positive."
        }

        val safeDurationMillis = min(
            maxDuration.inWholeMilliseconds,
            MAX_DURATION.inWholeMilliseconds,
        ).coerceAtLeast(1L)

        return withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()

            val minBufferSize = AudioRecord.getMinBufferSize(
                VOSK_SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_ENCODING,
            )

            check(minBufferSize > 0) {
                createAudioReadErrorMessage(minBufferSize)
            }

            val recognizerChunkBytes =
                (VOSK_SAMPLE_RATE_HZ * VOSK_RECOGNIZER_CHUNK_SECONDS)
                    .roundToInt() * BYTES_PER_SAMPLE
            val recorderBufferSize = maxOf(
                minBufferSize,
                recognizerChunkBytes,
            )
            val maxBytes = ((safeDurationMillis * VOSK_SAMPLE_RATE_HZ
                    * VOSK_CHANNEL_COUNT * BYTES_PER_SAMPLE) / 1000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val recordedBytes = ByteArrayOutputStream(maxBytes)
            val voiceLevels = mutableListOf<Float>()
            val buffer = ByteArray(recognizerChunkBytes)
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(VOSK_SAMPLE_RATE_HZ)
                        .setEncoding(AUDIO_ENCODING)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(recorderBufferSize)
                .build()

            try {
                recorder.startRecording()

                val startedAtMillis = SystemClock.elapsedRealtime()

                while (
                    recordedBytes.size() < maxBytes &&
                    !shouldStop() &&
                    SystemClock.elapsedRealtime() - startedAtMillis < safeDurationMillis
                ) {
                    coroutineContext.ensureActive()

                    val bytesLeft = maxBytes - recordedBytes.size()
                    val readBytes = recorder.read(
                        buffer,
                        0,
                        min(buffer.size, bytesLeft),
                    )

                    when {
                        readBytes > 0 -> {
                            recordedBytes.write(buffer, 0, readBytes)
                            val nextVoiceLevel = calculateVoiceLevel(buffer, readBytes)
                            voiceLevels += nextVoiceLevel
                            onVoiceLevel(nextVoiceLevel)
                        }
                        readBytes == 0 -> delay(READ_IDLE_DELAY_MILLIS)
                        else -> throw IllegalStateException(
                            createAudioReadErrorMessage(readBytes)
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }

            val pcmBytes = recordedBytes.toByteArray()
            outputFile.outputStream().use { outputStream ->
                outputStream.write(createWavHeader(pcmBytes.size))
                outputStream.write(pcmBytes)
            }

            KsenaxVoskRecordedVoiceFile(
                wavFile            = outputFile,
                sampleRateHz       = VOSK_SAMPLE_RATE_HZ,
                channelCount       = VOSK_CHANNEL_COUNT,
                bitsPerSample      = BITS_PER_SAMPLE,
                durationMillis     = pcmBytes.size.toLong() * 1000L /
                    (VOSK_SAMPLE_RATE_HZ * VOSK_CHANNEL_COUNT * BYTES_PER_SAMPLE),
                wavHeaderSizeBytes = VOSK_WAV_HEADER_SIZE_BYTES,
                voiceLevels        = voiceLevels.toList(),
            )
        }
    }

    private fun calculateVoiceLevel(
        buffer:    ByteArray,
        readBytes: Int,
    ): Float {
        var sumSquares = 0.0
        var sampleCount = 0

        var index = 0
        while (index + 1 < readBytes) {
            val lowByte = buffer[index].toInt() and 0xFF
            val highByte = buffer[index + 1].toInt()
            val sample = ((highByte shl 8) or lowByte).toShort().toInt()
            val normalizedSample = sample / PCM_16_MAX_ABS_VALUE

            sumSquares += normalizedSample * normalizedSample
            sampleCount += 1
            index += BYTES_PER_SAMPLE
        }

        if (sampleCount == 0) {
            return 0f
        }

        val rms = sqrt(sumSquares / sampleCount)
        return ((rms - VOICE_NOISE_FLOOR) * VOICE_LEVEL_GAIN)
            .toFloat()
            .coerceIn(0f, 1f)
    }

    private fun createWavHeader(pcmDataSize: Int): ByteArray {
        val byteRate = VOSK_SAMPLE_RATE_HZ * VOSK_CHANNEL_COUNT * BYTES_PER_SAMPLE
        val blockAlign = VOSK_CHANNEL_COUNT * BYTES_PER_SAMPLE
        val totalDataLength = pcmDataSize + VOSK_WAV_HEADER_SIZE_BYTES - 8

        return ByteBuffer.allocate(VOSK_WAV_HEADER_SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(totalDataLength)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1)
            .putShort(VOSK_CHANNEL_COUNT.toShort())
            .putInt(VOSK_SAMPLE_RATE_HZ)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(BITS_PER_SAMPLE.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(pcmDataSize)
            .array()
    }

    private fun createAudioReadErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION ->
                "Android could not read Vosk audio: AudioRecord is not ready."
            AudioRecord.ERROR_BAD_VALUE ->
                "Android could not read Vosk audio: invalid audio buffer size."
            AudioRecord.ERROR_DEAD_OBJECT ->
                "Android stopped Vosk audio recording: AudioRecord was closed."
            AudioRecord.ERROR ->
                "Android could not read Vosk audio from microphone."
            else ->
                "Android could not read Vosk audio from microphone: error code $errorCode."
        }
    }

    companion object {
        const val VOSK_SAMPLE_RATE_HZ = 16_000
        const val VOSK_CHANNEL_COUNT = 1
        const val VOSK_WAV_HEADER_SIZE_BYTES = 44
        const val VOSK_RECOGNIZER_CHUNK_SECONDS = 0.2f
        const val VOSK_AUDIO_ENCODING_BITS = 16

        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BITS_PER_SAMPLE = VOSK_AUDIO_ENCODING_BITS
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        private const val READ_IDLE_DELAY_MILLIS = 10L
        private const val PCM_16_MAX_ABS_VALUE = 32_768.0
        private const val VOICE_NOISE_FLOOR = 0.015
        private const val VOICE_LEVEL_GAIN = 8.0

        val MAX_DURATION: Duration = 30.seconds
    }
}
