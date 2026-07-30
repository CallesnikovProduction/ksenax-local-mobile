package com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KsenaxModelVerificationSessionRegistryTest {

    @Test
    fun verifiedModelIsRememberedUntilSessionInvalidation() {
        val registry = KsenaxModelVerificationSessionRegistry()
        registry.onAppForegrounded()

        registry.markVerified("gemma")

        assertTrue(registry.isVerified("gemma"))
        registry.onAppBackgrounded()
        assertFalse(registry.isVerified("gemma"))
    }

    @Test
    fun staleVerificationCannotRestoreCacheAfterInvalidation() {
        val registry = KsenaxModelVerificationSessionRegistry()
        registry.onAppForegrounded()
        val startedInSession = registry.currentSession()

        registry.onAppBackgrounded()
        val wasMarked = registry.markVerified(
            modelKey = "gemma",
            expectedSession = startedInSession,
        )

        assertFalse(wasMarked)
        assertFalse(registry.isVerified("gemma"))
    }

    @Test
    fun validationCompletedInBackgroundIsNotRemembered() {
        val registry = KsenaxModelVerificationSessionRegistry()

        assertFalse(registry.markVerified("gemma"))
        assertFalse(registry.isVerified("gemma"))
    }

    @Test
    fun reinstallInvalidatesOnlySelectedModel() {
        val registry = KsenaxModelVerificationSessionRegistry()
        registry.onAppForegrounded()
        registry.markVerified("gemma")
        registry.markVerified("vosk")

        registry.invalidate("gemma")

        assertFalse(registry.isVerified("gemma"))
        assertTrue(registry.isVerified("vosk"))
    }
}
