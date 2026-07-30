package com.kolesnikovprod.ksetaorch.addons.discovery

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

/**
 * Повторно проверяет идентичность установленного addon APK перед предоставлением
 * чувствительного доступа к management- или model-provider API.
 *
 * Проверка должна выполняться непосредственно перед использованием ранее
 * обнаруженного аддона, поскольку пакет мог быть удалён, переустановлен,
 * обновлён или заменён после discovery-прохода.
 *
 * Идентичность считается совпавшей, если текущий Android UID-пакет совпадает
 * с ожидаемым UID и хотя бы один текущий или исторический signing certificate
 * совпадает с ожидаемым SHA-256 fingerprint.
 *
 * Реализация может выполнять PackageManager IO и не должна вызываться
 * с главного потока.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
fun interface AddonPackageIdentityVerifier {

    /**
     * Проверяет соответствие текущей идентичности установленного пакета
     * ранее зафиксированным данным обнаружением.
     *
     * @param packageName Пакетное Android имя проверяемого addon APK.
     * @param expectedUid UID package, сохранённый во время обнаружения.
     * @param expectedSigningCertificateSha256 Ожидаемые SHA-256 fingerprints
     * signing certificates.
     * @return `true`, если UID и signing identity совпадают.
     *
     * @since 0.3
     */
    fun matches(
        packageName                     : String,
        expectedUid                     : Int,
        expectedSigningCertificateSha256: Set<String>,
    ): Boolean
}

/**
 * Android-реализация живой проверки идентичности addon APK через
 * [PackageManager].
 *
 * Повторно читает актуальные пакетные метаданные и signing information,
 * нормализует SHA-256 fingerprints и сравнивает их с данными, сохранёнными
 * во время обнаружения.
 *
 * Любая невозможность надёжно подтвердить идентичность приводит к `false`.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
class PackageManagerAddonIdentityVerifier(
    private val packageManager: PackageManager,
) : AddonPackageIdentityVerifier {

    override fun matches(
        packageName                     : String,
        expectedUid                     : Int,
        expectedSigningCertificateSha256: Set<String>,
    ): Boolean {
        return try {
            // повторный запрос к Android прямо сейчас
            val packageInfo = packageManager.readAddonPackageInfo(packageName)
            // отсутствие applicationInfo = невозможна проверка идентичности
            val currentUid = packageInfo.applicationInfo?.uid ?: return false
            // нормализация ожидаемых сертификатов
            val expectedSigners = expectedSigningCertificateSha256
                .mapTo(mutableSetOf(), String::normalizeSha256)
            // чтение текущих сертификатов
            val currentSigners = packageInfo
                .addonSigningCertificateSha256()
                .mapTo(mutableSetOf(), String::normalizeSha256)

            currentUid == expectedUid &&
                currentSigners.any(expectedSigners::contains)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}

/**
 * Читает [PackageInfo] addon APK вместе с signing information,
 * используя совместимый API для текущей версии Android.
 *
 * @since 0.3
 */
internal fun PackageManager.readAddonPackageInfo(
    packageName: String,
): PackageInfo {
    val flags = PackageManager.GET_SIGNING_CERTIFICATES

    // скрывается различие версий Android API, поскольку
    // 13+: появляются типизированные флаги
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong()),
        )
    } else {
        // старый путь через обычный Int
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }
}

/**
 * Возвращает канонические SHA-256 fingerprints signing certificates APK.
 *
 * Для package с несколькими текущими signer-ами используются
 * [android.content.pm.SigningInfo.getApkContentsSigners]. Для package
 * с одним signer-ом используется signing history, чтобы учитывать
 * поддерживаемую Android ротацию ключей.
 *
 * Fingerprints возвращаются в верхнем регистре без разделителей.
 *
 * @since 0.3
 */
internal fun PackageInfo.addonSigningCertificateSha256(): Set<String> {
    val packageSigningInfo = signingInfo ?: return emptySet()
    val signatures: Array<out Signature> =
        if (packageSigningInfo.hasMultipleSigners()) {
            packageSigningInfo.apkContentsSigners
        } else {
            packageSigningInfo.signingCertificateHistory
        }

    return signatures
        .mapTo(mutableSetOf()) { signature ->
            MessageDigest
                .getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString(separator = "") { byte ->
                    String.format(
                        Locale.ROOT,
                        "%02X",
                        byte.toInt() and 0xFF,
                    )
                }
        }
}

/**
 * Приводит фингерпринт к единому виду:
 *
 * Было:
 * ```
 * "aa:bb:cc"
 * ```
 *
 * Стало:
 * ```
 * "AABBCC"
 * ```
 */
private fun String.normalizeSha256() = replace(":", "")
        .trim()
        .uppercase(Locale.ROOT)