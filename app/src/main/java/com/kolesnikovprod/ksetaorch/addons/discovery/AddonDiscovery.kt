package com.kolesnikovprod.ksetaorch.addons.discovery

/**
 * Порт обнаружения установленных addon APK.
 *
 * Скрывает Android [android.content.pm.PackageManager] и предоставляет
 * остальной системе нормализованный снимок discovery-процесса.
 *
 * Реализация должна найти Android services, объявляющие публичный
 * OpenKsenax addon action, прочитать их метаданные из манифеста и разделить
 * кандидатов на успешно разобранные и отклонённые.
 *
 * Ошибка отдельного APK не должна прерывать всё сканирование. Такой кандидат
 * должен быть отражён в [AddonDiscoverySnapshot] как отклонённый. Исключение
 * допускается только при невозможности выполнить discovery-процесс целиком.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
interface AddonDiscovery {

    /**
     * Сканирует установленные Android packages и возвращает атомарный снимок
     * найденных и отклонённых addon-кандидатов.
     *
     * Метод не гарантирует, что обнаруженные аддоны доверены, совместимы
     * с текущим host API или готовы к запуску. Эти проверки выполняются
     * последующими слоями системы.
     *
     * @return результат одного полного прохода discovery.
     * @throws AddonDiscoveryException если Android PackageManager или
     * инфраструктура discovery не позволили выполнить сканирование целиком.
     *
     * @since 0.3
     */
    suspend fun discover(): AddonDiscoverySnapshot
}