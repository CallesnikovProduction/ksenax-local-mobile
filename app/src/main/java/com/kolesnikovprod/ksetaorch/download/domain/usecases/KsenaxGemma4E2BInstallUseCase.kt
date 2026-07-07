package com.kolesnikovprod.ksetaorch.download.domain.usecases

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxDownloadGateway
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.platform.Gemma4E2BDownloadGateway

/**
 * Публичный use case установки основной модели Gemma-4 (E2B, 2 млрд параметров) Ksenax.
 *
 * Идеальный пример фасада: снаружи вроде бы Gemma-ориентированный use case,
 * но внутри практически всем рулит делегат.
 *
 * @constructor закрытый, поскольку снаружи нельзя передать
 * произвольный [SingleFileInstallDelegate]
 *
 * @property installer Делегат закачки одного файла.
 *
 * @since 0.2
 */
class KsenaxGemma4E2BInstallUseCase private constructor(
    private val installer: SingleFileInstallDelegate,
) : KsenaxModelInstallUseCase by installer {

    /**
     * Доступный конструктор для того, чтобы проинициализировать внутренний делегат,
     * путём которого будет устанавливаться файл модели.
     *
     * @since 0.2
     */
    constructor(
        context: Context,
        downloadGateway: KsenaxDownloadGateway =
            Gemma4E2BDownloadGateway(context.applicationContext),
    ) : this(
        installer = SingleFileInstallDelegate(
            context             = context,
            installTarget       = KsenaxInstallTarget.GEMMA_4_E2B,
            downloadGateway     = downloadGateway,
            legacyDownloadIdKey = LEGACY_DOWNLOAD_ID_KEY,
        ),
    )

    fun deleteGemma4E2BFile(): Boolean = deleteLocalArtifacts()

    fun clearGemma4E2BSavedVoices() {
        installer.clearSavedVoices()
    }

    fun getGemma4E2BModelPath():          String = installer.getModelPath()

    fun getGemma4E2BCacheDirPath():       String = installer.getRuntimeCachePath()

    fun getGemma4E2BSavedVoicesDirPath(): String = installer.getSavedVoicesPath()

    private companion object {
        const val LEGACY_DOWNLOAD_ID_KEY = "active_model_download_id"
    }
}
