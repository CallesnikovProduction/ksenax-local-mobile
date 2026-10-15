package com.kolesnikovprod.ksetaorch.download

import com.kolesnikovprod.ksetaorch.download.domain.ArtifactFileInstaller
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtifactFileInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun atomicReplacementConsumesCandidateAndReplacesOldBytes() {
        val candidate = file("candidate", "new")
        val installed = file("installed", "old")

        assertTrue(ArtifactFileInstaller.promote(candidate, installed))

        assertEquals("new", installed.readText())
        assertFalse(candidate.exists())
    }

    @Test
    fun missingCandidatePreservesInstalledBytes() {
        val installed = file("installed", "old")

        assertFalse(ArtifactFileInstaller.promote(File(temporary.root, "missing"), installed))

        assertEquals("old", installed.readText())
    }

    @Test
    fun failedMovePreservesBothCandidateAndExistingTree() {
        val candidate = file("candidate", "new")
        val installed = temporary.newFolder("installed")
        val old = File(installed, "model").apply { writeText("old") }

        assertFalse(ArtifactFileInstaller.promote(candidate, installed))

        assertEquals("old", old.readText())
        assertEquals("new", candidate.readText())
    }

    @Test
    fun samePathAndNormalizedAliasAreRejected() {
        val installed = file("installed", "old")
        assertFalse(ArtifactFileInstaller.promote(installed, installed))
        assertFalse(ArtifactFileInstaller.promote(File(temporary.root, "./installed"), installed))
        assertEquals("old", installed.readText())
    }

    @Test
    fun directoryCanBePromotedToUnusedPath() {
        val candidate = temporary.newFolder("candidate")
        File(candidate, "model").writeText("new")
        val installed = File(temporary.root, "installed")

        assertTrue(ArtifactFileInstaller.promote(candidate, installed))

        assertEquals("new", File(installed, "model").readText())
        assertFalse(candidate.exists())
    }

    @Test
    fun overlappingTreesAreRejected() {
        val candidate = temporary.newFolder("candidate")
        File(candidate, "model").writeText("new")

        assertFalse(ArtifactFileInstaller.promote(candidate, File(candidate, "nested")))
        assertEquals("new", File(candidate, "model").readText())
    }

    @Test
    fun missingDestinationParentDoesNotConsumeCandidate() {
        val candidate = file("candidate", "new")
        assertFalse(ArtifactFileInstaller.promote(candidate, File(temporary.root, "missing/model")))
        assertEquals("new", candidate.readText())
    }

    @Test
    fun symbolicCandidateAndDestinationAreRejected() {
        val candidate = file("candidate", "new")
        val installed = file("installed", "old")
        val link = File(temporary.root, "link")
        createSymbolicLink(link, candidate)

        assertFalse(ArtifactFileInstaller.promote(link, installed))
        assertFalse(ArtifactFileInstaller.promote(installed, link))
        assertEquals("old", installed.readText())
        assertEquals("new", candidate.readText())
    }

    @Test
    fun linkedParentAndLinksInsideCandidateTreeAreRejected() {
        val real = temporary.newFolder("real")
        val candidate = File(real, "model").apply { writeText("new") }
        val link = File(temporary.root, "link")
        createSymbolicLink(link, real)
        val installed = file("installed", "old")

        assertFalse(ArtifactFileInstaller.promote(File(link, "model"), installed))
        val tree = temporary.newFolder("tree")
        createSymbolicLink(File(tree, "model"), candidate)
        assertFalse(ArtifactFileInstaller.promote(tree, File(temporary.root, "promoted")))
        assertEquals("old", installed.readText())
    }

    @Test
    fun hardLinkedAliasIsRejected() {
        val candidate = file("candidate", "new")
        val installed = File(temporary.root, "installed")
        try {
            Files.createLink(installed.toPath(), candidate.toPath())
        } catch (exception: Exception) {
            assumeNoException("Hard links are unavailable on this filesystem", exception)
        }

        assertFalse(ArtifactFileInstaller.promote(candidate, installed))
        assertEquals("new", installed.readText())
        assertTrue(candidate.exists())
    }

    private fun file(name: String, text: String): File =
        temporary.newFile(name).apply { writeText(text) }

    private fun createSymbolicLink(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (exception: Exception) {
            assumeNoException("Symbolic links require filesystem/OS support", exception)
        }
    }
}
