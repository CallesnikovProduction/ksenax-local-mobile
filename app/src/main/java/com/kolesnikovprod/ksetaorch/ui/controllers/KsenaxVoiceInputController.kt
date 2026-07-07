package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.voice.KsenaxRecordedVoiceInput
import com.kolesnikovprod.ksetaorch.communication.voice.vosk.KsenaxVoskSpeechTranscriber
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxGemma4E2BInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxVoskRuSmallInstallUseCase
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxTranscribingModel

class KsenaxVoiceInputController(
    private val voskInstallUseCase : KsenaxVoskRuSmallInstallUseCase,
    private val gemmaInstallUseCase: KsenaxGemma4E2BInstallUseCase,
    private val gemmaModelSession  : KsenaxModelSession,
) {
    suspend fun resolveInputText(
        recordedVoice: KsenaxRecordedVoiceInput
    ): String {
        return when (recordedVoice) {
            is KsenaxRecordedVoiceInput.Gemma -> {
                gemmaModelSession.transcribe(
                    KsenaxVoiceMessage(
                        file = recordedVoice.file,
                        sampleRateHz = recordedVoice.sampleRateHz,
                        channelCount = recordedVoice.channelCount,
                        durationMillis = recordedVoice.durationMillis,
                    ),
                ).text
            }
            is KsenaxRecordedVoiceInput.Vosk -> {
                val transcription = KsenaxVoskSpeechTranscriber(
                    modelDirectoryPath = voskInstallUseCase.getInstalledPath()
                ).transcribe(recordedVoice.message)

                transcription.text
            }
        }
    }

    fun voiceOutputDirectoryPathFor(model: KsenaxTranscribingModel): String {
        return when (model) {
            KsenaxTranscribingModel.Gemma -> gemmaInstallUseCase.getGemma4E2BSavedVoicesDirPath()
            KsenaxTranscribingModel.Vosk  -> voskInstallUseCase.getVoskSavedVoicesDirPath()
        }
    }
}
