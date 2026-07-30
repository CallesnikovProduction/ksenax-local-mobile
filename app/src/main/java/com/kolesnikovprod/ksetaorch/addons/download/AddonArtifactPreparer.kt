package com.kolesnikovprod.ksetaorch.addons.download

import dev.openksenax.addons.contract.AddonId
import com.kolesnikovprod.ksetaorch.addons.registry.RegisteredAddon
import java.io.File
import java.util.Locale

/**
 * Подготавливает опубликованный addon APK к передаче системному
 * Android installer.
 *
 * Реализация должна полностью скачать файл, проверить его размер,
 * SHA-256, package identity, version, signing certificate и manifest
 * до создания [VerifiedAddonApk].
 *
 * Обычный [File] не пересекает эту границу: успешный результат обязан
 * содержать типизированное доказательство прохождения verifier-конвейера.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal fun interface AddonArtifactPreparer {

    /**
     * Скачивает и проверяет APK зарегистрированного аддона.
     *
     * @param addon зарегистрированное описание ожидаемого аддона.
     * @param onProgress callback для публикации текущей стадии, объёма
     * загрузки и скорости.
     * @return готовый проверенный APK либо типизированную причину отказа.
     *
     * @since 0.3
     */
    suspend fun prepare(
        addon     : RegisteredAddon,
        onProgress: (AddonInstallProgress) -> Unit,
    ): AddonArtifactPreparationResult
}

/**
 * Снапшот текущего прогресса.
 * Стадия реальной установки APK до системного подтверждения Android.
 *
 * Байты описаны [Long], потому что [Int] ограничен примерно двумя гигабайтами.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal data class AddonInstallProgress(
    val stage          : AddonInstallStage,
    val downloadedBytes: Long               = 0L,
    val totalBytes     : Long?              = null, // HTTP-сервер может не дать Content-Length

    /**
     * Скорость может быть неизвестна, потому что:
     * 1. Загрузка ещё не началась;
     * 2. Недостаточно данных для замера;
     * 3. Стадия уже не [AddonInstallStage.DOWNLOADING];
     * 4. Источник не поддерживает прогресс.
     */
    val bytesPerSecond : Long?              = null,
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { total ->
                (downloadedBytes.toDouble() / total.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            }
}

/**
 * Устойчивые стадии download/verify конвейера.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal enum class AddonInstallStage {
    DOWNLOADING,          // APK скачивается
    VERIFYING_FILE,       // Проверка бинаря: SHA-256, размер, наличие
    VERIFYING_APK,        // Проверка как Android-пакета (сертификаты, манифестные метаданные)
    READY_FOR_INSTALLER,  // Проверки завершены, APK можно передавать в Android installer
}

/**
 * Результат подготовки APK.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal sealed interface AddonArtifactPreparationResult {

    data class Ready(
        val apk: VerifiedAddonApk,
    ) : AddonArtifactPreparationResult

    data class Failed(
        val reason : AddonArtifactFailure,
        val message: String?,
    ) : AddonArtifactPreparationResult
}

/**
 * Причина отказа до открытия Android installer.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal enum class AddonArtifactFailure {
    UNSAFE_DOWNLOAD_URL,
    DOWNLOAD_FAILED,
    FILE_SIZE_MISMATCH,
    SHA256_MISMATCH,
    APK_UNREADABLE,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    SIGNATURE_MISMATCH,
    MANIFEST_MISMATCH,
}

/**
 * Непрозрачное типизированное доказательство того, что addon APK прошёл
 * полный download- и verification-конвейер OpenKsenax.
 *
 * Экземпляр содержит только нормализованные и проверенные сведения
 * о загруженном APK. Обычный [File] не должен передаваться системному
 * installer напрямую: следующий слой принимает только [VerifiedAddonApk].
 *
 * Внутренний конструктор подчеркивает идею о том, что создание объекта
 * контролируется внутренней инфраструктурой.
 *
 * Класс является прямой реализацией паттерна «Proof Type» («Capability Token»):
 * наличие объекта само по себе доказывает, что кто-то внутри доверенного download-контура
 * уже проверял APK.
 *
 * @property addonId логический идентификатор проверенного аддона.
 * @property packageName Android package name внутри APK.
 * @property versionCode version code APK.
 * @property sha256 канонический SHA-256 файла без разделителей.
 * @property signingCertificateSha256 канонический SHA-256 signing
 * certificate APK.
 * @property sizeBytes фактический размер проверенного файла.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal class VerifiedAddonApk internal constructor(
    val addonId             : AddonId,
    val packageName         : String,
    val versionCode         : Long,

    // НЕ СВОЙСТВА, а параметры конструктора!
    sha256                  : String,
    signingCertificateSha256: String,

    val sizeBytes           : Long,
    internal val file       : File,
) {

    // А тут уже свойства, только нормализованные!
    val sha256                  : String = sha256.normalizeSha256()
    val signingCertificateSha256: String = signingCertificateSha256.normalizeSha256()

    init {
        require(packageName.isNotBlank())
        require(versionCode > 0L)
        require(sizeBytes > 0L)
        require(SHA_256_REGEX.matches(this.sha256))
        require(SHA_256_REGEX.matches(this.signingCertificateSha256))
    }

    private companion object {

        /**
         * Шаблон требует ровно 64 символа и только A-F и 0-9
         */
        val SHA_256_REGEX = Regex("^[A-F0-9]{64}$")
    }
}

internal fun String.normalizeSha256(): String {
    return replace(":", "")
        .trim()
        .uppercase(Locale.ROOT) // независимость от языка устройства
}
