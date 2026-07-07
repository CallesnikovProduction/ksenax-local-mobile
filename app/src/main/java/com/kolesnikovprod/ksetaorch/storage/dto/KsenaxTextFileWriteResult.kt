package com.kolesnikovprod.ksetaorch.storage.dto

/**
 * Закрытая иерархия результата операции записи текстового файла.
 *
 * DTO намеренно остаётся маленьким: слой инструментов должен получить достаточно
 * данных, чтобы объяснить пользователю, куда был сохранён артефакт, но не должен
 * зависеть от конкретного Android API, через который это произошло.
 *
 * [Success] описывает успешно достигнутое место хранения. [Failure] описывает
 * ожидаемую ошибку хранилища в форме, пригодной для UI/лога.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 * @see com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver
 */
sealed interface KsenaxTextFileWriteResult {

    /**
     * Успешный результат операции с текстовым файлом.
     *
     * @property location место, куда был записан текстовый артефакт.
     */
    data class Success(
        val location: KsenaxTextFileLocation,
    ) : KsenaxTextFileWriteResult {

        constructor(
            displayPath: String,
            storageKind: KsenaxTextFileStorageKind,
            uri:         String? = null,
        ) : this(
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
     * Ожидаемая ошибка записи или доступа к хранилищу.
     *
     * [message] должен быть достаточно понятным для диагностики и аккуратного
     * показа пользователю. Не стоит складывать сюда stacktrace или внутренние
     * детали реализации Android-провайдера.
     */
    data class Failure(
        val message: String,
    ) : KsenaxTextFileWriteResult
}
