package com.kolesnikovprod.ksetaorch.download.contracts

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID

/**
 * Верхний контракт установки одного локального модельного артефакта.
 *
 * Coordinator должен зависеть от этого интерфейса, а не от Gemma/Vosk
 * реализаций. Контракт описывает одинаковый жизненный цикл:
 * старт загрузки, восстановление download id, отмена, очистка, подготовка
 * скачанного кандидата и финальная проверка установленного артефакта.
 * UI-coordinator и process-independent background worker используют один
 * контракт и одну общую финализацию; реализация не должна предполагать, что
 * вызывающий ViewModel всё время остаётся жив.
 *
 * Для готовых `.litertlm` моделей Gemma и FunctionGemma подготовка подтверждает
 * наличие файла. Для zip-модели Vosk она распаковывает архив и приводит
 * файловую систему к runtime-виду.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxModelInstallUseCase {

    /**
     * Какой локальный артефакт обслуживает конкретная реализация.
     *
     * @since 0.2
     */
    val installTarget: KsenaxInstallTarget

    /**
     * Ставит артефакт в очередь загрузки и сохраняет download id.
     *
     * Работает в следующей последовательности:
     * 1. применяется сетевая политика ([KsenaxDownloadPolicy]);
     * 2. ставится загрузка в очередь;
     * 3. получается `downloadId`;
     * 4. сохраняется `downloadId`;
     * 5. возвращается `downloadId` наружу.
     *
     * Сам transfer выполняет системный DownloadManager. После его terminal
     * broadcast фоновый install worker может продолжить подготовку и проверку
     * через этот же use case без участия UI.
     *
     * @since 0.2
     */
    fun startDownloadAndSave(
        policy: KsenaxDownloadPolicy = KsenaxDownloadPolicy(),
    ): Long

    /**
     * Возвращает сохраненный download id или [NO_DOWNLOAD_ID].
     *
     * Даёт ответы на вопросы:
     * - *была ли активная загрузка?*
     * - *какой `downloadId` проверять вообще?*
     * - *можно ли восстановить состояние экрана установки?*
     *
     * @since 0.2
     */
    fun getSavedDownloadId(): Long

    /**
     * Если есть реальный `downloadId` — отменяет загрузку;
     * после этого чистятся артефакты.
     *
     * @since 0.2
     */
    fun cancelDownload(downloadId: Long)

    /**
     * Безусловно очищает сохранённый `downloadId` и локальные артефакты.
     * Для асинхронных terminal callback-ов следует использовать
     * [clearArtifactsIfOwnedBy].
     *
     * @since 0.2
     */
    fun clearArtifacts()

    /**
     * Очищает локальные артефакты и сохранённый id только если текущим
     * владельцем установки всё ещё является [downloadId].
     *
     * Метод нужен для запоздалых observer/worker-ов: завершение старой задачи
     * не должно удалить файлы или id более новой загрузки.
     * Реализация с несколькими экземплярами use case обязана переопределить
     * метод атомарно на общем target-specific хранилище. Default оставлен
     * только для source compatibility простых однопоточных реализаций.
     *
     * @return `true`, если id совпал и очистка принадлежала вызывающему;
     * `false`, если задача уже была отменена или заменена.
     *
     * @since 0.4
     */
    fun clearArtifactsIfOwnedBy(downloadId: Long): Boolean {
        if (getSavedDownloadId() != downloadId) return false

        clearArtifacts()
        return true
    }

    /**
     * Безусловно удаляет сохранённый `downloadId`.
     * Для асинхронной финализации следует использовать
     * [clearSavedDownloadIdIfOwnedBy].
     *
     * @since 0.2
     */
    fun clearSavedDownloadId()

    /**
     * Удаляет сохранённый id только если он всё ещё равен [downloadId].
     * Production-реализация должна переопределить проверку и удаление как одну
     * атомарную операцию общего target-specific хранилища.
     *
     * @return `true`, если вызывающий владел сохранённым id.
     *
     * @since 0.4
     */
    fun clearSavedDownloadIdIfOwnedBy(downloadId: Long): Boolean {
        if (getSavedDownloadId() != downloadId) return false

        clearSavedDownloadId()
        return true
    }

    /**
     * Удаляет локальные файлы/директории установки, не меняя сохранённый `downloadId`.
     *
     * @since 0.2
     */
    fun deleteLocalArtifacts(): Boolean

    /**
     * Быстрая проверка, есть ли на диске кандидат для установки или уже
     * установленный артефакт.
     *
     * **Необходимо вызывать на [kotlinx.coroutines.Dispatchers.IO],
     * так как эта операция — операция с файловой системой не на основном потоке.**
     *
     * @since 0.2
     */
    suspend fun hasInstallCandidate(): Boolean

    /**
     * Подготавливает скачанный кандидат к runtime-виду.
     *
     * Для обычного файла может вернуть `true` без действий. Для архивов этот
     * шаг отвечает за распаковку, перенос во финальную директорию и удаление
     * временных файлов.
     *
     * **Необходимо вызывать на [kotlinx.coroutines.Dispatchers.IO],
     * так как эта операция — операция с файловой системой не на основном потоке.**
     *
     * @since 0.2
     */
    suspend fun prepareInstallCandidate(): Boolean

    /**
     * Проверяет, что установленный артефакт готов к использованию runtime-слоем.
     *
     * - Если проверка успешна, то поступает `true/false` от gateway;
     * - Если корутина отменена, то отмена пробрасывается дальше;
     * - Если любая другая ошибка, то установка считается невалидной.
     *
     * **Необходимо вызывать на [kotlinx.coroutines.Dispatchers.IO],
     * так как эта операция — операция с файловой системой не на основном потоке.**
     *
     * @since 0.2
     */
    suspend fun hasValidInstallation(): Boolean

    /**
     * Возвращает состояние активной задачи загрузки.
     *
     * Читается состояние из [android.app.DownloadManager], а реализующий
     * класс получает доменный [KsenaxDownloadTaskSnapshot], а не сырой Cursor.
     * Снапшот уже содержит progress, реальные byte-счётчики, среднюю скорость
     * и ETA, пригодные для передачи координатору.
     *
     * @since 0.2
     */
    fun queryDownloadSnapshot(downloadId: Long): KsenaxDownloadTaskSnapshot?

    /**
     * Возвращает путь, который должен использовать runtime после успешной
     * проверки установки.
     *
     * @since 0.2
     */
    fun getInstalledPath(): String
}
