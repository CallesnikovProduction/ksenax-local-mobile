package com.kolesnikovprod.ksetaorch.download.domain.data

/**
 * Дозорное значение для состояния, где активной задачи загрузки нет.
 *
 * Это не download id модели и не код ошибки. Значение используется только как
 * sentinel в UI-снапшоте, use case и coordinator-е.
 *
 * @since 0.1
 * @author Stephan Kolesnikov
 */
const val NO_DOWNLOAD_ID = -1L

/**
 * Иммутабельный UI-снапшот установки модели.
 *
 * Это основной язык общения между
 * [com.kolesnikovprod.ksetaorch.download.KsenaxModelInstallCoordinator]
 * и Compose/ViewModel-слоем. Снапшот не запускает загрузку сам и не знает про
 * Android [android.app.DownloadManager]; он только фиксирует состояние, которое
 * уже можно безопасно отрисовать.
 *
 * @property currentDownloadId id активной задачи загрузки или [NO_DOWNLOAD_ID].
 * @property isInstalled `true`, если локальный артефакт прошел install-проверку.
 * @property isDownloading `true`, если coordinator сейчас наблюдает активную загрузку.
 * @property isInterrupted `true`, если загрузка пропала, упала или файл оказался невалидным.
 * @property isCancelled `true`, если пользователь отменил загрузку.
 * @property preparationState состояние подготовки скачанного артефакта к runtime-виду.
 * Для Vosk отражает распаковку zip-архива и перенос модели в финальную директорию.
 * @property isValidating `true`, если идет проверка локального файла.
 * @property downloadProgress прогресс задачи в диапазоне `[0f; 1f]`.
 * @property hasCandidate результат быстрой проверки наличия кандидата установки.
 * @property isValidInstallation результат глубокой проверки установленного артефакта.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class KsenaxInstallSnapshot(
    val currentDownloadId:     Long                      = NO_DOWNLOAD_ID,
    val isInstalled:           Boolean                   = false,
    val isDownloading:         Boolean                   = false,
    val isInterrupted:         Boolean                   = false,
    val isCancelled:           Boolean                   = false,
    val preparationState:      KsenaxInstallCheckState   = KsenaxInstallCheckState.NON_CONFIRMED,
    val isValidating:          Boolean                   = false,
    val downloadProgress:      Float                     = 0f,
    val hasCandidate:          KsenaxInstallCheckState   = KsenaxInstallCheckState.NON_CONFIRMED,
    val isValidInstallation:   KsenaxInstallCheckState   = KsenaxInstallCheckState.NON_CONFIRMED,
)

/**
 * Состояние отдельного шага проверки локального модельного артефакта.
 *
 * Используется overlay-слоем, чтобы показывать пользователю не один общий
 * Boolean, а пошаговую картину: проверка еще не подтверждена, идет работа,
 * шаг успешен или шаг провален.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class KsenaxInstallCheckState {
    /**
     * Проверка завершилась успешно.
     */
    SUCCESS,

    /**
     * Проверка еще не выполнялась или результат пока нельзя считать финальным.
     */
    NON_CONFIRMED,

    /**
     * Проверка выполняется прямо сейчас.
     */
    LOADING,

    /**
     * Проверка завершилась неуспешно.
     */
    FAILURE,
}
