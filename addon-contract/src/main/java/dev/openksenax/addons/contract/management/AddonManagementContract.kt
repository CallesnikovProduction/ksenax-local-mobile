package dev.openksenax.addons.contract.management

/**
 * Содержит стабильные параметры асинхронного management API между
 * OpenKsenax и автономными addon APK.
 *
 * Management API является управляющим контуром и предназначен только для:
 * - получения текущего runtime-состояния аддона;
 * - запроса включения или выключения runtime;
 * - получения [android.app.PendingIntent] для открытия UI аддона;
 * - отмены ранее отправленного асинхронного запроса.
 *
 * Фоновая бизнес-логика аддона и capability-вызовы не должны выполняться
 * через management-контракт.
 *
 * Объект фиксирует версию AIDL API и параметры аутентификации Binder-caller.
 * Реальная проверка вызывающего процесса выполняется внутри реализации
 * management Stub в addon APK.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
object AddonManagementContract {

    /**
     * Текущая версия асинхронного management AIDL API.
     *
     * Сопоставляется со значением, объявленным аддоном через
     * [dev.openksenax.addons.contract.AddonManifestContract.META_MANAGEMENT_API_VERSION],
     * до установления Binder-соединения.
     *
     * С этой версией сверяется OpenKsenax, когда аддон в `AndroidManifest.xml` объявляет, например:
     * ```xml
     * <meta-data
     *     android:name="dev.openksenax.addon.MANAGEMENT_API_VERSION"
     *     android:value="1" />
     * ```
     *
     * @since 0.3
     */
    const val CURRENT_API_VERSION: Int = 1

    /**
     * Permission, которой автономный addon service защищает входящие
     * Binder-подключения.
     *
     * Permission проверяется Android у вызывающего приложения. Она является
     * первым уровнем защиты и не заменяет проверку Binder caller UID,
     * package name и signing certificate внутри каждого удалённого метода.
     *
     * Addon APK может быть подписан собственным ключом, отличным от ключа
     * OpenKsenax.
     *
     * Пример декларации сервиса аддоном:
     * ```xml
     * <service
     *     android:name=".management.AddonManagementService"
     *     android:exported="true"
     *     android:permission=
     *         "dev.openksenax.permission.BIND_ADDON_MANAGEMENT">
     *
     *     <intent-filter>
     *         <action android:name="dev.openksenax.action.ADDON_SERVICE" />
     *     </intent-filter>
     * </service>
     * ```
     *
     * @since 0.3
     */
    const val BIND_PERMISSION =
        "dev.openksenax.permission.BIND_ADDON_MANAGEMENT"
}
