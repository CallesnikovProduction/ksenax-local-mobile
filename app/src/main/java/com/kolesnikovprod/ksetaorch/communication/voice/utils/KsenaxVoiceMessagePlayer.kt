package com.kolesnikovprod.ksetaorch.communication.voice.utils

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Проигрывает один локально сохранённый WAV-файл голосовой команды за раз.
 *
 * Новый запуск останавливает предыдущий файл и включает выбранную запись
 * сначала. [play] готовит файл на [Dispatchers.IO], поэтому UI-bound coroutine
 * не выполняет синхронную media preparation на своём потоке.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxVoiceMessagePlayer {

    /**
     * Текущий плеер.
     * `null` означает, что ничего не играет ИЛИ плеер уже релизнут
     *
     * @since 0.2
     */
    private var mediaPlayer:         MediaPlayer? = null

    /**
     * Путь файла, который сейчас считается активным.
     * @since 0.2
     */
    private var activeVoiceFilePath: String? = null

    /*
    * Callbacks for current playback
    * */
    private var onCompletion:        (() -> Unit)? = null
    private var onError:             ((String) -> Unit)? = null

    /**
     * Готовит и проигрывает WAV-файл голосовой команды.
     *
     * Внутри может быть подготовка плеера, поэтому, I/O.
     *
     * @param voiceFile директория WAV-файла
     * @param onCompletion callback после успешного окончания
     * @param onError callback при ошибке проигрывания
     *
     * @since 0.2
     */
    suspend fun play(
        voiceFile:    File,
        onCompletion: () -> Unit = {},
        onError:      (String) -> Unit = {},
    ): KsenaxVoicePlaybackResult = withContext(Dispatchers.IO) {
        stop() // намеренно останавливаем предыдущую запись

        if (!voiceFile.exists() || !voiceFile.isFile) {
            return@withContext KsenaxVoicePlaybackResult.Failure("Voice command file not found")
        }

        // временная ссылка для catch-блока
        var nextPlayer: MediaPlayer? = null

        /*
        * Далее начинается цепочка вызовов:
        * - idle
        * - initialized
        * - prepared
        * - started
        * */
        try {
            // Стадия idle
            val preparedPlayer = MediaPlayer()
            nextPlayer = preparedPlayer
            preparedPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // речь
                    .setUsage(AudioAttributes.USAGE_MEDIA)               // media playback
                    .build()
            )

            // стадия initialized
            preparedPlayer.setDataSource(voiceFile.absolutePath)

            /*
            * Прикручивание слушателей.
            * */

            // playback дошёл до конца
            preparedPlayer.setOnCompletionListener { completedPlayer ->
                // сравнение по ссылке + сам callback
                val completionCallback = if (mediaPlayer === completedPlayer) {
                    onCompletion
                } else {
                    null
                }
                // полная очистка состояния
                if (mediaPlayer === completedPlayer) {
                    mediaPlayer                                = null
                    activeVoiceFilePath                        = null
                    this@KsenaxVoiceMessagePlayer.onCompletion = null
                    this@KsenaxVoiceMessagePlayer.onError      = null
                }
                completedPlayer.release()
                completionCallback?.invoke()
            }

            // для ошибок playback-а
            preparedPlayer.setOnErrorListener { failedPlayer, _, _ ->
                val errorCallback = if (mediaPlayer === failedPlayer) {
                    this@KsenaxVoiceMessagePlayer.onError
                } else {
                    null
                }
                if (mediaPlayer === failedPlayer) {
                    mediaPlayer                                = null
                    activeVoiceFilePath                        = null
                    this@KsenaxVoiceMessagePlayer.onCompletion = null
                    this@KsenaxVoiceMessagePlayer.onError      = null
                }
                failedPlayer.release()
                errorCallback?.invoke("Failed to play voice command")
                true // ошибка обработана
            }

            // стадия prepared
            preparedPlayer.prepare()

            // только после подготовленного плеера, он может быть активным
            mediaPlayer                                = preparedPlayer
            activeVoiceFilePath                        = voiceFile.absolutePath
            this@KsenaxVoiceMessagePlayer.onCompletion = onCompletion
            this@KsenaxVoiceMessagePlayer.onError      = onError

            // стадия started
            preparedPlayer.start()
            nextPlayer = null
            KsenaxVoicePlaybackResult.Success
        } catch (ce: CancellationException) {
            if (mediaPlayer === nextPlayer) {
                mediaPlayer                                = null
                activeVoiceFilePath                        = null
                this@KsenaxVoiceMessagePlayer.onCompletion = null
                this@KsenaxVoiceMessagePlayer.onError      = null
            }
            nextPlayer?.release()
            throw ce
        } catch (e: Exception) {
            if (mediaPlayer === nextPlayer) {
                mediaPlayer                                = null
                activeVoiceFilePath                        = null
                this@KsenaxVoiceMessagePlayer.onCompletion = null
                this@KsenaxVoiceMessagePlayer.onError      = null
            }
            nextPlayer?.release()
            KsenaxVoicePlaybackResult.Failure(
                e.message ?: "Failed to play voice command",
            )
        }
    }

    /**
     * Ставит текущее голосовое сообщение на паузу.
     *
     * @since 0.2
     */
    fun pause(): KsenaxVoicePlaybackResult {
        val activePlayer = mediaPlayer
            ?: return KsenaxVoicePlaybackResult.Failure("No active voice command")

        return try {
            if (activePlayer.isPlaying) {
                activePlayer.pause()
            }
            KsenaxVoicePlaybackResult.Success
        } catch (exception: Exception) {
            KsenaxVoicePlaybackResult.Failure(
                exception.message ?: "Failed to pause voice command",
            )
        }
    }

    /**
     * Продолжает проигрывание после паузы.
     *
     * @since 0.2
     */
    fun resume(): KsenaxVoicePlaybackResult {
        val activePlayer = mediaPlayer
            ?: return KsenaxVoicePlaybackResult.Failure("No voice to continuing")

        return try {
            if (!activePlayer.isPlaying) {
                activePlayer.start()
            }
            KsenaxVoicePlaybackResult.Success
        } catch (exception: Exception) {
            KsenaxVoicePlaybackResult.Failure(
                exception.message ?: "Failed to continue voice command",
            )
        }
    }

    /**
     * Останавливает текущее воспроизведение и освобождает Android audio-resource.
     *
     * @since 0.2
     */
    fun stop() {
        val activePlayer    = mediaPlayer ?: return
        mediaPlayer         = null
        activeVoiceFilePath = null
        onCompletion        = null
        onError             = null

        runCatching {
            if (activePlayer.isPlaying) {
                activePlayer.stop()
            }
        }
        activePlayer.release()
    }

    // AutoCloseable implementation
    fun close() {
        stop()
    }

    /**
     * UI-oriented function: показать кнопку паузы, или кнопка старта (например)
     *
     * @since 0.2
     */
    fun isPlayingFile(voiceFilePath: String): Boolean {
        return activeVoiceFilePath == voiceFilePath && mediaPlayer?.isPlaying == true
    }

    /**
     * UI-oriented function: вернуть текущую позицию playback-а.
     *
     * @since 0.2
     */
    fun currentPositionMillis(): Long {
        return runCatching {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        }.getOrDefault(0L)
    }
}

/**
 * Интерфейс-DTO, содержащий два состояния проигрывания.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxVoicePlaybackResult {
    data object Success : KsenaxVoicePlaybackResult

    data class Failure(
        val message: String,
    ) : KsenaxVoicePlaybackResult
}
