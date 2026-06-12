package com.kolesnikovprod.ksetaorch.download

/**
 * Фасад для загрузки и проверки конкретной модели Gemma.
 *
 * [KsenaxModelDownloader] умеет работать с произвольным URL, именем файла и именем
 * модели. Этот wrapper фиксирует параметры одной конкретной модели: HuggingFace URL,
 * локальное имя файла, директорию модели, ожидаемый размер и ожидаемый SHA256.
 *
 * Благодаря этому вызывающему коду не нужно каждый раз вручную передавать один и тот
 * же набор строк и integrity-метаданных. UI или будущий install manager может просто
 * вызвать `downloadGemma4E2B()`, `hasValidGemma4E2BFile()` или `deleteGemma4E2BFile()`.
 *
 * Важно: wrapper не запускает модель и не выполняет инференс. Он отвечает только за
 * удобный доступ к операциям загрузки/проверки/удаления для заранее описанной модели.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class KsenaxDownloadWrapper(
    private val downloader: KsenaxModelDownloader,
) {

    /**
     * Запускает загрузку Gemma 4 E2B через нижележащий downloader.
     *
     * Возвращаемое значение - это `downloadId` задачи DownloadManager. Его нужно
     * сохранить во внешнем слое, если приложение должно восстановить состояние
     * загрузки после пересоздания экрана или процесса.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun downloadGemma4E2B(): Long {
        downloader.deleteModelCacheDirectory(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            cacheDirectoryName = GEMMA_4_E2B_CACHE_DIRECTORY_NAME,
        )

        return downloader.download(
            url = GEMMA_4_E2B_URL,
            fileName = GEMMA_4_E2B_FILE_NAME,
            modelName = GEMMA_4_E2B_MODEL_NAME
        )
    }

    /**
     * Проверяет, есть ли непустой файл Gemma 4 E2B в ожидаемой локальной директории.
     *
     * Это быстрая проверка файла-кандидата. Она не доказывает, что модель полностью
     * скачана и не повреждена. Для финального флага установки нужно использовать
     * [hasValidGemma4E2BFile].
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun hasGemma4E2BFile(): Boolean {
        return downloader.hasModelFile(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            fileName = GEMMA_4_E2B_FILE_NAME
        )
    }

    /**
     * Выполняет полную integrity-проверку локального файла Gemma 4 E2B.
     *
     * Метод делегирует проверку в [KsenaxModelDownloader.hasValidModelFile], передавая
     * ожидаемый размер и SHA256 из публичных метаданных модели. Так приложение
     * подтверждает, что локальный `.litertlm` совпадает с ожидаемым артефактом.
     *
     * Подсчет SHA256 читает весь файл, поэтому этот метод нужно вызывать из фонового
     * контекста, например через `withContext(Dispatchers.IO)`.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun hasValidGemma4E2BFile(): Boolean {
        return downloader.hasValidModelFile(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            fileName = GEMMA_4_E2B_FILE_NAME,
            expectedSizeBytes = GEMMA_4_E2B_SIZE_BYTES,
            expectedSha256 = GEMMA_4_E2B_SHA256,
        )
    }

    /**
     * Удаляет локальный файл Gemma 4 E2B.
     *
     * Используется перед новой загрузкой, после отмены загрузки и после неуспешной
     * проверки файла. Метод возвращает `true`, если файла уже нет или он был удален.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun deleteGemma4E2BFile(): Boolean {
        val isModelFileDeleted = downloader.deleteModelFile(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            fileName = GEMMA_4_E2B_FILE_NAME
        )
        val isCacheDirectoryDeleted = downloader.deleteModelCacheDirectory(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            cacheDirectoryName = GEMMA_4_E2B_CACHE_DIRECTORY_NAME,
        )

        return isModelFileDeleted && isCacheDirectoryDeleted
    }

    /**
     * Отменяет активную задачу загрузки Gemma 4 E2B.
     *
     * Метод скрывает от install-сервиса конкретный механизм отмены. Для Android
     * реализации это `DownloadManager.remove(downloadId)`.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun cancelGemma4E2BDownload(downloadId: Long) {
        downloader.cancelDownload(downloadId)
    }

    /**
     * Возвращает доменный снимок задачи загрузки Gemma 4 E2B.
     *
     * Wrapper не отдает наружу `Cursor` или Android-константы, чтобы верхние слои
     * зависели от состояния установки, а не от деталей `DownloadManager`.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun queryGemma4E2BDownloadSnapshot(downloadId: Long): KsenaxDownloadSnapshot? {
        return downloader.queryDownloadSnapshot(downloadId)
    }

    /**
     * Возвращает абсолютный путь к локальному файлу Gemma 4 E2B.
     *
     * Метод не доказывает, что модель валидна. Перед передачей этого пути в runtime
     * вызывающий слой должен уже выполнить [hasValidGemma4E2BFile].
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getGemma4E2BModelPath(): String {
        return downloader.getModelFile(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            fileName = GEMMA_4_E2B_FILE_NAME,
        ).absolutePath
    }

    /**
     * Возвращает абсолютный путь к директории кэша Gemma 4 E2B.
     *
     * Папка называется `cashed` намеренно: это локальная runtime-директория рядом
     * с `.litertlm` файлом модели, куда LiteRT-LM может складывать временные
     * артефакты общения с моделью.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun getGemma4E2BCacheDirPath(): String {
        return downloader.getModelCacheDirectory(
            modelName = GEMMA_4_E2B_MODEL_NAME,
            cacheDirectoryName = GEMMA_4_E2B_CACHE_DIRECTORY_NAME,
        ).absolutePath
    }

    /**
     * Возвращает, разрешены ли новые загрузки через metered-сеть.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun isAllowOverMeteredNetwork(): Boolean {
        return downloader.isAllowOverMeteredNetwork()
    }

    /**
     * Настраивает возможность новых загрузок через metered-сеть.
     *
     * Уже созданные задачи DownloadManager этим вызовом не изменяются.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun setAllowOverMeteredNetwork(allowOverMeteredNetwork: Boolean) {
        downloader.setAllowOverMeteredNetwork(allowOverMeteredNetwork)
    }

    /**
     * Возвращает, разрешены ли новые загрузки в роуминге.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun isAllowOverRoaming(): Boolean {
        return downloader.isAllowOverRoaming()
    }

    /**
     * Настраивает возможность новых загрузок в роуминге.
     *
     * Уже созданные задачи DownloadManager этим вызовом не изменяются.
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun setAllowOverRoaming(allowOverRoaming: Boolean) {
        downloader.setAllowOverRoaming(allowOverRoaming)
    }

    companion object {
        /**
         * Стабильное имя модели внутри локальной директории `models/`.
         * @since 0.1
         * @author Stepan Kolesnikov
         */
        const val GEMMA_4_E2B_MODEL_NAME = "gemma-4-e2b"

        /**
         * HuggingFace URL файла модели.
         *
         * Сейчас используется `resolve/main`, что удобно для разработки, но для релиза
         * лучше закрепить URL на конкретный revision/commit. Тогда URL, размер и SHA256
         * будут описывать один неизменяемый артефакт.
         * @since 0.1
         * @author Stepan Kolesnikov
         */
        const val GEMMA_4_E2B_URL =
            "https://huggingface.co/litert-community/" +
                    "gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        /**
         * Локальное имя файла модели.
         * @since 0.1
         * @author Stepan Kolesnikov
         */
        const val GEMMA_4_E2B_FILE_NAME =
            "gemma-4-e2b-it.litertlm"

        /**
         * Директория runtime-кэша рядом с файлом модели:
         *
         * ```text
         * models/gemma-4-e2b/cashed
         * ```
         * @since 0.1
         * @author Stepan Kolesnikov
         */
        const val GEMMA_4_E2B_CACHE_DIRECTORY_NAME = "cashed"

        /**
         * Ожидаемый размер `.litertlm` файла в байтах.
         *
         * Используется как быстрый integrity-фильтр перед дорогим подсчетом SHA256.
         * @since 0.1
         * @author Stepan Kolesnikov
         */
        const val GEMMA_4_E2B_SIZE_BYTES = 2_588_147_712L

        /**
         * Ожидаемый SHA256 модельного файла из публичных HuggingFace/LFS метаданных.
         *
         * Локально скачанный файл считается установленной моделью только если его
         * собственный SHA256 совпадает с этим значением.
         * @since 0.1
         * @author Stepan Kolesnikov
         */
        const val GEMMA_4_E2B_SHA256 =
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    }
}

/**
 * Sentinel-значение для состояния, где активной задачи загрузки нет.
 * @since 0.1
 * @author Stepan Kolesnikov
 */
const val NO_DOWNLOAD_ID = -1L
