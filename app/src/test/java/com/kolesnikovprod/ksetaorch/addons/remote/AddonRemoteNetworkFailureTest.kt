package com.kolesnikovprod.ksetaorch.addons.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonRemoteNetworkFailureTest {

    @Test
    fun recognizesDirectAndWrappedConnectivityFailures() {
        assertTrue(UnknownHostException().isNetworkUnavailable())
        assertTrue(
            IOException(
                "outer",
                SocketTimeoutException("timed out"),
            ).isNetworkUnavailable(),
        )
    }

    @Test
    fun doesNotMislabelGenericTransferFailureAsOffline() {
        assertFalse(
            IOException("Cannot finalize downloaded file")
                .isNetworkUnavailable(),
        )
    }
}
