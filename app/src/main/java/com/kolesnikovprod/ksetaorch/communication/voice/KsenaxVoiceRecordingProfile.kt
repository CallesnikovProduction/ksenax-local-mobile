package com.kolesnikovprod.ksetaorch.communication.voice

import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxRecordedVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoskRecordedVoiceFile
import java.io.File

/**
 * Профиль записи голосовой команды.
 *
 * UI выбирает модель транскрибации, а voice-контур переводит этот выбор в
 * профиль записи. Communication-слой не импортирует UI enum, чтобы не связывать
 * runtime-логику с presentation.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxVoiceRecordingProfile {
    /**
     * Обычный WAV для multimodal-транскрибации через Gemma/LiteRT-LM.
     */
    Gemma,

    /**
     * WAV с Vosk STT-семантикой: 16 kHz, mono, PCM 16-bit little-endian.
     */
    Vosk,
}

/**
 * Единый результат записи, независимо от выбранного профиля.
 *
 * Старый Gemma-путь может взять [asGenericRecordedVoiceMessage]. Vosk-путь
 * может проверить ветку [Vosk] и открыть PCM-stream через
 * [KsenaxVoskRecordedVoiceFile.openPcmInputStream].
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxRecordedVoiceInput {
    val profile: KsenaxVoiceRecordingProfile
    val file: File
    val sampleRateHz: Int
    val channelCount: Int
    val durationMillis: Long
    val voiceLevels: List<Float>

    fun asGenericRecordedVoiceMessage(): KsenaxRecordedVoiceMessage

    data class Gemma(
        val message: KsenaxRecordedVoiceMessage,
    ) : KsenaxRecordedVoiceInput {
        override val profile: KsenaxVoiceRecordingProfile = KsenaxVoiceRecordingProfile.Gemma
        override val file: File = message.file
        override val sampleRateHz: Int = message.sampleRateHz
        override val channelCount: Int = message.channelCount
        override val durationMillis: Long = message.durationMillis
        override val voiceLevels: List<Float> = message.voiceLevels

        override fun asGenericRecordedVoiceMessage(): KsenaxRecordedVoiceMessage {
            return message
        }
    }

    data class Vosk(
        val message: KsenaxVoskRecordedVoiceFile,
    ) : KsenaxRecordedVoiceInput {
        override val profile: KsenaxVoiceRecordingProfile = KsenaxVoiceRecordingProfile.Vosk
        override val file: File = message.wavFile
        override val sampleRateHz: Int = message.sampleRateHz
        override val channelCount: Int = message.channelCount
        override val durationMillis: Long = message.durationMillis
        override val voiceLevels: List<Float> = message.voiceLevels

        override fun asGenericRecordedVoiceMessage(): KsenaxRecordedVoiceMessage {
            return message.toGenericRecordedVoiceMessage()
        }
    }
}
