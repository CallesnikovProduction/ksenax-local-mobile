package com.kolesnikovprod.ksetaorch.download.platform

import android.content.Context
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.ModelArtifactSpec

/**
 * Внутренний gateway закреплённого Gemma 4 E2B `.litertlm` артефакта.
 *
 * Общая файловая и DownloadManager-механика живёт в
 * [SingleFileModelDownloadGateway], здесь остаются только данные конкретной
 * модели.
 *
 * @since 0.3
 */
internal class Gemma4E2BDownloadGateway(
    context: Context,
) : SingleFileModelDownloadGateway(
    context = context,
    artifact = ModelArtifactSpec(
        url                  = GEMMA_4_E2B_URL,
        localFileName        = GEMMA_4_E2B_FILE_NAME,
        storageDirectoryName = KsenaxInstallTarget.GEMMA_4_E2B.storageDirectoryName,
        expectedSizeBytes    = GEMMA_4_E2B_SIZE_BYTES,
        expectedSha256       = GEMMA_4_E2B_SHA256,
    ),
) {
    companion object {
        const val GEMMA_4_E2B_URL =
            "https://huggingface.co/litert-community/" +
                "gemma-4-E2B-it-litert-lm/resolve/361a401/" +
                "gemma-4-E2B-it.litertlm"

        const val GEMMA_4_E2B_FILE_NAME =
            "openksenax_gemma_4_e2b_it.litertlm"

        const val GEMMA_4_E2B_SIZE_BYTES = 2_588_147_712L

        const val GEMMA_4_E2B_SHA256 =
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    }
}
