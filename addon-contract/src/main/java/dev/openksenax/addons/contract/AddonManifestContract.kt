package dev.openksenax.addons.contract

/**
 * Содержит стабильные Android Manifest-ключи публичного контракта
 * между OpenKsenax и автономными addon APK.
 *
 * Аддоны объявляют addon service, версии поддерживаемых контрактов,
 * модель исполнения и необходимые host capabilities в собственном
 * `AndroidManifest.xml`. OpenKsenax использует те же значения во время
 * обнаружения компонентов через Android [android.content.pm.PackageManager].
 *
 * Строковые значения являются частью межприложенческого протокола.
 * После публикации их нельзя переименовывать вместе с внутренними классами
 * или package names проекта. Расширение контракта должно выполняться
 * добавлением новых ключей с сохранением уже опубликованных значений.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
object AddonManifestContract {

    /**
     * Intent action, по которому OpenKsenax обнаруживает установленные
     * addon services через Android PackageManager.
     *
     * Заключён в `AndroidManifest.xml` в блоке:
     * ```xml
     * <intent-filter>
     *      <action android:name="dev.openksenax.action.ADDON_SERVICE" />
     * </intent-filter>
     * ```
     *
     * Объявление action делает service кандидатом в аддоны, но не означает,
     * что найденный компонент прошёл проверку manifest и совместимости.
     *
     * @since 0.3
     */
    const val ADDON_SERVICE_ACTION = "dev.openksenax.action.ADDON_SERVICE"

    /**
     * Ключ `meta-data` со стабильным логическим идентификатором аддона.
     *
     * Значение преобразуется в [AddonId] и не обязано совпадать с Android
     * application ID, package name или именем service.
     *
     * Resolved in `AndroidManifest.xml` by NR (e.g.):
     * ```xml
     *<meta-data
     *         android:name="dev.openksenax.addon.ID"
     *         android:value="dev.openksenax.addon.no-radar" />
     * ```
     *
     * @since 0.3
     */
    const val META_ADDON_ID = "dev.openksenax.addon.ID"

    /**
     * Ключ `meta-data` с версией общего публичного addon-протокола.
     *
     * Значение используется для проверки совместимости формата и правил
     * взаимодействия аддона с текущей версией OpenKsenax.
     *
     * Resolved in `AndroidManifest.xml` (e.g.):
     * ```xml
     * <meta-data
     *         android:name="dev.openksenax.addon.PROTOCOL_VERSION"
     *         android:value="1" />
     * ```
     *
     * @since 0.3
     */
    const val META_PROTOCOL_VERSION = "dev.openksenax.addon.PROTOCOL_VERSION"

    /**
     * Ключ `meta-data` с версией асинхронного management AIDL API,
     * реализованного addon service.
     *
     * Версия management API развивается отдельно от общей версии
     * addon-протокола.
     *
     * Resolved in `AndroidManifest.xml` (e.g.):
     * ```xml
     * <meta-data
     *         android:name="dev.openksenax.addon.MANAGEMENT_API_VERSION"
     *         android:value="1" />
     * ```
     *
     * @since 0.3
     */
    const val META_MANAGEMENT_API_VERSION = "dev.openksenax.addon.MANAGEMENT_API_VERSION"

    /**
     * Ключ `meta-data` с минимальной версией host API, необходимой
     * аддону для корректной работы.
     *
     * Аддон считается несовместимым, если текущая версия host API
     * ниже указанного значения.
     *
     * Resolved in `AndroidManifest.xml` (e.g.):
     * ```xml
     * <meta-data
     *         android:name="dev.openksenax.addon.MINIMUM_HOST_API"
     *         android:value="1" />
     * ```
     * @since 0.3
     */
    const val META_MINIMUM_HOST_API = "dev.openksenax.addon.MINIMUM_HOST_API"

    /**
     * Ключ metadata с моделью исполнения аддона.
     *
     * Значение должно соответствовать опубликованному представлению
     * одного из элементов [AddonExecutionModel].
     *
     * Resolved in `AndroidManifest.xml` (e.g.):
     * ```xml
     * <meta-data
     *         android:name="dev.openksenax.addon.EXECUTION_MODEL"
     *         android:value="AUTONOMOUS_APPLICATION" />
     * ```
     * @since 0.3
     * @see AddonExecutionModel
     */
    const val META_EXECUTION_MODEL = "dev.openksenax.addon.EXECUTION_MODEL"

    /**
     * Ключ `meta-data` со списком обязательных возможностей host.
     *
     * Идентификаторы передаются строкой через запятую и преобразуются
     * в множество [HostCapabilityId]. Если хотя бы одна обязательная
     * возможность недоступна, аддон не должен активироваться.
     *
     * Resolved in `AndroidManifest.xml` (e.g.):
     * ```xml
     * <meta-data
     *         android:name="dev.openksenax.addon.REQUIRED_CAPABILITIES"
     *         android:value="model.text-generation,model.function-calling" />
     * ```
     * @since 0.3
     * @see HostCapabilities
     */
    const val META_REQUIRED_CAPABILITIES = "dev.openksenax.addon.REQUIRED_CAPABILITIES"
}