package com.kolesnikovprod.ksetaorch.communication.voice

import android.Manifest
import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxRecordedVoiceMessage
import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoskVoiceFileRecorder
import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoiceMessagePlayer
import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoiceMessageRecorder
import com.kolesnikovprod.ksetaorch.communication.voice.utils.KsenaxVoicePlaybackResult
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Координирует локальную запись голоса, состояние воспроизведения и live-уровень сигнала.
 *
 * Коротко: voice input adapter, ведь он создаёт короткие WAV-файлы,
 * публикует уровни сигнала и хранит состояние одного активного воспроизведения.
 * Транскрибация и tool execution остаются в модельном слое.
 *
 * @property voiceRecorder отвечает за запись WAV
 * @property voskVoiceRecorder отвечает за запись WAV под Vosk STT
 * @property voicePlayer отвечает за проигрыш WAV
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 * @see KsenaxVoiceMessageRecorder
 * @see KsenaxVoiceMessagePlayer
 */
class KsenaxVoiceController(
    private val voiceRecorder:     KsenaxVoiceMessageRecorder  = KsenaxVoiceMessageRecorder(),
    private val voskVoiceRecorder: KsenaxVoskVoiceFileRecorder = KsenaxVoskVoiceFileRecorder(),
    private val voicePlayer:       KsenaxVoiceMessagePlayer    = KsenaxVoiceMessagePlayer(),
) {

    /**
     * Основная корутина записи
     *
     * @since 0.2
     */
    private var voiceRecordingJob:         Job? = null

    /**
     * Отдельная корутина, каждый 100 мс которая обновляет длительность записи
     * @since 0.2
     */
    private var voiceRecordingDurationJob: Job? = null

    /**
     * Корутина запуска playback-а. Нужна, потому что `voicePlayer.play()` занят
     * на [kotlinx.coroutines.Dispatchers.IO]
     * @since 0.2
     */
    private var voicePlaybackStartJob:     Job? = null

    /**
     * Корутина, которая каждые 50 ms спрашивает у player-а текущую позицию:
     * @since 0.2
     */
    private var voicePlaybackProgressJob:  Job? = null
    private val shouldStopVoiceRecording        = AtomicBoolean(false)

    /**
     * Версионирование playback-запроса
     * @since 0.2
     */
    private val voicePlaybackRequestVersion     = AtomicLong(0L)

    /**
     * Горячий поток состояния с текущим значением. Всегда имеет значение.
     * @since 0.2
     */
    private val _snapshot                       = MutableStateFlow(KsenaxVoiceSnapshot())

    /**
     * Широковещательные события внутри приложения (например, отложенное проигрывание)
     * @since 0.2
     */
    private val _voiceLevels                    = MutableSharedFlow<Float>(
        extraBufferCapacity = VOICE_LEVEL_BUFFER_CAPACITY,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST,
    )

    val snapshots: StateFlow<KsenaxVoiceSnapshot> = _snapshot.asStateFlow()

    val voiceLevels: SharedFlow<Float>            = _voiceLevels.asSharedFlow()

    fun currentSnapshot(): KsenaxVoiceSnapshot    = _snapshot.value

    /**
     * Форматирование длительности голосового
     * @since 0.2
     */
    fun formatVoiceDuration(durationMillis: Long): String {
        val totalSeconds = ((durationMillis + 999L) / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L

        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun createVoiceOutputFile(
        savedVoicesDirPath: String,
        profile:            KsenaxVoiceRecordingProfile = KsenaxVoiceRecordingProfile.Gemma,
    ): File {
        return when (profile) {
            KsenaxVoiceRecordingProfile.Gemma -> File(
                savedVoicesDirPath,
                "command-${System.currentTimeMillis()}-${UUID.randomUUID()}.wav",
            )

            KsenaxVoiceRecordingProfile.Vosk ->
                voskVoiceRecorder.createVoskVoiceOutputFile(savedVoicesDirPath)
        }
    }

    /**
     * Начинает WAV-запись. Вызывающий код должен преждевременно проверить
     * [Manifest.permission.RECORD_AUDIO] перед тем, как вызывать этот метод.
     *
     * Аннотации перед сигнатурой удаляют лишь возмущения IDE (Android Studio),
     * но ответственность за разрешения на запись остаётся за вызывающим кодом.
     *
     * @param coroutineScope scope, в котором живёт запись
     * @param outputFile куда писать WAV (директория)
     * @param onRecorded suspend-callback после успешной записи
     * @param onFailure callback ошибки
     *
     * @since 0.2
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @SuppressLint("MissingPermission")
    fun startRecording(
        coroutineScope: CoroutineScope,
        outputFile    : File,
        onRecorded    : suspend (KsenaxRecordedVoiceMessage) -> Unit,
        onFailure     : (String) -> Unit,
    ) {
        startRecording(
            coroutineScope    = coroutineScope,
            outputFile        = outputFile,
            recordingProfile  = KsenaxVoiceRecordingProfile.Gemma,
            onRecorded        = { recordedInput ->
                onRecorded(recordedInput.asGenericRecordedVoiceMessage())
            },
            onFailure         = onFailure,
        )
    }

    /**
     * Начинает запись с явным профилем транскрибации.
     *
     * [KsenaxVoiceRecordingProfile.Gemma] пишет обычный WAV для multimodal-
     * транскрибации через Gemma. [KsenaxVoiceRecordingProfile.Vosk] пишет
     * Vosk-ready WAV, у которого можно открыть PCM-часть без header-а.
     *
     * @param recordingProfile профиль записи, выбранный UI/настройками.
     * @param onRecorded callback с unified voice input.
     *
     * @since 0.2
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @SuppressLint("MissingPermission")
    fun startRecording(
        coroutineScope:   CoroutineScope,
        outputFile:       File,
        recordingProfile: KsenaxVoiceRecordingProfile,
        onRecorded:       suspend (KsenaxRecordedVoiceInput) -> Unit,
        onFailure:        (String) -> Unit,
    ) {
        // Если запись уже идёт, то новый старт игнорируется
        if (voiceRecordingJob?.isActive == true) {
            return
        }

        // Проигрывание старого playback нужно остановить
        stopPlayback()
        shouldStopVoiceRecording.set(false)
        updateSnapshot {
            copy(
                voiceLevel              = 0f,
                isRecording             = true,
                isProcessingVoice       = false,
                recordingProfile        = recordingProfile,
                recordingDurationMillis = 0L,
            )
        }
        startVoiceRecordingDurationUpdates(
            coroutineScope  = coroutineScope,
            startedAtMillis = SystemClock.elapsedRealtime(),
        )

        voiceRecordingJob = coroutineScope.launch {
            val recordedVoice = try {
                recordVoiceInput(
                    outputFile       = outputFile,
                    recordingProfile = recordingProfile,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (exception: Exception) {
                onFailure(
                    exception.message ?: "Failed to record a new voice message",
                )
                null
            } finally {
                shouldStopVoiceRecording.set(false)
                voiceRecordingJob = null
                stopVoiceRecordingDurationUpdates()
                updateSnapshot {
                    copy(
                        voiceLevel              = 0f,
                        isRecording             = false,
                        recordingProfile        = null,
                        recordingDurationMillis = 0L,
                    )
                }
            }

            handleRecordedVoice(
                recordedVoice = recordedVoice,
                onRecorded    = onRecorded,
                onFailure     = onFailure,
            )
        }
    }

    /**
     * Мягкая остановка записи голосового сообщения.
     * @since 0.2
     */
    fun stopRecording() {
        shouldStopVoiceRecording.set(true)
        stopVoiceRecordingDurationUpdates()
        updateSnapshot {
            copy(
                voiceLevel        = 0f,
                isRecording       = false,
                isProcessingVoice = true,
                recordingProfile  = null,
            )
        }
    }

    /**
     * State machine playback. Этот метод — одна кнопка "`play` / `pause` / `resume`".
     *
     * @since 0.2
     */
    fun togglePlayback(
        coroutineScope: CoroutineScope,
        voiceFilePath:  String,
        onFailure:     (String) -> Unit,
    ) {
        val playbackResult = when {
            _snapshot.value.playingVoiceFilePath == voiceFilePath -> {
                voicePlayer.pause().also { result ->
                    if (result is KsenaxVoicePlaybackResult.Success) {
                        updateSnapshot {
                            copy(
                                playingVoiceProgressMillis = voicePlayer.currentPositionMillis(),
                                playingVoiceFilePath       = null,
                                pausedVoiceFilePath        = voiceFilePath,
                            )
                        }
                        // progress job больше не нужен, потому что позиция не движется.
                        stopVoicePlaybackProgressUpdates()
                    }
                }
            }

            _snapshot.value.pausedVoiceFilePath == voiceFilePath -> {
                voicePlayer.resume().also { result ->
                    if (result is KsenaxVoicePlaybackResult.Success) {
                        updateSnapshot {
                            copy(
                                playingVoiceFilePath = voiceFilePath,
                                pausedVoiceFilePath  = null,
                            )
                        }
                        startVoicePlaybackProgressUpdates(
                            coroutineScope = coroutineScope,
                            voiceFilePath  = voiceFilePath,
                        )
                    }
                }
            }

            else -> {
                val playbackRequestVersion = voicePlaybackRequestVersion.incrementAndGet()
                voicePlaybackStartJob?.cancel()
                stopVoicePlaybackProgressUpdates()
                updateSnapshot {
                    copy(
                        playingVoiceFilePath       = null,
                        pausedVoiceFilePath        = null,
                        playingVoiceProgressMillis = 0L,
                    )
                }

                // асинхронный запуск плеера
                voicePlaybackStartJob = coroutineScope.launch {
                    val playbackResult = voicePlayer.play(
                        voiceFile    = File(voiceFilePath),
                        onCompletion = {
                            coroutineScope.launch {
                                clearVoicePlaybackState(
                                    voiceFilePath = voiceFilePath,
                                )
                            }
                        },
                        onError      = { message ->
                            coroutineScope.launch {
                                clearVoicePlaybackState(
                                    voiceFilePath = voiceFilePath,
                                )
                                onFailure(message)
                            }
                        },
                    )

                    val currentPlaybackRequestVersion =
                        this@KsenaxVoiceController.voicePlaybackRequestVersion.get()
                    val isObsoletePlaybackRequest     =
                        currentPlaybackRequestVersion != playbackRequestVersion

                    if (isObsoletePlaybackRequest) {
                        return@launch
                    }

                    voicePlaybackStartJob = null

                    if (playbackResult is KsenaxVoicePlaybackResult.Success) {
                        updateSnapshot {
                            copy(
                                playingVoiceFilePath       = voiceFilePath,
                                pausedVoiceFilePath        = null,
                                playingVoiceProgressMillis = 0L,
                            )
                        }
                        startVoicePlaybackProgressUpdates(
                            coroutineScope = coroutineScope,
                            voiceFilePath  = voiceFilePath,
                        )
                    } else if (playbackResult is KsenaxVoicePlaybackResult.Failure) {
                        handlePlaybackFailure(playbackResult, onFailure)
                    }
                }
                KsenaxVoicePlaybackResult.Success
            }
        }

        if (playbackResult is KsenaxVoicePlaybackResult.Failure) {
            handlePlaybackFailure(playbackResult, onFailure)
        }
    }

    fun close() {
        shouldStopVoiceRecording.set(true)
        voicePlaybackRequestVersion.incrementAndGet()
        voiceRecordingJob?.cancel()
        voicePlaybackStartJob?.cancel()
        voicePlaybackStartJob = null
        stopVoiceRecordingDurationUpdates()
        stopVoicePlaybackProgressUpdates()
        voicePlayer.close()
        updateSnapshot {
            KsenaxVoiceSnapshot()
        }
    }

    private fun stopPlayback() {
        voicePlaybackRequestVersion.incrementAndGet()
        voicePlaybackStartJob?.cancel()
        voicePlaybackStartJob = null
        voicePlayer.stop()
        stopVoicePlaybackProgressUpdates()
        updateSnapshot {
            copy(
                playingVoiceFilePath       = null,
                pausedVoiceFilePath        = null,
                playingVoiceProgressMillis = 0L,
            )
        }
    }

    private fun startVoicePlaybackProgressUpdates(
        coroutineScope: CoroutineScope,
        voiceFilePath:  String,
    ) {
        stopVoicePlaybackProgressUpdates()
        updateSnapshot {
            copy(playingVoiceProgressMillis = voicePlayer.currentPositionMillis())
        }
        voicePlaybackProgressJob = coroutineScope.launch {
            while (_snapshot.value.playingVoiceFilePath == voiceFilePath) {
                updateSnapshot {
                    copy(playingVoiceProgressMillis = voicePlayer.currentPositionMillis())
                }
                delay(50L.milliseconds)
            }
        }
    }

    private fun stopVoicePlaybackProgressUpdates() {
        voicePlaybackProgressJob?.cancel()
        voicePlaybackProgressJob = null
    }

    private fun startVoiceRecordingDurationUpdates(
        coroutineScope:  CoroutineScope,
        startedAtMillis: Long,
    ) {
        stopVoiceRecordingDurationUpdates()
        voiceRecordingDurationJob = coroutineScope.launch {
            while (_snapshot.value.isRecording) {
                val elapsedMillis = SystemClock.elapsedRealtime() - startedAtMillis
                updateSnapshot {
                    copy(recordingDurationMillis = elapsedMillis.coerceAtLeast(0L))
                }
                delay(RECORDING_TIMER_UPDATE_DELAY_MILLIS)
            }
        }
    }

    private fun stopVoiceRecordingDurationUpdates() {
        voiceRecordingDurationJob?.cancel()
        voiceRecordingDurationJob = null
    }

    private fun clearVoicePlaybackState(
        voiceFilePath: String,
    ) {
        val currentSnapshot       = _snapshot.value
        val wasActiveVoiceMessage = currentSnapshot.playingVoiceFilePath == voiceFilePath ||
                                    currentSnapshot.pausedVoiceFilePath == voiceFilePath

        if (!wasActiveVoiceMessage) {
            return
        }

        updateSnapshot {
            copy(
                playingVoiceFilePath       = null,
                pausedVoiceFilePath        = null,
                playingVoiceProgressMillis = 0L,
            )
        }
        stopVoicePlaybackProgressUpdates()
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordVoiceInput(
        outputFile:       File,
        recordingProfile: KsenaxVoiceRecordingProfile,
    ): KsenaxRecordedVoiceInput {
        return when (recordingProfile) {
            KsenaxVoiceRecordingProfile.Gemma -> KsenaxRecordedVoiceInput.Gemma(
                message = voiceRecorder.recordToWav(
                    outputFile   = outputFile,
                    maxDuration  = 30.seconds,
                    shouldStop   = { shouldStopVoiceRecording.get() },
                    onVoiceLevel = ::publishVoiceLevel,
                ),
            )

            KsenaxVoiceRecordingProfile.Vosk -> KsenaxRecordedVoiceInput.Vosk(
                message = voskVoiceRecorder.recordToVoskWav(
                    outputFile   = outputFile,
                    maxDuration  = 30.seconds,
                    shouldStop   = { shouldStopVoiceRecording.get() },
                    onVoiceLevel = ::publishVoiceLevel,
                ),
            )
        }
    }

    private fun publishVoiceLevel(nextVoiceLevel: Float) {
        _voiceLevels.tryEmit(nextVoiceLevel)
        updateSnapshot {
            copy(voiceLevel = nextVoiceLevel)
        }
    }

    private suspend fun handleRecordedVoice(
        recordedVoice: KsenaxRecordedVoiceInput?,
        onRecorded:    suspend (KsenaxRecordedVoiceInput) -> Unit,
        onFailure:     (String) -> Unit,
    ) {
        updateSnapshot {
            copy(isProcessingVoice = true)
        }

        if (recordedVoice == null || recordedVoice.durationMillis <= 0L) {
            updateSnapshot {
                copy(isProcessingVoice = false)
            }
            return
        }

        try {
            onRecorded(recordedVoice)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onFailure(
                exception.message ?: "Failed to process voice message",
            )
        } finally {
            updateSnapshot {
                copy(isProcessingVoice = false)
            }
        }
    }

    private fun handlePlaybackFailure(
        playbackResult: KsenaxVoicePlaybackResult.Failure,
        onFailure:     (String) -> Unit,
    ) {
        updateSnapshot {
            copy(
                playingVoiceFilePath       = null,
                pausedVoiceFilePath        = null,
                playingVoiceProgressMillis = 0L,
            )
        }
        stopVoicePlaybackProgressUpdates()
        onFailure(playbackResult.message)
    }

    private fun updateSnapshot(
        update: KsenaxVoiceSnapshot.() -> KsenaxVoiceSnapshot,
    ) {
        _snapshot.update { snapshot -> snapshot.update() }
    }

    private companion object {
        const val RECORDING_TIMER_UPDATE_DELAY_MILLIS = 100L
        const val VOICE_LEVEL_BUFFER_CAPACITY         = 32
    }
}

/**
 * Единая модель состояния voice UI
 *
 * @property playingVoiceFilePath какой файл сейчас играет
 * @property pausedVoiceFilePath какой файл стоит на паузе
 * @property playingVoiceProgressMillis текущая позиция playback-а
 * @property recordingDurationMillis сколько длится текущая запись
 * @property recordingProfile профиль текущей записи, если запись идёт
 * @property voiceLevel текущая громкость микрофона
 * @property isRecording идёт ли запись
 * @property isProcessingVoice запись завершена и преобразуется в текст
 *
 * @since 0.2
 */
data class KsenaxVoiceSnapshot(
    val playingVoiceFilePath:       String? = null,
    val pausedVoiceFilePath:        String? = null,
    val playingVoiceProgressMillis: Long    = 0L,
    val recordingDurationMillis:    Long    = 0L,
    val recordingProfile:           KsenaxVoiceRecordingProfile? = null,
    val voiceLevel:                 Float   = 0f,
    val isRecording:                Boolean = false,
    val isProcessingVoice:          Boolean = false,
)
