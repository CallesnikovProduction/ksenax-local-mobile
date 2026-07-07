package com.kolesnikovprod.ksetaorch.ui.main.settings

import com.kolesnikovprod.ksetaorch.communication.voice.KsenaxVoiceRecordingProfile

enum class KsenaxTranscribingModel(
    val title: String,
    val experimentalLabel: String?,
    val description: String,
) {
    Gemma(
        title             =
            "Gemma Speech-to-Text",
        experimentalLabel =
            null,
        description       =
            "Используется для расшифровки голосовых сообщений через локальную мультимодальную модель.",
    ),
    Vosk(
        title             =
            "Vosk",
        experimentalLabel =
            "(Experimental)",
        description       =
            "Компромисс для русской речи. " +
                    "Лёгкая offline-модель для отправки агентных команд в оркестратор.",
    ),
}

fun KsenaxTranscribingModel.toVoiceRecordingProfile(): KsenaxVoiceRecordingProfile {
    return when (this) {
        KsenaxTranscribingModel.Gemma -> KsenaxVoiceRecordingProfile.Gemma
        KsenaxTranscribingModel.Vosk  -> KsenaxVoiceRecordingProfile.Vosk
    }
}
