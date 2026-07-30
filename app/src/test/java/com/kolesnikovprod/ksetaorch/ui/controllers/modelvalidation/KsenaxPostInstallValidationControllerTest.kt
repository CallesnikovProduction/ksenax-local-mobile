package com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadPolicy
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadTaskSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxPostInstallValidationControllerTest {

    @Test
    fun successfulValidationRunsAllStagesAndCachesResult() = runBlocking {
        val registry = KsenaxModelVerificationSessionRegistry().apply {
            onAppForegrounded()
        }
        var reachabilityCalls = 0
        val controller = KsenaxPostInstallValidationController(
            installUseCase = FakeInstallUseCase(),
            sessionRegistry = registry,
            reachabilityProbe = { reachabilityCalls += 1 },
        )
        val stages = mutableListOf<KsenaxPostInstallValidationStage>()

        val result = controller.verify(stages::add)

        assertSame(KsenaxPostInstallValidationResult.Success, result)
        assertEquals(KsenaxPostInstallValidationStage.entries, stages)
        assertEquals(1, reachabilityCalls)
        assertTrue(registry.isVerified(KsenaxInstallTarget.GEMMA_4_E2B.id))
    }

    @Test
    fun backgroundedValidationCannotRestoreSessionCache() = runBlocking {
        val registry = KsenaxModelVerificationSessionRegistry().apply {
            onAppForegrounded()
        }
        val controller = KsenaxPostInstallValidationController(
            installUseCase = FakeInstallUseCase(),
            sessionRegistry = registry,
            reachabilityProbe = registry::onAppBackgrounded,
        )

        val result = controller.verify {}

        assertSame(KsenaxPostInstallValidationResult.SessionExpired, result)
        assertFalse(registry.isVerified(KsenaxInstallTarget.GEMMA_4_E2B.id))
    }

    private class FakeInstallUseCase(
        private val hasCandidate: Boolean = true,
        private val isValid: Boolean = true,
    ) : KsenaxModelInstallUseCase {
        override val installTarget = KsenaxInstallTarget.GEMMA_4_E2B

        override fun startDownloadAndSave(policy: KsenaxDownloadPolicy) = 1L
        override fun getSavedDownloadId() = NO_DOWNLOAD_ID
        override fun cancelDownload(downloadId: Long) = Unit
        override fun clearArtifacts() = Unit
        override fun clearSavedDownloadId() = Unit
        override fun deleteLocalArtifacts() = true
        override suspend fun hasInstallCandidate() = hasCandidate
        override suspend fun prepareInstallCandidate() = true
        override suspend fun hasValidInstallation() = isValid
        override fun queryDownloadSnapshot(
            downloadId: Long,
        ): KsenaxDownloadTaskSnapshot? = null
        override fun getInstalledPath() = "/models/gemma"
    }
}
