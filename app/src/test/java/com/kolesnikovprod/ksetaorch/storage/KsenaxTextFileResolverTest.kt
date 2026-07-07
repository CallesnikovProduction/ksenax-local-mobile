package com.kolesnikovprod.ksetaorch.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxTextFileResolverTest {

    @Test
    fun `workspace marker matcher accepts canonical and legacy provider names`() {
        assertTrue(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone"
            )
        )
        assertTrue(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone.txt"
            )
        )
        assertTrue(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone (1).txt"
            )
        )
        assertTrue(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone (42)"
            )
        )
    }

    @Test
    fun `workspace marker matcher rejects unrelated user files`() {
        assertFalse(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone notes.txt"
            )
        )
        assertFalse(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone (copy).txt"
            )
        )
        assertFalse(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "my-there.ksenaxzone.txt"
            )
        )
        assertFalse(
            KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName(
                "there.ksenaxzone.ksenax-backup"
            )
        )
    }
}
