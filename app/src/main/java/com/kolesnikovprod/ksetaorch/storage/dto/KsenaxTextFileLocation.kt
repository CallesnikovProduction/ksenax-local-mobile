package com.kolesnikovprod.ksetaorch.storage.dto

/**
 * Общее описание места, где находится текстовый артефакт.
 *
 * DTO используется и для чтения, и для записи: операция может отличаться, но
 * способ объяснить пользователю "где лежит файл" остаётся одним и тем же.
 *
 * @property displayPath человекочитаемое место назначения. Это может быть
 * реальный absolute path, относительный путь в Documents или понятное описание
 * SAF-директории. Значение не должно парситься как стабильный путь.
 * @property storageKind стабильная категория backend-а, через которую можно
 * принимать машинные решения без разбора [displayPath].
 * @property uri строковое представление Android [android.net.Uri], если
 * backend выдаёт URI. Для app-private файлов может быть `null`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxTextFileLocation(
    val displayPath: String,
    val storageKind: KsenaxTextFileStorageKind,
    val uri:         String? = null,
)

/**
 * Машинно-читаемый тип backend-а, в котором находится текстовый файл.
 *
 * Значения enum фиксируют архитектурную категорию, а не конкретную папку.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
enum class KsenaxTextFileStorageKind {
    /**
     * Приватная директория приложения, обычно основанная на [android.content.Context.filesDir].
     */
    APP_PRIVATE,

    /**
     * Пользовательски видимое хранилище через Android MediaStore.
     */
    MEDIA_STORE,

    /**
     * Legacy-доступ к пользовательской Documents-папке через прямой File API.
     *
     * Используется только для старых Android-версий, где ещё допустим прямой
     * доступ к публичной Documents-директории.
     */
    PUBLIC_DOCUMENTS,

    /**
     * Папка, выбранная пользователем через Storage Access Framework.
     */
    SAF
}
