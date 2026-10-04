package com.kolesnikovprod.ksetaorch.ui

import androidx.lifecycle.SavedStateHandle
import com.kolesnikovprod.ksetaorch.ui.helpers.clearAgenticChatLaunchState
import com.kolesnikovprod.ksetaorch.ui.helpers.clearBasicChatLaunchState
import com.kolesnikovprod.ksetaorch.ui.helpers.resolveSettingsPage
import com.kolesnikovprod.ksetaorch.ui.main.settings.KsenaxSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KsenaxNavigationContractTest {

    @Test
    fun `chat patterns expose one canonical route contract`() {
        assertEquals(
            "chat/basic/{chatId}",
            KsenaxRoutes.Chat.Pattern.BASIC_CONVERSATION,
        )
        assertEquals(
            "chat/agentic/{chatId}",
            KsenaxRoutes.Chat.Pattern.AGENTIC_CONVERSATION,
        )
        assertEquals(
            "chat/temporaric",
            KsenaxRoutes.Chat.Pattern.TEMPORARIC_CONVERSATION,
        )
    }

    @Test
    fun `chat route builders use new chat sentinel when id is absent`() {
        assertEquals("chat/basic/-1", KsenaxRoutes.Chat.RouteBuilder.basic())
        assertEquals("chat/agentic/-1", KsenaxRoutes.Chat.RouteBuilder.agentic())
    }

    @Test
    fun `chat route builders preserve existing chat id`() {
        assertEquals("chat/basic/42", KsenaxRoutes.Chat.RouteBuilder.basic(42L))
        assertEquals("chat/agentic/73", KsenaxRoutes.Chat.RouteBuilder.agentic(73L))
    }

    @Test
    fun `unknown settings page falls back to main`() {
        assertEquals(KsenaxSettingsPage.Main, resolveSettingsPage("missing"))
        assertEquals(KsenaxSettingsPage.Main, resolveSettingsPage(null))
        assertEquals(
            KsenaxSettingsPage.ResponseModel,
            resolveSettingsPage(KsenaxSettingsPage.ResponseModel.name),
        )
    }

    @Test
    fun `basic cleanup removes only basic launch payload`() {
        val state = populatedLaunchState()

        state.clearBasicChatLaunchState()

        assertNull(state.get<String>(KsenaxRoutes.Chat.StateKey.BASIC_INITIAL_MESSAGE))
        assertEquals(
            "agentic prompt",
            state.get<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_INITIAL_MESSAGE),
        )
    }

    @Test
    fun `agentic cleanup removes complete agentic launch payload`() {
        val state = populatedLaunchState()

        state.clearAgenticChatLaunchState()

        assertNull(state.get<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_INITIAL_MESSAGE))
        assertNull(state.get<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_WORKSPACE_URI))
        assertNull(state.get<String>(KsenaxRoutes.Chat.StateKey.AGENTIC_WORKSPACE_PATH))
        assertEquals(
            "basic prompt",
            state.get<String>(KsenaxRoutes.Chat.StateKey.BASIC_INITIAL_MESSAGE),
        )
    }

    private fun populatedLaunchState(): SavedStateHandle = SavedStateHandle(
        mapOf(
            KsenaxRoutes.Chat.StateKey.BASIC_INITIAL_MESSAGE to "basic prompt",
            KsenaxRoutes.Chat.StateKey.AGENTIC_INITIAL_MESSAGE to "agentic prompt",
            KsenaxRoutes.Chat.StateKey.AGENTIC_WORKSPACE_URI to "content://workspace",
            KsenaxRoutes.Chat.StateKey.AGENTIC_WORKSPACE_PATH to "/Documents/workspace",
        ),
    )
}
