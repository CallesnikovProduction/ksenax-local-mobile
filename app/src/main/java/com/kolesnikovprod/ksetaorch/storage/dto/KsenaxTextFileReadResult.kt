package com.kolesnikovprod.ksetaorch.storage.dto

/**
 * Закрытая иерархия результата операции чтения текстового файла.
 *
 * Успешное чтение возвращает сам текст и [KsenaxTextFileLocation], чтобы
 * вызывающий слой мог показать пользователю источник данных без знания
 * конкретного Android storage API.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxTextFileReadResult {

    /**
     * Успешно прочитанный текстовый артефакт.
     *
     * @property text содержимое файла, декодированное как UTF-8.
     * @property location место, откуда был прочитан файл.
     */
    data class Success(
        val text:     String,
        val location: KsenaxTextFileLocation,
    ) : KsenaxTextFileReadResult {

        constructor(
            text:        String,
            displayPath: String,
            storageKind: KsenaxTextFileStorageKind,
            uri:         String? = null,
        ) : this(
            text = text,
            location = KsenaxTextFileLocation(
                displayPath = displayPath,
                storageKind = storageKind,
                uri         = uri,
            )
        )

        val displayPath: String
            get() = location.displayPath

        val storageKind: KsenaxTextFileStorageKind
            get() = location.storageKind

        val uri: String?
            get() = location.uri
    }

    /**
     * Файл с таким именем не найден в текущем backend-е.
     */
    data class NotFound(
        val fileName: String,
    ) : KsenaxTextFileReadResult

    /**
     * Ожидаемая ошибка чтения или доступа к хранилищу.
     */
    data class Failure(
        val message: String,
    ) : KsenaxTextFileReadResult
}
