package com.kolesnikovprod.ksetaorch.addons.storage.contract

import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import dev.openksenax.addons.contract.AddonId

/**
 * Порт локального хранилища файловой проекции реально установленных
 * addon APK.
 *
 * Хранилище не определяет факт установки самостоятельно. Источником
 * installation state остаётся registry, построенный на данных Android
 * PackageManager.
 *
 * Реализация синхронизирует host-owned файлы с текущим состоянием registry:
 * создаёт или обновляет локальные metadata, переносит подтверждённые
 * временные APK и banner-артефакты в финальные каталоги и удаляет файлы
 * аддонов, которые подтверждённо исчезли с устройства.
 *
 * Наличие скачанного APK во временной директории само по себе не означает,
 * что аддон установлен.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
internal interface AddonLocalStore {

    /**
     * Приводит локальное addon-хранилище в соответствие с текущим состоянием
     * registry («reconcile» - «согласовать»).
     *
     * Обновляет metadata реально установленных аддонов, переносит подходящие
     * временные APK и banner-артефакты в финальные каталоги и, только при свежем
     * discovery-состоянии, удаляет локальные каталоги отсутствующих packages.
     *
     * @param registryState актуальный снимок addon registry.
     * @return локальные записи всех подтверждённо установленных аддонов.
     *
     * @since 0.3
     */
    suspend fun reconcile(
        registryState: AddonRegistryState,
    ): List<InstalledAddonRecord>

    /**
     * Читает сохранённую локальную metadata-карточку установленного package.
     *
     * Повреждённый, отсутствующий или относящийся к другому package файл
     * трактуется как отсутствие записи.
     *
     * @param packageName Android package name аддона.
     * @return сохранённая запись либо `null`.
     *
     * @since 0.3
     */
    suspend fun read(
        packageName: String,
    ): InstalledAddonRecord?

    /**
     * Удаляет host-owned файлы подтверждённо удалённого addon APK.
     *
     * Метод очищает как финальный package-каталог, так и оставшиеся временные
     * download-артефакты, относящиеся к [addonId].
     *
     * @since 0.3
     */
    suspend fun remove(
        addonId: AddonId,
        packageName: String,
    )
}
