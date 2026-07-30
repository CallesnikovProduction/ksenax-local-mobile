package com.kolesnikovprod.ksetaorch.download.domain.data

/**
 * Доменный снимок состояния текущей задачи загрузки (Data Transfer Object)
 * в конкретный момент времени.
 *
 * Иными словами, отвечает на вопрос: **«Что происходит сейчас с загрузкой?»**
 *
 * @property progress число в диапазоне `[0; 1]`, показывающее "процент" текущей загрузки.
 * Рекомендуется приведение к процентам явно.
 * @property state локальный флаг состояния (на каком этапе сейчас скачка)
 * @property reasonCode опциональный платформенный код причины от DownloadManager.
 * Полезен для диагностики состояний `PAUSED` и `FAILED`.
 * @property waitReason доменная причина временного ожидания, пригодная для UI
 * без импорта Android-констант.
 * @property transferMetrics сырые счётчики байтов, сглаженная фактическая
 * скорость и ETA текущей загрузки.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxDownloadTaskSnapshot(
    val progress:        Float,
    val state:           KsenaxDownloadState,
    val reasonCode:      Int? = null,
    val waitReason:      KsenaxDownloadWaitReason? = null,
    val transferMetrics: KsenaxDownloadTransferMetrics = KsenaxDownloadTransferMetrics(),
) {
    init {
        // require ("требовать")
        require(progress in 0f..1f) {
            "Download progress must be in 0f <= range <= 1f, but was $progress"
        }
    }
}

/**
 * Причина, по которой системная загрузка временно не передаёт данные.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class KsenaxDownloadWaitReason {
    WAITING_TO_RETRY,
    WAITING_FOR_NETWORK,
    WAITING_FOR_UNMETERED_NETWORK,
    PAUSED_BY_SYSTEM,
}

/**
 * Собственная модель (язык) состояния загрузки без прямой зависимости UI от Android-констант.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxDownloadState {

    /**
     * Терминальный вариант завершения загрузки со статусом «Успешно»
     */
    SUCCESSFUL,

    /**
     * Терминальный вариант завершения загрузки со статусом «Неудачно»
     */
    FAILED,

    /**
     * Нетерминальный вариант состояния загрузки со статусом «В ожидании»
     */
    PENDING,

    /**
     * Нетерминальный вариант состояния загрузки со статусом «Запущено»
     */
    RUNNING,

    /**
     * Нетерминальный вариант состояния загрузки со статусом «Остановлено»
     */
    PAUSED,

    /**
     * Нетерминальный вариант состояния загрузки со статусом «Неизвестно»
     */
    UNKNOWN;
}
