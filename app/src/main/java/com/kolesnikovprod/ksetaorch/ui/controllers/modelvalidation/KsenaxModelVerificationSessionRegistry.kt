package com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation

/**
 * Хранит успешные проверки локальных моделей только в пределах текущей
 * foreground-сессии приложения.
 *
 * Реестр не читает файлы и не знает о Compose. Проверяющий controller отмечает
 * модель по стабильному ключу install-target-а, а process lifecycle сбрасывает
 * все отметки, когда приложение покидает foreground. Благодаря номеру сессии
 * проверка, завершившаяся уже после такого сброса, не может вернуть устаревшую
 * отметку обратно в кэш.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
class KsenaxModelVerificationSessionRegistry {

    private val lock = Any()
    private var currentSession = 0L
    private var isForeground = false
    private val verifiedModels = mutableSetOf<String>()

    /**
     * Сообщает, была ли модель успешно проверена в текущей foreground-сессии.
     *
     * @since 0.3
     */
    fun isVerified(modelKey: String): Boolean = synchronized(lock) {
        modelKey in verifiedModels
    }

    /**
     * Запоминает успешную проверку, выполненную доверенным install-контуром в
     * текущей foreground-сессии.
     *
     * @since 0.3
     */
    fun markVerified(modelKey: String): Boolean {
        require(modelKey.isNotBlank()) {
            "Model verification key must not be blank."
        }
        return synchronized(lock) {
            if (!isForeground) {
                false
            } else {
                verifiedModels += modelKey
                true
            }
        }
    }

    /**
     * Забывает результат конкретной модели, например перед её повторной
     * установкой или заменой файла.
     *
     * @since 0.3
     */
    fun invalidate(modelKey: String) {
        require(modelKey.isNotBlank()) {
            "Model verification key must not be blank."
        }
        synchronized(lock) {
            verifiedModels -= modelKey
        }
    }

    /**
     * Возвращает номер foreground-сессии, в которой началась проверка.
     *
     * @since 0.3
     */
    internal fun currentSession(): Long = synchronized(lock) {
        currentSession
    }

    /**
     * Сохраняет результат только если приложение всё ещё находится в той же
     * foreground-сессии, в которой проверка началась.
     *
     * @since 0.3
     */
    internal fun markVerified(
        modelKey: String,
        expectedSession: Long,
    ): Boolean {
        require(modelKey.isNotBlank()) {
            "Model verification key must not be blank."
        }
        return synchronized(lock) {
            if (!isForeground || currentSession != expectedSession) {
                false
            } else {
                verifiedModels += modelKey
                true
            }
        }
    }

    /**
     * Разрешает сохранять результаты новой foreground-сессии.
     *
     * @since 0.3
     */
    fun onAppForegrounded() {
        synchronized(lock) {
            isForeground = true
        }
    }

    /**
     * Закрывает текущую foreground-сессию и забывает все прошлые проверки.
     *
     * Пока приложение остаётся в background, поздно завершившаяся install- или
     * chat-проверка не сможет отметить модель валидной.
     *
     * @since 0.3
     */
    fun onAppBackgrounded() {
        synchronized(lock) {
            isForeground = false
            currentSession += 1L
            verifiedModels.clear()
        }
    }
}
