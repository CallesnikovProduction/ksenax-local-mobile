package com.kolesnikovprod.ksetaorch.addons.discovery

/**
 * Базовое исключение критического сбоя discovery-процесса.
 *
 * Используется только тогда, когда невозможно получить корректный снимок
 * сканирования в целом. Ошибки отдельных addon APK не должны выбрасываться
 * наружу: такие кандидаты помещаются в коллекцию отклонённых элементов
 * внутри [AddonDiscoverySnapshot].
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
sealed class AddonDiscoveryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * Обозначает невозможность выполнить системное сканирование установленных
     * addon services через Android PackageManager.
     *
     * Ошибка относится ко всему discovery-проходу, а не к одному конкретному
     * APK.
     *
     * @property cause исходная платформенная или инфраструктурная ошибка.
     *
     * @since 0.3
     */
    class ScanFailed(cause: Throwable) : AddonDiscoveryException(
        message = "Failed to scan installed addon packages",
        cause   = cause,
    )
}