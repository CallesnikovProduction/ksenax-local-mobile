package com.kolesnikovprod.ksetaorch.download

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kolesnikovprod.ksetaorch.download.domain.FileIntegrityVerifier
import com.kolesnikovprod.ksetaorch.download.domain.FileTreeDeleter
import com.kolesnikovprod.ksetaorch.download.domain.SafeZipExtractor
import com.kolesnikovprod.ksetaorch.download.domain.ArtifactFileInstaller
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.platform.AndroidModelDownloadBackend
import com.kolesnikovprod.ksetaorch.download.platform.VoskRuSmallDownloadGateway
import com.kolesnikovprod.ksetaorch.download.platform.SingleFileModelDownloadGateway
import com.kolesnikovprod.ksetaorch.download.domain.data.ModelArtifactSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Isolated DownloadManager fixture; never touches installed model targets. @since 0.4 */
@RunWith(AndroidJUnit4::class)
class DownloadManagerDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixture get() = File(context.cacheDir, "spec002-download-fixture.txt")

    @Test fun atomicPromotionOnAndroidExternalStorage() {
        val backend = AndroidModelDownloadBackend(context)
        val directory = "spec002-test-${UUID.randomUUID()}"
        val root = backend.getModelDirectory(directory)
        check(root.mkdirs())
        try {
            val staging = File(root, "candidate.partial").apply { writeText("verified-new") }
            val installed = File(root, "model.litertlm").apply { writeText("old") }
            assertTrue(ArtifactFileInstaller.promote(staging, installed))
            assertEquals("verified-new", installed.readText())
        } finally {
            FileTreeDeleter.deleteIfExists(root)
        }
    }

    @Suppress("DEPRECATION")
    @Test fun cancelPreservesCompletedLegacyArtifact() {
        val backend = AndroidModelDownloadBackend(context)
        val directory = "spec002-test-${UUID.randomUUID()}"
        val file = backend.getModelFile(directory, "legacy.litertlm")
        check(file.parentFile!!.mkdirs())
        file.writeText("verified-legacy-fixture")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var id = -1L
        try {
            id = manager.addCompletedDownload("SPEC-002 legacy fixture", "test", false,
                "application/octet-stream", file.absolutePath, file.length(), true)
            backend.cancelBy(id)
            assertTrue("A completed legacy row must not delete its runtime destination", file.isFile)
        } finally {
            if (id > 0L) manager.remove(id)
            FileTreeDeleter.deleteIfExists(backend.getModelDirectory(directory))
        }
    }

    @Test fun cleanupFinishedTestFixtures() {
        check(!fixture.exists()) { "A download fixture is still active" }
        val models = File(context.getExternalFilesDir(null), "models").canonicalFile
        val pattern = Regex("(?:\\.downloads-)?spec002-test-[a-f0-9-]{36}")
        models.listFiles()?.filter { pattern.matches(it.name) }?.forEach {
            check(it.parentFile?.canonicalFile == models)
            assertTrue(FileTreeDeleter.deleteIfExists(it))
        }
    }

    @Test fun submitControlledTransfer() {
        check(!fixture.exists()) { "Previous fixture must be verified/cleaned first" }
        val directory = "spec002-test-${UUID.randomUUID()}"
        val backend = AndroidModelDownloadBackend(context)
        val id = backend.enqueueNew(
            VoskRuSmallDownloadGateway.VOSK_RU_SMALL_URL,
            "vosk-test.partial",
            directory,
            allowOverMeteredNetwork = true,
            allowOverRoaming = false,
            expectedSizeBytes = VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SIZE_BYTES,
            displayName = "SPEC-002 Vosk transfer test",
            staging = true,
        )
        fixture.writeText("$id\n$directory")
        assertNotNull(backend.querySnapshotBy(id))
        assertFalse(backend.getModelFile(directory, "model.litertlm").exists())
        report("SUBMITTED id=$id directory=$directory")
    }

    @Test fun verifyAndCleanControlledTransfer() {
        val lines = fixture.readLines()
        val id = lines[0].toLong()
        val directory = lines[1]
        require(directory.startsWith("spec002-test-"))
        val backend = AndroidModelDownloadBackend(context)
        var status = backend.querySnapshotBy(id)
        val deadline = System.nanoTime() + 180_000_000_000L
        while (status?.state !in setOf(KsenaxDownloadState.SUCCESSFUL, KsenaxDownloadState.FAILED) &&
            System.nanoTime() < deadline) {
            Thread.sleep(500L)
            status = backend.querySnapshotBy(id)
        }
        try {
            assertEquals("DownloadManager reason=${status?.reasonCode}", KsenaxDownloadState.SUCCESSFUL, status?.state)
            val candidate = backend.downloadFile(id, VoskRuSmallDownloadGateway.VOSK_RU_SMALL_URL, directory, "legacy.zip")
            assertTrue(FileIntegrityVerifier.matches(candidate,
                VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SIZE_BYTES,
                VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SHA256))
            val gateway = object : SingleFileModelDownloadGateway(context, ModelArtifactSpec(
                url = VoskRuSmallDownloadGateway.VOSK_RU_SMALL_URL,
                localFileName = "verified-vosk.zip",
                storageDirectoryName = directory,
                expectedSizeBytes = VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SIZE_BYTES,
                expectedSha256 = VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SHA256,
            )) {}
            assertFalse(gateway.getModelDirectory().exists())
            assertTrue("First installation must create its runtime parent and promote atomically",
                gateway.installDownloadedFile(id, {}, { it() }))
            val installed = gateway.getModelFile()
            val extracted = backend.getModelSubdirectory(directory, "extraction")
            assertTrue(SafeZipExtractor.extract(installed, extracted))
            assertTrue(VoskRuSmallDownloadGateway.isValidVoskModelDirectory(
                File(extracted, VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_ROOT_DIRECTORY_NAME)))
            report("VERIFIED bytes=${installed.length()} sha256=${VoskRuSmallDownloadGateway.VOSK_RU_SMALL_ARCHIVE_SHA256}")
        } finally {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
            FileTreeDeleter.deleteIfExists(backend.getModelDirectory(".downloads-$directory"))
            FileTreeDeleter.deleteIfExists(backend.getModelDirectory(directory))
            fixture.delete()
        }
    }

    private fun report(message: String) {
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "SPEC002 $message\n") })
    }
}
