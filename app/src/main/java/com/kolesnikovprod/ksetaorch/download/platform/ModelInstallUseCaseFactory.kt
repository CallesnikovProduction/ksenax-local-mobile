package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxFunctionGemmaInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxGemma4E2BInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxVoskRuSmallInstallUseCase

/**
 * Background composition root для target-specific install use case.
 *
 * Android Worker нельзя снабдить объектами ViewModel composition tree после
 * смерти процесса, поэтому он восстанавливает только нужный use case по
 * стабильному [KsenaxInstallTarget].
 *
 * @since 0.3
 */
internal object ModelInstallUseCaseFactory {

    fun create(
        context: Context,
        installTarget: KsenaxInstallTarget,
    ): KsenaxModelInstallUseCase {
        val appContext = context.applicationContext

        return when (installTarget) {
            KsenaxInstallTarget.GEMMA_4_E2B ->
                KsenaxGemma4E2BInstallUseCase(appContext)

            KsenaxInstallTarget.FUNCTION_GEMMA_270M ->
                KsenaxFunctionGemmaInstallUseCase(appContext)

            KsenaxInstallTarget.VOSK_RU_SMALL ->
                KsenaxVoskRuSmallInstallUseCase(appContext)
        }
    }

    fun targetById(targetId: String): KsenaxInstallTarget? {
        return KsenaxInstallTarget.entries.firstOrNull { target ->
            target.id == targetId
        }
    }
}
