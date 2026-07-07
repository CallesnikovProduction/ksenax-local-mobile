package com.kolesnikovprod.ksetaorch.download.domain.usecases

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.platform.FunctionGemmaDownloadGateway

/**
 * Публичный use case установки FunctionGemma 270M Mobile Actions.
 *
 * Устанавливает готовый Q8 `.litertlm` артефакт в
 * `models/functiongemma-270m`. Модель предназначена для локального выбора
 * Android-действий, а не для обычного диалога.
 *
 * @since 0.3
 */
class KsenaxFunctionGemmaInstallUseCase private constructor(
    private val installer: SingleFileInstallDelegate,
) : KsenaxModelInstallUseCase by installer {

    constructor(
        context: Context,
        downloadGateway: KsenaxDownloadGateway =
            FunctionGemmaDownloadGateway(context.applicationContext),
    ) : this(
        installer = SingleFileInstallDelegate(
            context = context,
            installTarget = KsenaxInstallTarget.FUNCTION_GEMMA_270M,
            downloadGateway = downloadGateway,
        ),
    )

    fun getRuntimeCachePath(): String = installer.getRuntimeCachePath()
}
