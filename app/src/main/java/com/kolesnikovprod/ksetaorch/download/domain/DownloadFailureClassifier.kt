package com.kolesnikovprod.ksetaorch.download.domain

import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallFailure

/** Перевод HTTP/platform reason в устойчивую причину отказа. @since 0.4 */
internal object DownloadFailureClassifier {
    fun classify(reason: Int?): KsenaxInstallFailure = when (reason) {
        -2 -> KsenaxInstallFailure.SOURCE_CHANGED
        401, 403 -> KsenaxInstallFailure.AUTHORIZATION
        404, 410 -> KsenaxInstallFailure.NOT_FOUND
        429 -> KsenaxInstallFailure.RATE_LIMITED
        in 500..599 -> KsenaxInstallFailure.SERVER
        1006 -> KsenaxInstallFailure.INSUFFICIENT_STORAGE
        1001, 1007 -> KsenaxInstallFailure.STORAGE
        1004, 1008 -> KsenaxInstallFailure.NETWORK
        else -> KsenaxInstallFailure.UNKNOWN
    }
}
