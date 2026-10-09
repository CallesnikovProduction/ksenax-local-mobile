package com.kolesnikovprod.ksetaorch.live

import android.content.Context
import android.os.Bundle
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.security.MessageDigest

/** Проверяет отдельный файл кандидата; не допускает тесты на рабочем файле модели.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class LiveCandidateModel private constructor(val file: File, val sha256: String) {
    fun engineConfig(context: Context, contextTokens: Int = 1024, backend: String = "CPU"): EngineConfig {
        require(contextTokens in setOf(1024, 2048))
        require(backend in setOf("CPU", "GPU"))
        val cache = "live-action-candidate/$sha256" + if (contextTokens == 1024 && backend == "CPU") "" else "/$backend-$contextTokens"
        return EngineConfig(modelPath = file.path, backend = if (backend == "CPU") Backend.CPU() else Backend.GPU(),
            audioBackend = null, maxNumTokens = contextTokens,
            cacheDir = File(context.cacheDir, cache).apply { mkdirs() }.path)
    }

    companion object {
        fun resolve(context: Context, args: Bundle): LiveCandidateModel {
            val root = File(context.getExternalFilesDir(null), "live-routing-candidates").canonicalFile
            val file = File(requireNotNull(args.getString("modelPath"))).canonicalFile
            require(file.toPath().startsWith(root.toPath()) && file.isFile && file.canRead())
            val expected = requireNotNull(args.getString("sha256")).lowercase()
            require(expected.matches(Regex("[0-9a-f]{64}")))
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Candidate SHA-256 mismatch" }
            return LiveCandidateModel(file, expected)
        }
    }
}
