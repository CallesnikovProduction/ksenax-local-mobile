package com.kolesnikovprod.ksetaorch.communication.voice.utils

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * DTO/метаданные записанного голосового сообщения (результата записи).
 *
 * @property file файл `.wav`, куда реально записали голос
 * @property sampleRateHz частота дискретизации
 * @property channelCount каналы
 * @property durationMillis продолжительность записи
 * @property voiceLevels список уровней для waveform/UI
 *
 * @author Stephan Kolesnikov
 * @since 0.2
 * @see KsenaxVoiceMessageRecorder
 */
data class KsenaxRecordedVoiceMessage(
    val file:           File,
    val sampleRateHz:   Int,
    val channelCount:   Int,
    val durationMillis: Long,
    val voiceLevels:    List<Float> = emptyList(),
)


/**
 * Записывает короткую голосовую команду в WAV для локального модельного входа.
 *
 * Формат файла:
 *
 * ```text
 * WAV
 * mono
 * 16 kHz
 * PCM 16-bit little-endian
 * max 30 seconds
 * ```
 *
 * Класс не хранит Android [android.content.Context]. Он открывает [AudioRecord]
 * на одну сессию, записывает WAV-файл и освобождает recorder. Ошибки чтения
 * Android audio API выходят исключениями, чтобы контроллер показал ошибку
 * вместо зависания.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxVoiceMessageRecorder {

    /**
     * Записывает голос в WAV-файл.
     *
     * [shouldStop] вызывается в цикле записи. Для push-to-talk будущий UI сможет
     * вернуть `true`, когда пользователь отпустил кнопку микрофона. Если callback
     * всегда возвращает `false`, запись завершится по [maxDuration] или раньше,
     * если Android audio API вернёт ошибку чтения.
     *
     * @param outputFile куда сохранить записанный WAV
     * @param maxDuration максимальная длина записи
     * @param shouldStop callback для остановки
     * @param onVoiceLevel callback с нормализованным уровнем громкости `0f..1f`
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun recordToWav(
        outputFile:   File,
        maxDuration:  Duration = MAX_DURATION,
        shouldStop:   () -> Boolean = { false },
        onVoiceLevel: (Float) -> Unit = {},
    ): KsenaxRecordedVoiceMessage {
        require(maxDuration > Duration.ZERO) {
            "maxDuration must be positive."
        }

        val safeDurationMillis = min(
            maxDuration.inWholeMilliseconds,
            MAX_DURATION.inWholeMilliseconds,
        ).coerceAtLeast(1L)

        // Микрофон + файловая запись рулятся в отдельный поток, не в главном
        return withContext(Dispatchers.IO) {

            outputFile.parentFile?.mkdirs()

            // Минимальный размер буфера для формата
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_ENCODING,
            )

            check(minBufferSize > 0) {
                createAudioReadErrorMessage(minBufferSize)
            }

            // AudioRecord получает достаточно большой внутренний буфер, а читаем мы
            // небольшими порциями, чтобы UI получал частые обновления уровня голоса.
            val recorderBufferSize = maxOf(minBufferSize, SAMPLE_RATE_HZ)

            // Сколько PCM-байт можно записать максимум.
            // Для 30 секунд: 30000 * 16000 * 2 / 1000 = 960k bytes < 1 MB
            val maxBytes = ((safeDurationMillis * SAMPLE_RATE_HZ
                    * CHANNEL_COUNT * BYTES_PER_SAMPLE) / 1000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()

            // Пишем PCM в память, а потом целиком сохранять будем на диск
            val recordedBytes = ByteArrayOutputStream(maxBytes)

            val voiceLevels = mutableListOf<Float>()

            // Временный read-буфер: 2048 bytes = примерно 64 ms PCM 16 kHz mono.
            val buffer = ByteArray(READ_BUFFER_SIZE_BYTES)

            // Низкоуровневый Android API для записи звука.
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setEncoding(AUDIO_ENCODING)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(recorderBufferSize)
                .build()

            try {
                // Пошла запись
                recorder.startRecording()

                val startedAtMillis = SystemClock.elapsedRealtime()

                while (
                    // НЕ записали слишком много байт
                    recordedBytes.size() < maxBytes &&
                    // Внешний UI попросил остановиться
                    !shouldStop() &&
                    // Не вышли за время
                    SystemClock.elapsedRealtime() - startedAtMillis < safeDurationMillis
                ) {
                    // Проверка, не отменена ли корутина (пользователь может уйти)
                    coroutineContext.ensureActive()

                    val bytesLeft = maxBytes - recordedBytes.size()

                    // положительное число байт, 0, отрицательный код ошибки
                    val readBytes = recorder.read(
                        buffer,
                        0,
                        min(buffer.size, bytesLeft),
                    )

                    // Пишем только положительные значения
                    if (readBytes > 0) {
                        recordedBytes.write(buffer, 0, readBytes)
                        val nextVoiceLevel = calculateVoiceLevel(buffer, readBytes)
                        voiceLevels += nextVoiceLevel
                        onVoiceLevel(nextVoiceLevel)
                    } else if (readBytes == 0) {
                        delay(READ_IDLE_DELAY_MILLIS)
                    } else {
                        throw IllegalStateException(createAudioReadErrorMessage(readBytes))
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }

            val pcmBytes = recordedBytes.toByteArray()
            outputFile.outputStream().use { outputStream ->
                outputStream.write(createWavHeader(pcmBytes.size))
                outputStream.write(pcmBytes)
            }

            KsenaxRecordedVoiceMessage(
                file           = outputFile,
                sampleRateHz   = SAMPLE_RATE_HZ,
                channelCount   = CHANNEL_COUNT,
                durationMillis = pcmBytes.size.toLong() * 1000L /
                    (SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE),
                voiceLevels    = voiceLevels.toList(),
            )
        }
    }

    /**
     * Считает RMS-уровень для PCM 16-bit little-endian и переводит его в диапазон UI.
     *
     * Небольшой noise floor убирает дрожание от комнатного шума, gain делает речь
     * визуально заметной без необходимости кричать в микрофон.
     *
     * @param buffer сколько байтов PCM
     * @param readBytes сколько байт в нём реально валидны
     *
     * @since 0.2
     */
    private fun calculateVoiceLevel(
        buffer:    ByteArray,
        readBytes: Int,
    ): Float {
        /*
        * RMS = Root Mean Square: sqrt((x1^2 + x2^2 + ... + xn^2) / n)
        * Не может быть среднее, потому что звук колеблется около нуля,
        * а квадраты дают реальную энергию сигнала
        * */
        var sumSquares = 0.0
        var sampleCount = 0

        // Идём по 2 байта
        var index = 0
        while (index + 1 < readBytes) {
            // нижний байт - 0..255
            val lowByte = buffer[index].toInt() and 0xFF
            // верхний байт - -127..128
            val highByte = buffer[index + 1].toInt()
            // Собирается 16-битное число
            val sample = ((highByte shl 8) or lowByte).toShort().toInt()
            // Нормализация в [-1, 1]
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

    /**
     * WAV header — это первые 44 байта в начале `.wav` файла.
     *
     * Складывается путём фиксированных блоков:
     * ```text
     * RIFF chunk:
     * "RIFF"        4 bytes
     * file size     4 bytes
     * "WAVE"        4 bytes
     *               = 12 bytes
     *
     * fmt chunk:
     * "fmt "        4 bytes
     * fmt size      4 bytes
     * audio format  2 bytes
     * channels      2 bytes
     * sample rate   4 bytes
     * byte rate     4 bytes
     * block align   2 bytes
     * bits/sample   2 bytes
     *               = 24 bytes
     *
     * data chunk header:
     * "data"        4 bytes
     * data size     4 bytes
     *               = 8 bytes
     * ```
     * @since 0.2
     */
    private fun createWavHeader(pcmDataSize: Int): ByteArray {
        // Сколько байт аудио идёт за 1 секунду
        val byteRate = SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE
        // Сколько байт занимает один “кадр” аудио
        val blockAlign = CHANNEL_COUNT * BYTES_PER_SAMPLE
        // размер всего WAV-файла минус первые 8 байт (WAV/RIFF исторически так устроен)
        val totalDataLength = pcmDataSize + WAV_STANDARD_HEADER_SIZE - 8

        return ByteBuffer.allocate(WAV_STANDARD_HEADER_SIZE) // 44 ячейки
            .order(ByteOrder.LITTLE_ENDIAN)                      // Младший байт первым (wav)
            .put("RIFF".toByteArray(Charsets.US_ASCII))          // подпись RIFF
            .putInt(totalDataLength)                           // размер данных RIFF
            .put("WAVE".toByteArray(Charsets.US_ASCII))          // внутри RIFF лежит WAV
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)                                  // для обычного PCM = 16
            .putShort(1)                                 // PCM, несжатый звук
            .putShort(CHANNEL_COUNT.toShort())
            .putInt(SAMPLE_RATE_HZ)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(BITS_PER_SAMPLE.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))    // пишем начало секции с реальными
            .putInt(pcmDataSize)
            .array() // готовые 44 байта
    }

    /**
     * Генерация ошибки чтения [AudioRecord], основываясь на местных константах
     * @since 0.2
     */
    private fun createAudioReadErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION ->
                "Android could not read audio from microphone: AudioRecord is not ready to read."
            AudioRecord.ERROR_BAD_VALUE ->
                "Android Could not read audio from microphone: Incorrect audio buffer size."
            AudioRecord.ERROR_DEAD_OBJECT ->
                "Android stopped audio recording: System AudioRecord was closed."
            AudioRecord.ERROR ->
                "Android couldn't read the sound from the microphone."
            else ->
                "Android Could not read audio from microphone: error code $errorCode."
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ           = 16_000
        const val CHANNEL_COUNT            = 1
        const val CHANNEL_CONFIG           = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_ENCODING           = AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE          = 16
        const val BYTES_PER_SAMPLE         = BITS_PER_SAMPLE / 8
        const val WAV_STANDARD_HEADER_SIZE = 44
        const val READ_BUFFER_SIZE_BYTES   = 2_048
        const val READ_IDLE_DELAY_MILLIS   = 10L
        const val PCM_16_MAX_ABS_VALUE     = 32_768.0
        const val VOICE_NOISE_FLOOR        = 0.015
        const val VOICE_LEVEL_GAIN         = 8.0
        val MAX_DURATION: Duration         = 30.seconds
    }
}
