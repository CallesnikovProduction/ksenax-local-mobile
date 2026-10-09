package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.*
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.basic.KsenaxBasicChatViewModel
import com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.temporaric.KsenaxTemporaricChatViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Проверяет lifecycle и порядок сообщений без Android runtime/инференса.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatLifecycleTest {
    private fun checkChat(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try { block(store) } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    private fun basic(store: ViewModelStore, repository: ChatTestRepository, session: ChatTestSession,
                      verifier: ChatTestVerifier = ChatTestVerifier()): KsenaxBasicChatViewModel =
        KsenaxBasicChatViewModel(null, SavedStateHandle(), repository, KsenaxBasicChatCoordinator(session), verifier, "Test", { 100L })
            .also { store.put("basic", it) }

    private fun temporary(store: ViewModelStore, session: ChatTestSession, verifier: ChatTestVerifier = ChatTestVerifier()) =
        KsenaxTemporaricChatViewModel(KsenaxTemporaricChatCoordinator(session), verifier, "Test", { 100L })
            .also { store.put("temporary", it) }

    @Test fun `basic reserves send while repository write is suspended`() = checkChat { store ->
        val repository = ChatTestRepository().apply { writeBarrier = CompletableDeferred() }
        val session = ChatTestSession()
        val vm = basic(store, repository, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        vm.onInputTextChanged("B"); vm.onSendClick(); runCurrent()
        assertEquals(1, repository.userWritesStarted)
        repository.writeBarrier!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("A"), session.persistent.map { it.prompt })
        assertEquals(listOf("A", "reply:A"), repository.chats.value.single().messages.map { it.text })
        assertFalse(vm.uiState.value.isGenerating)
    }

    @Test fun `new temporary session cancels pending verification`() = checkChat { store ->
        val session = ChatTestSession()
        val verifier = ChatTestVerifier().apply { barrier = CompletableDeferred() }
        val vm = temporary(store, session, verifier)
        vm.onMessageFromMain("A"); runCurrent()
        vm.onNewChatClick(); verifier.barrier!!.complete(Unit); advanceUntilIdle()
        assertTrue(session.ephemeral.isEmpty())
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test fun `leaving temporary screen cancels pending verification`() = checkChat { store ->
        val session = ChatTestSession()
        val verifier = ChatTestVerifier().apply { barrier = CompletableDeferred() }
        val vm = temporary(store, session, verifier)
        vm.onMessageFromMain("A"); runCurrent()
        vm.onLeaveForNavigation(); verifier.barrier!!.complete(Unit); advanceUntilIdle()
        assertTrue(session.ephemeral.isEmpty())
        assertEquals(listOf("A"), vm.uiState.value.messages.map { it.text })
    }

    @Test fun `basic persists partial when ViewModel store is cleared`() = checkChat { store ->
        val session = ChatTestSession().apply { reply = { flow { emit(KsenaxModelStreamEvent.TextDelta("partial")); awaitCancellation() } } }
        val repository = ChatTestRepository()
        val vm = basic(store, repository, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        assertEquals("partial", vm.uiState.value.streamingAssistantText)
        store.clear(); runCurrent()
        assertEquals(listOf("A", "partial"), repository.chats.value.single().messages.map { it.text })
        assertEquals(0, session.closes)
    }

    @Test fun `basic ignores blank input and commits each message once`() = checkChat { store ->
        val repository = ChatTestRepository(); val session = ChatTestSession(); val vm = basic(store, repository, session)
        vm.onInputTextChanged("  "); vm.onSendClick(); advanceUntilIdle()
        assertTrue(repository.chats.value.isEmpty()); assertTrue(session.persistent.isEmpty())
        vm.onInputTextChanged(" A "); vm.onSendClick(); advanceUntilIdle()
        assertEquals(listOf(KsenaxMessageRole.User, KsenaxMessageRole.Assistant), repository.chats.value.single().messages.map { it.role })
        assertEquals(listOf("A", "reply:A"), vm.uiState.value.activeChat!!.messages.map { it.text })
        assertFalse(vm.uiState.value.isGenerating)
    }

    @Test fun `basic recovers after write failure and after stream failure`() = checkChat { store ->
        val repository = ChatTestRepository().apply { failNextWrite = true }
        val session = ChatTestSession(); val vm = basic(store, repository, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        assertEquals("write failed", vm.uiState.value.errorMessage); assertFalse(vm.uiState.value.isGenerating)
        session.reply = { flow { emit(KsenaxModelStreamEvent.TextDelta("partial")); error("stream failed") } }
        vm.onInputTextChanged("B"); vm.onSendClick(); advanceUntilIdle()
        assertFalse(vm.uiState.value.isGenerating); assertNotNull(vm.uiState.value.errorMessage)
        session.reply = { text -> flowOf(KsenaxModelStreamEvent.Completed(KsenaxModelResponse("reply:$text", 12, KsenaxModelTaskProfile.CHAT))) }
        vm.onInputTextChanged("C"); vm.onSendClick(); advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf("B", "partial", "C", "reply:C"), repository.chats.value.single().messages.map { it.text })
        assertTrue(session.persistent.last().systemInstruction.contains("USER: B\nASSISTANT: partial"))
    }

    @Test fun `temporary turns stay isolated and reset clears RAM`() = checkChat { store ->
        val session = ChatTestSession(); val vm = temporary(store, session)
        vm.onInputTextChanged(" "); vm.onSendClick(); advanceUntilIdle(); assertTrue(session.ephemeral.isEmpty())
        vm.onInputTextChanged(" A "); vm.onSendClick(); advanceUntilIdle()
        vm.onInputTextChanged("B"); vm.onSendClick(); advanceUntilIdle()
        assertEquals(listOf("A", "B"), session.ephemeral)
        assertTrue(session.persistent.isEmpty()); assertEquals(0, session.resets)
        assertEquals(listOf("A", "reply:A", "B", "reply:B"), vm.uiState.value.messages.map { it.text })
        vm.onNewChatClick(); assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test fun `temporary stop retains partial and cancels transport without error`() = checkChat { store ->
        var cancelled = false
        val session = ChatTestSession().apply { reply = { flow {
            try { emit(KsenaxModelStreamEvent.TextDelta("partial")); awaitCancellation() } finally { cancelled = true }
        } } }
        val vm = temporary(store, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        vm.onStopGeneration(); runCurrent()
        assertTrue(cancelled); assertNull(vm.uiState.value.errorMessage); assertFalse(vm.uiState.value.isGenerating)
        assertEquals(listOf("A", "partial"), vm.uiState.value.messages.map { it.text })
        assertEquals(0, session.closes)
    }

    @Test fun `basic history comes from persisted chat even when UI notification is delayed`() = checkChat { store ->
        val repository = ChatTestRepository(); val session = ChatTestSession(); val vm = basic(store, repository, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        repository.pauseNotifications = true
        vm.onInputTextChanged("B"); vm.onSendClick(); advanceUntilIdle()
        vm.onInputTextChanged("C"); vm.onSendClick(); advanceUntilIdle()
        assertTrue(session.persistent.last().systemInstruction.contains("USER: B\nASSISTANT: reply:B"))
    }

    @Test fun `verification reserves admission before stage callback`() = checkChat { store ->
        val verifier = ChatTestVerifier().apply { barrier = CompletableDeferred() }
        val session = ChatTestSession(); val repository = ChatTestRepository(); val vm = basic(store, repository, session, verifier)
        vm.onInputTextChanged("A"); vm.onSendClick()
        vm.onInputTextChanged("B"); vm.onSendClick(); runCurrent()
        assertTrue(vm.uiState.value.isScreenBlocked)
        verifier.barrier!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("A"), session.persistent.map { it.prompt })
        assertEquals(1, repository.userWritesStarted)
    }

    @Test fun `temporary rejects concurrent send and recovers after stream failure`() = checkChat { store ->
        val session = ChatTestSession().apply { reply = { flow {
            emit(KsenaxModelStreamEvent.TextDelta("partial")); awaitCancellation()
        } } }
        val vm = temporary(store, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        vm.onInputTextChanged("B"); vm.onSendClick(); runCurrent()
        assertEquals(listOf("A"), session.ephemeral)
        vm.onStopGeneration(); runCurrent()
        session.reply = { flow { error("stream failed") } }
        vm.onInputTextChanged("B"); vm.onSendClick(); advanceUntilIdle()
        assertEquals("stream failed", vm.uiState.value.errorMessage); assertFalse(vm.uiState.value.isGenerating)
        session.reply = { text -> flowOf(KsenaxModelStreamEvent.Completed(KsenaxModelResponse("reply:$text", 12, KsenaxModelTaskProfile.CHAT))) }
        vm.onInputTextChanged("C"); vm.onSendClick(); advanceUntilIdle()
        assertEquals(listOf("A", "B", "C"), session.ephemeral)
        assertEquals(listOf("A", "partial", "B", "C", "reply:C"), vm.uiState.value.messages.map { it.text })
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun `basic stop persists once and saved id restores history in a new ViewModel`() = checkChat { store ->
        val session = ChatTestSession().apply { reply = { flow {
            emit(KsenaxModelStreamEvent.TextDelta("partial")); awaitCancellation()
        } } }
        val repository = ChatTestRepository(); val vm = basic(store, repository, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        vm.onStopGeneration(); runCurrent()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf("A", "partial"), repository.chats.value.single().messages.map { it.text })
        val id = vm.uiState.value.activeChatId!!
        store.clear(); runCurrent()
        val secondSession = ChatTestSession()
        val restored = KsenaxBasicChatViewModel(null, SavedStateHandle(mapOf("basic_chat_active_chat_id" to id)), repository,
            KsenaxBasicChatCoordinator(secondSession), ChatTestVerifier(), "Test", { 100L }).also { store.put("restored", it) }
        restored.onInputTextChanged("B"); restored.onSendClick(); advanceUntilIdle()
        assertEquals(id, restored.uiState.value.activeChatId)
        assertTrue(secondSession.persistent.single().systemInstruction.contains("USER: A\nASSISTANT: partial"))
        assertEquals(listOf("A", "partial", "B", "reply:B"), repository.chats.value.single().messages.map { it.text })
    }

    @Test fun `temporary exit cancels pending gate and clear cancels transport`() = checkChat { store ->
        val session = ChatTestSession(); val verifier = ChatTestVerifier().apply { barrier = CompletableDeferred() }
        val vm = temporary(store, session, verifier)
        vm.onMessageFromMain("A"); runCurrent(); vm.onExitRequested()
        verifier.barrier!!.complete(Unit); advanceUntilIdle()
        assertTrue(session.ephemeral.isEmpty())
        var cancelled = false
        session.reply = { flow { try { awaitCancellation() } finally { cancelled = true } } }
        vm.onInputTextChanged("B"); vm.onSendClick(); advanceUntilIdle()
        store.clear(); runCurrent()
        assertTrue(cancelled); assertTrue(session.persistent.isEmpty()); assertEquals(0, session.closes)
    }

    @Test fun `stop before user persistence finishes removes uncommitted transient`() = checkChat { store ->
        val repository = ChatTestRepository().apply { writeBarrier = CompletableDeferred() }
        val session = ChatTestSession(); val vm = basic(store, repository, session)
        vm.onInputTextChanged("A"); vm.onSendClick(); advanceUntilIdle()
        vm.onStopGeneration(); runCurrent()
        assertTrue(repository.chats.value.isEmpty()); assertTrue(session.persistent.isEmpty())
        assertNull(vm.uiState.value.transientUserText)
        assertNull(vm.uiState.value.errorMessage); assertFalse(vm.uiState.value.isGenerating)
        assertEquals("A", vm.uiState.value.inputText)
    }

    @Test fun `stop immediately after ready submission does not leave busy state stuck`() = checkChat { store ->
        val repository = ChatTestRepository(); val session = ChatTestSession(); val vm = basic(store, repository, session)
        vm.onInputTextChanged("seed"); vm.onSendClick(); advanceUntilIdle()
        repository.writeBarrier = CompletableDeferred()
        vm.onInputTextChanged("A"); vm.onSendClick(); vm.onStopGeneration(); runCurrent()
        assertFalse(vm.uiState.value.isGenerating)
        assertEquals(listOf("seed"), session.persistent.map { it.prompt })
    }

    @Test fun `temporary immediate stop also finalizes reserved busy state`() = checkChat { store ->
        val session = ChatTestSession(); val vm = temporary(store, session)
        vm.onInputTextChanged("seed"); vm.onSendClick(); advanceUntilIdle()
        var cancelled = false
        session.reply = { flow { try { awaitCancellation() } finally { cancelled = true } } }
        vm.onInputTextChanged("A"); vm.onSendClick(); vm.onStopGeneration(); runCurrent()
        assertFalse(vm.uiState.value.isGenerating); assertTrue(cancelled); assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf("seed", "A"), session.ephemeral)
    }
}
