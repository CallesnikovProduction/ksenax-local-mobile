package com.kolesnikovprod.ksetaorch.download.contracts

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import java.io.File

/**
 * Единый gateway-контракт для работы с одним заранее выбранным модельным
 * артефактом.
 *
 * Gateway подразумевает, что конкретная реализация уже знает свой артефакт:
 * URL, локальное имя файла, директорию модели и правила integrity-проверки.
 *
 * Поэтому вызывающий слой не должен передавать artifact-specific константы в
 * каждый метод. Реализация сама подставляет их внутрь низкоуровневого backend-а.
 *
 * Gateway описывает весь локальный "цех обслуживания" модельного файла:
 * - сетевую политику новых загрузок;
 * - постановку загрузки конкретной модели в очередь;
 * - построение относительных путей внутри model storage;
 * - получение [File]-ссылок на модельную директорию, runtime-cache и saved-voices;
 * - удаление файлов и model-adjacent директорий;
 * - отмену и опрос активной задачи загрузки;
 * - быструю и глубокую проверку локального файла модели.
 *
 * Gateway не должен считать модель установленной сразу после [enqueue].
 * `downloadId` означает только то, что Android-прослойка приняла задачу в
 * очередь. Готовность модели подтверждается отдельно через [hasValidModel].
 * Если артефакт требует post-processing, например распаковки zip Vosk-модели,
 * этот шаг должен жить выше - в
 * [com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase].
 *
 * Gateway может использовать такой механизм внутри реализации,
 * но не обязан наследовать его публичный API.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxDownloadGateway {

    /**
     * Разрешает или запрещает новые загрузки через metered-сеть, например
     * мобильный интернет с лимитом трафика.
     *
     * Изменение значения влияет только на будущие задачи загрузки. Уже созданные
     * задачи системного download-менеджера не перенастраиваются этим флагом.
     *
     * @since 0.2
     */
    var allowOverMeteredNetwork: Boolean

    /**
     * Разрешает или запрещает новые загрузки в роуминге.
     *
     * Для тяжелых model artifacts безопасное значение по умолчанию - `false`.
     * Как и [allowOverMeteredNetwork], этот флаг должен применяться только к
     * новым задачам.
     *
     * @since 0.2
     */
    var allowOverRoaming: Boolean

    /**
     * Ставит заранее выбранный модельный файл в очередь загрузки.
     *
     * Реализация сама подставляет URL, имя файла и имя директории модели. Метод
     * возвращает только id задачи загрузки, а не признак установленной модели.
     *
     * @return id задачи загрузки, по которому позже можно отменить или запросить
     * состояние через [querySnapshotBy].
     *
     * @since 0.2
     */
    fun enqueue(): Long

    /**
     * Формирует относительный путь к файлу модели внутри app-specific storage.
     *
     * Пример ожидаемой формы:
     *
     * ```text
     * models/<model-name>/<downloaded-artifact>
     * ```
     *
     * @return относительный путь, пригодный для передачи в download destination.
     *
     * @since 0.2
     */
    fun modelFilePath(): String

    /**
     * Формирует относительный путь к директории конкретной модели.
     *
     * @return относительный путь директории модели.
     *
     * @since 0.2
     */
    fun modelDirectoryPath(): String

    /**
     * Возвращает [File] ожидаемого локального файла модели.
     *
     * Метод только собирает путь. Он не обязан создавать файл или доказывать,
     * что модель уже скачана и валидна.
     *
     * @return объект [File] для ожидаемого пути модели.
     *
     * @since 0.2
     */
    fun getModelFile(): File

    /**
     * Возвращает [File] директории конкретной модели.
     *
     * @return директория модели в app-specific storage.
     *
     * @since 0.2
     */
    fun getModelDirectory(): File

    /**
     * Возвращает [File] подпапки внутри директории конкретной модели.
     *
     * Используется для model-adjacent артефактов: runtime-cache, сохраненных
     * голосовых команд и других файлов, которые должны жить рядом с моделью.
     *
     * @param directoryName имя подпапки внутри директории модели.
     * @return объект [File] для подпапки модели.
     *
     * @since 0.2
     */
    fun getModelSubdirectory(
        directoryName: String
    ): File

    /**
     * Возвращает runtime-cache директорию выбранной модели.
     *
     * Эта директория не является Gemma-specific свойством. Это общий
     * model-adjacent паттерн: runtime может складывать сюда временные артефакты,
     * которые должны жить рядом с конкретным `.litertlm` файлом.
     *
     * @return директория `runtime-cache` внутри директории модели.
     *
     * @since 0.2
     */
    fun getRuntimeCacheDirectory(): File {
        return getModelSubdirectory(RUNTIME_CACHE_DIRECTORY_NAME)
    }

    /**
     * Возвращает директорию сохраненных голосовых команд для выбранной модели.
     *
     * Папка тоже не привязана к Gemma как к семейству моделей: это общий
     * локальный runtime-артефакт приложения рядом с моделью.
     *
     * @return директория `saved-voices` внутри директории модели.
     *
     * @since 0.2
     */
    fun getSavedVoicesDirectory(): File {
        return getModelSubdirectory(SAVED_VOICES_DIRECTORY_NAME)
    }

    /**
     * Удаляет локальный файл модели.
     *
     * Реализация должна считать отсутствие файла успешным итогом, потому что
     * целевое состояние "файла нет" уже достигнуто.
     *
     * @return `true`, если файл отсутствует или был удален.
     *
     * @since 0.2
     */
    fun deleteModelFile(): Boolean

    /**
     * Удаляет подпапку внутри директории конкретной модели.
     *
     * Основной сценарий - очистка `runtime-cache` или других артефактов,
     * которые могут перестать соответствовать новому/перекачанному файлу модели.
     *
     * @param directoryName имя подпапки внутри директории модели.
     * @return `true`, если подпапка отсутствует или была удалена.
     *
     * @since 0.2
     */
    fun deleteModelSubdirectory(
        directoryName: String
    ): Boolean

    /**
     * Удаляет runtime-cache директорию выбранной модели.
     *
     * Вызывается перед повторной загрузкой и при очистке невалидного файла,
     * чтобы runtime не продолжал работать со старыми артефактами.
     *
     * @return `true`, если директория отсутствует или была удалена.
     *
     * @since 0.2
     */
    fun deleteRuntimeCacheDirectory(): Boolean {
        return deleteModelSubdirectory(RUNTIME_CACHE_DIRECTORY_NAME)
    }

    /**
     * Отменяет активную задачу загрузки по ее системному id.
     *
     * @param downloadId id задачи, полученный из [enqueue].
     *
     * @since 0.2
     */
    fun cancelBy(downloadId: Long)

    /**
     * Возвращает доменный снимок состояния задачи загрузки.
     *
     * Реализация должна спрятать платформенные детали вроде Android `Cursor`,
     * `DownloadManager.Query` и status-констант, отдавая наружу только
     * [KsenaxDownloadTaskSnapshot].
     *
     * @param downloadId id задачи, полученный из [enqueue].
     * @return снимок состояния или `null`, если задача не найдена.
     *
     * @since 0.2
     */
    fun querySnapshotBy(downloadId: Long): KsenaxDownloadTaskSnapshot?

    /**
     * Быстро проверяет наличие локального файла-кандидата.
     *
     * Эта проверка не доказывает, что модель валидна. Частично скачанный файл
     * тоже может существовать и иметь ненулевой размер, поэтому финальный флаг
     * установки должен опираться на [hasValidModel].
     *
     * @return `true`, если в ожидаемом месте есть непустой файл-кандидат.
     *
     * @since 0.2
     */
    fun hasModelCandidate(): Boolean

    /**
     * Выполняет глубокую target-specific integrity-проверку локальной модели.
     *
     * Для готового `.litertlm` это обычно точный размер и SHA256. Для
     * распакованной Vosk-модели проверяется обязательная структура директорий
     * и файлов.
     *
     * Глубокая проверка может читать большой файл или обходить директорию,
     * поэтому вызывающий use case выполняет её вне главного потока.
     *
     * @return `true`, если локальный файл совпадает с ожидаемым артефактом.
     *
     * @since 0.2
     */
    fun hasValidModel(): Boolean

    companion object {
        /**
         * Общая директория runtime-кэша рядом с модельным файлом.
         *
         * @since 0.2
         */
        const val RUNTIME_CACHE_DIRECTORY_NAME = "runtime-cache"

        /**
         * Общая директория для сохраненных WAV-файлов голосовых команд.
         *
         * @since 0.2
         */
        const val SAVED_VOICES_DIRECTORY_NAME = "saved-voices"
    }
}
