package com.kolesnikovprod.ksetaorch.addons.discovery

import dev.openksenax.addons.contract.AddonId

/**
 * Неизменяемый снимок результата одного полного discovery-прохода
 * по установленным Android packages.
 *
 * Содержит отдельно успешно разобранные аддоны и service-кандидаты,
 * которые объявили публичный addon action, но не прошли базовую проверку
 * Android-компонента, manifest metadata или signing information.
 *
 * Ошибка одного стороннего APK не должна прерывать обнаружение остальных
 * аддонов. Поэтому локальные ошибки кандидатов представлены через
 * [rejectedCandidates], а исключение всего discovery-процесса используется
 * только при невозможности выполнить системное сканирование целиком.
 *
 * Снимок описывает состояние устройства на момент [scannedAtEpochMillis]
 * и может устареть после установки, удаления или обновления APK.
 *
 * @property addons корректно обнаруженные и нормализованные addon APK.
 * @property rejectedCandidates найденные service-кандидаты, отклонённые
 * во время базового разбора.
 * @property scannedAtEpochMillis время завершения или формирования снимка
 * в миллисекундах Unix epoch.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class AddonDiscoverySnapshot(
    val addons:               List<DiscoveredAddon>,
    val rejectedCandidates:   List<RejectedAddonCandidate>,
    val scannedAtEpochMillis: Long,
)

/**
 * Android service, обнаруженный по публичному addon action, но не прошедший
 * базовый разбор или валидацию discovery-контура.
 *
 * Объект сохраняет максимально доступные координаты проблемного компонента
 * и типизированную причину отклонения. Поля package и service могут
 * отсутствовать, если Android PackageManager не предоставил соответствующую
 * информацию.
 *
 * Отклонённый кандидат не является [DiscoveredAddon] и не должен передаваться
 * в trust, compatibility, registry или coordination-контуры.
 *
 * @property packageName Android package name кандидата, если удалось получить.
 * @property serviceClassName имя обнаруженного service, если оно доступно.
 * @property reason причина, по которой кандидат не был принят discovery-слоем.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class RejectedAddonCandidate(
    val packageName:      String?,
    val serviceClassName: String?,
    val reason:           AddonDiscoveryRejectionReason,
)

/**
 * Типизированная причина отклонения Android service во время обнаружения.
 *
 * Причины этого семейства относятся к одному конкретному кандидату
 * и не должны прерывать полный проход сканирования. Они предназначены
 * для диагностики, журналирования и отображения проблем установленных
 * APK аддонов.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
sealed interface AddonDiscoveryRejectionReason {

    /**
     * PackageManager вернул кандидата без обязательного [android.content.pm.ServiceInfo].
     *
     * Без ServiceInfo невозможно определить компонент, прочитать метаданные
     * и безопасно продолжить разбор.
     *
     * @since 0.3
     */
    data object MissingServiceInfo : AddonDiscoveryRejectionReason

    /**
     * Обнаруженный сервис аддона недоступен другим приложениям.
     *
     * Автономный addon APK обязан экспортировать management service,
     * иначе OpenKsenax не сможет подключиться к нему через bindService().
     *
     * @since 0.3
     */
    data object ServiceNotExported : AddonDiscoveryRejectionReason

    /**
     * Экспортированный addon service не защищён ожидаемой стабильным разрешением
     * management-контракта.
     *
     * Открытый сервис без требуемого разрешения создаёт небезопасную
     * Binder-точку входа для сторонних приложений.
     *
     * @property actualPermission фактически объявленное разрешение сервиса
     * или `null`, если защита отсутствует.
     *
     * @since 0.3
     */
    data class InvalidManagementPermission(
        val actualPermission: String?
    ) : AddonDiscoveryRejectionReason

    /**
     * В метаданных `AndroidManifest.xml` отсутствует обязательное поле публичного
     * addon-контракта.
     *
     * @property key стабильный manifest-ключ отсутствующего значения.
     *
     * @since 0.3
     */
    data class MissingMetadata(val key: String) : AddonDiscoveryRejectionReason

    /**
     * Manifest metadata содержит значение, которое невозможно преобразовать
     * в ожидаемый доменный или протокольный тип.
     *
     * @property key manifest-ключ невалидного поля.
     * @property value исходное значение, если его удалось прочитать.
     *
     * @since 0.3
     */
    data class InvalidMetadata(
        val key:   String,
        val value: String?
    ) : AddonDiscoveryRejectionReason

    /**
     * PackageManager не предоставил signing certificates APK либо их
     * fingerprints невозможно было надёжно вычислить.
     *
     * Без signing information кандидат нельзя передавать в последующую
     * проверку доверия.
     *
     * @since 0.3
     */
    data object SigningCertificateUnavailable : AddonDiscoveryRejectionReason

    /**
     * Один установленный Android package объявил более одного addon service.
     *
     * В текущей версии протокола один APK может представлять только один
     * логический аддон и одну management-точку входа.
     *
     * @property packageName package, нарушивший ограничение.
     *
     * @since 0.3
     */
    data class MultipleAddonServicesInPackage(
        val packageName: String
    ) : AddonDiscoveryRejectionReason

    /**
     * Несколько разных APK заявили один и тот же стабильный [AddonId].
     *
     * Discovery не может считать такие кандидаты одновременно валидными,
     * поскольку дальнейшая адресация по `addonId` стала бы неоднозначной.
     *
     * @property addonId конфликтующий логический идентификатор аддона.
     *
     * @since 0.3
     */
    data class DuplicateAddonId(
        val addonId: AddonId
    ) : AddonDiscoveryRejectionReason

    /**
     * При обработке конкретного кандидата произошла непредвиденная ошибка,
     * не соответствующая более точной категории отклонения.
     *
     * Используется как защитный fallback, чтобы повреждённый или необычный
     * APK не прервал обнаружение остальных аддонов.
     *
     * @property message безопасное диагностическое описание ошибки.
     *
     * @since 0.3
     */
    data class UnexpectedFailure(
        val message: String?
    ) : AddonDiscoveryRejectionReason
}