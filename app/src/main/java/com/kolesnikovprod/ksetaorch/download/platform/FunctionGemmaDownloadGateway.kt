package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.ModelArtifactSpec

/**
 * Внутренний gateway FunctionGemma 270M Mobile Actions в формате LiteRT-LM.
 *
 * Артефакт закреплён на неизменяемом commit Hugging Face и проходит проверку
 * точного размера и SHA256 перед использованием.
 *
 * @since 0.3
 */
internal class FunctionGemmaDownloadGateway(
    context: Context,
) : SingleFileModelDownloadGateway(
    context = context,
    artifact = ModelArtifactSpec(
        url                  = FUNCTION_GEMMA_URL,
        localFileName        = FUNCTION_GEMMA_FILE_NAME,
        storageDirectoryName = KsenaxInstallTarget.FUNCTION_GEMMA_270M.storageDirectoryName,
        expectedSizeBytes    = FUNCTION_GEMMA_SIZE_BYTES,
        expectedSha256       = FUNCTION_GEMMA_SHA256,
    ),
) {
    companion object {
        const val FUNCTION_GEMMA_REVISION =
            "82d0f654a6270c518d16c600edce3136221b3347"

        const val FUNCTION_GEMMA_URL =
            "https://huggingface.co/litert-community/" +
                "functiongemma-mobile-actions_q8_ekv1024.litertlm/resolve/" +
                "$FUNCTION_GEMMA_REVISION/mobile-actions_q8_ekv1024.litertlm"

        const val FUNCTION_GEMMA_FILE_NAME =
            "openksenax_functiongemma_270m_mobile_actions_q8_ekv1024.litertlm"

        const val FUNCTION_GEMMA_SIZE_BYTES = 284_426_240L

        const val FUNCTION_GEMMA_SHA256 =
            "92109695f911d1872fa8ae07c1e3ff0ed70f2c3d1690d410ec6db8587c2ab409"
    }
}
