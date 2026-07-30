package com.kolesnikovprod.ksetaorch.addons.discovery

import dev.openksenax.addons.contract.AddonExecutionModel
import dev.openksenax.addons.contract.AddonId
import dev.openksenax.addons.contract.HostCapabilityId

/**
 * Нормализованное описание addon APK, обнаруженного среди установленных
 * Android packages.
 *
 * Объект формируется обнаруживающим (discover) слоем на основании данных Android
 * [android.content.pm.PackageManager], manifest metadata, информации
 * о service и signing certificates.
 *
 * Наличие объекта подтверждает, что кандидат был найден и его обязательные
 * manifest-поля удалось преобразовать в доменные типы. Оно не означает,
 * что аддон присутствует в официальном каталоге, доверен пользователем,
 * подписан разрешённым сертификатом, совместим с текущим host API или
 * готов к Binder-подключению.
 *
 * Полный поток для обнаружения:
 * ```text
 * PackageManager.queryIntentServices()
 *         ↓
 * ResolveInfo / ServiceInfo
 *         ↓
 * читать package, service, UID, version
 *         ↓
 * читать manifest metadata
 *         ↓
 * AddonId(...)
 * HostCapabilityId(...)
 * AddonExecutionModel(...)
 *         ↓
 * читать SigningInfo
 *         ↓
 * SHA-256 fingerprints
 *         ↓
 * DiscoveredAddon
 * ```
 * При неудаче в одном из пунктов аддон объявляется отклонённым (rejected).
 *
 * @property addonId Стабильный логический идентификатор аддона.
 * @property uid Android UID установленного приложения.
 * @property packageName Название аддон-APK у Android package.
 * @property serviceClassName полное имя обнаруженного management service.
 * @property versionCode числовая версия установленного APK.
 * @property versionName человекочитаемое имя версии, если оно объявлено.
 * @property protocolVersion версия публичного addon-протокола.
 * @property managementApiVersion версия management AIDL API аддона.
 * @property minimumHostApi минимальная версия host API, необходимая аддону.
 * @property executionModel архитектурная модель исполнения аддона.
 * @property requiredHostCapabilities обязательные возможности host,
 * необходимые аддону для работы.
 * @property signingCertificateSha256 SHA-256 fingerprints текущих и
 * исторических signing certificates APK.
 * @property firstInstallTimeEpochMillis Время первой установки package,
 * сообщённое Android PackageManager.
 * @property lastUpdateTimeEpochMillis Время последнего обновления package,
 * сообщённое Android PackageManager.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class DiscoveredAddon(
    /**
     * Например,
     * ```
     * dev.openksenax.addon.no-radar
     * ```
     */
    val addonId:                  AddonId,

    /**
     * Требуется для идентификации Binder-вызовщика, проверок безопасности и диагностики.
     * Может быть удален и изменен при переустановке/удалении приложения/аддона
     */
    val uid:                      Int,

    /**
     * Требуется для явного `ComponentName`, [android.content.pm.PackageManager]-запросов.
     * Может НЕ совпадать с [addonId].
     */
    val packageName:              String,

    /**
     * Вместе с пакетом имя формирует точный Binder endpoint.
     */
    val serviceClassName:         String,
    val versionCode:              Long,

    /**
     * По нему нельзя запускать сортировку
     */
    val versionName:              String?,
    val protocolVersion:          Int,
    val managementApiVersion:     Int,
    val minimumHostApi:           Int,
    val executionModel:           AddonExecutionModel,
    val requiredHostCapabilities: Set<HostCapabilityId>,

    /**
     * SHA-256 fingerprints signing certificates установленного addon APK.
     *
     * Множество может содержать текущий сертификат и элементы signing
     * history при поддерживаемой Android ротации ключей. Значения должны
     * храниться в едином каноническом формате, чтобы сравнение с trust
     * policy не зависело от регистра или разделителей.
     *
     * @since 0.3
     */
    val signingCertificateSha256: Set<String>,
    val firstInstallTimeEpochMillis: Long = 0L,
    val lastUpdateTimeEpochMillis: Long = 0L,
) {
    init {
        require(uid >= 0) {
            "uid must not be negative"
        }
        require(packageName.isNotBlank()) {
            "packageName must not be blank"
        }
        require(serviceClassName.isNotBlank()) {
            "serviceClassName must not be blank"
        }
        require(versionCode > 0) {
            "versionCode must be positive"
        }
        require(protocolVersion > 0) {
            "protocolVersion must be positive"
        }
        require(managementApiVersion > 0) {
            "managementApiVersion must be positive"
        }
        require(minimumHostApi > 0) {
            "minimumHostApi must be positive"
        }
        require(firstInstallTimeEpochMillis >= 0L) {
            "firstInstallTimeEpochMillis must not be negative"
        }
        require(lastUpdateTimeEpochMillis >= 0L) {
            "lastUpdateTimeEpochMillis must not be negative"
        }
    }
}
