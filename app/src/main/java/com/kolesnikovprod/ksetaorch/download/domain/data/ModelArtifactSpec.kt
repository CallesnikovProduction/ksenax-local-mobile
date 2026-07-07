package com.kolesnikovprod.ksetaorch.download.domain.data

val SHA256_REGEX_PATTERN = Regex("^[a-fA-F0-9]{64}$")

/**
 * Спецификация одного скачиваемого runtime-артефакта модели.
 *
 * Описывает модельный файл как неизменяемый install-конфиг:
 * откуда скачать файл, под каким именем сохранить его локально,
 * в какой модельной директории держать артефакты, а также какие
 * размер и SHA-256 ожидаются после загрузки.
 *
 * [url] должен использовать HTTPS, потому что модельный файл является
 * доверенным runtime-артефактом приложения и не должен загружаться по
 * небезопасному каналу.
 *
 * [localFileName] — имя файла внутри директории модели. Не должно быть
 * пустым.
 *
 * [storageDirectoryName] — имя app-private директории, в которой лежат
 * файл модели и связанные с ним runtime-артефакты: cache, temporary data,
 * saved voices и другие поддиректории.
 *
 * [expectedSizeBytes] используется как быстрая проверка полноты загрузки.
 *
 * [expectedSha256] используется как строгая проверка целостности файла.
 * Значение должно быть 64-символьной hexadecimal SHA-256 строкой.
 *
 * @throws IllegalArgumentException если спецификация содержит небезопасные
 * или некорректные значения.
 *
 * @since 0.2
 */
internal data class ModelArtifactSpec(
    val url:                  String,
    val localFileName:        String,
    val storageDirectoryName: String,
    val expectedSizeBytes:    Long,
    val expectedSha256:       String,
) {
    init {
        // Скачка происходит только по защищённому каналу
        require(url.startsWith("https://")) {
            "Model artifact URL must be HTTPS"
        }
        // Без нормального имени Backend не построит путь
        require(localFileName.isNotBlank()) {
            "Local model file name must not be blank"
        }
        // Директории разные, чтобы не было каши
        require(storageDirectoryName.isNotBlank()) {
            "Model storage directory must not be blank"
        }
        // Против заглушек и модель должна быть непустой
        require(expectedSizeBytes > 0L) {
            "Expected model size must be positive"
        }
        // Корректный SHA-256
        require(expectedSha256.matches(SHA256_REGEX_PATTERN)) {
            "Expected model SHA256 must contain 64 hex characters"
        }
    }
}
