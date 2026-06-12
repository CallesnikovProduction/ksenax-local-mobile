package com.kolesnikovprod.ksetaorch.download

/**
 * Снимок текущей задачи загрузки.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
data class KsenaxDownloadSnapshot(
    val progress: Float,
    val state: KsenaxDownloadState,
)

/**
 * Упрощенное состояние загрузки без прямой зависимости UI от Android-констант.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
enum class KsenaxDownloadState {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
    UNKNOWN,
}
