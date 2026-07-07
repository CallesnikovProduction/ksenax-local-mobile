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
     * Очищает сохраненный `downloadId` и локальные артефакты.
     *
     * @since 0.2
     */
    fun clearArtifacts()

    /**
     * Удаляет только сохраненный `downloadId`.
     *
     * @since 0.2
     */
    fun clearSavedDownloadId()

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
     * класс получает доменный [KsenaxDownloadTaskSnapshot], а не сырые данные установщика.
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
