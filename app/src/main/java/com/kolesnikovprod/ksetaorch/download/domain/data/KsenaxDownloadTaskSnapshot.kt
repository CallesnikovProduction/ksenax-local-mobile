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
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxDownloadTaskSnapshot(
    val progress:   Float,
    val state:      KsenaxDownloadState,
    val reasonCode: Int? = null
) {
    init {
        // require ("требовать")
        require(progress in 0f..1f) {
            "Download progress must be in 0f <= range <= 1f, but was $progress"
        }
    }
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
