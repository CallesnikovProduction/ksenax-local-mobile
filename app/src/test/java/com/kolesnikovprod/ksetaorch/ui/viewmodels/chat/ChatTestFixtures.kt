package com.kolesnikovprod.ksetaorch.ui.viewmodels.chat

import com.kolesnikovprod.ksetaorch.communication.model.*
import com.kolesnikovprod.ksetaorch.storage.chat.domain.KsenaxChatRepository
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.*
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*

/** Управляемые зависимости JVM-тестов; модель и Android IO не запускаются.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class ChatTestSession : KsenaxModelSession {
    val persistent = mutableListOf<KsenaxModelRequest>()
    val ephemeral = mutableListOf<String>()
    var resets = 0
    var closes = 0
    var prepare: suspend () -> Unit = {}
    var reply: (String) -> Flow<KsenaxModelStreamEvent> = { text -> flowOf(
        KsenaxModelStreamEvent.Completed(KsenaxModelResponse("reply:$text", 12, KsenaxModelTaskProfile.CHAT))) }
    override suspend fun initializeEngine() = prepare()
    override fun streamPersistent(request: KsenaxModelRequest) = flow {
        persistent += request
        emitAll(reply(request.prompt))
    }
    override fun streamEphemeral(userText: String) = flow {
        ephemeral += userText
        emitAll(reply(userText))
    }
    override suspend fun resetPersistentConversation() { resets++ }
    override suspend fun close() { closes++ }
    override suspend fun askStateless(request: KsenaxModelRequest): KsenaxModelResponse = error("Unexpected stateless entry")
    override suspend fun askPersistent(request: KsenaxModelRequest): KsenaxModelResponse = error("Unexpected non-streaming entry")
    override suspend fun transcribe(voiceMessage: KsenaxVoiceMessage,
        prompt: com.kolesnikovprod.ksetaorch.communication.model.transcription.KsenaxVoiceTranscriptionPrompt): KsenaxModelResponse =
        error("Voice is outside chat lifecycle tests")
}

/** Repository с явной задержкой записи для проверки повторной отправки.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class ChatTestRepository : KsenaxChatRepository {
    override val chats = MutableStateFlow<List<KsenaxStoredChat>>(emptyList())
    private var stored = emptyList<KsenaxStoredChat>()
    var pauseNotifications = false
    var writeBarrier: CompletableDeferred<Unit>? = null
    var userWritesStarted = 0
    var failNextWrite = false
    override fun observeChat(chatId: Long) = flow { emit(stored.find { it.id == chatId }) }
    private fun publish() { if (!pauseNotifications) chats.value = stored }
    private suspend fun beforeWrite() {
        userWritesStarted++
        writeBarrier?.await()
        if (failNextWrite) { failNextWrite = false; error("write failed") }
    }
    override suspend fun saveChat(chat: KsenaxStoredChat): Long {
        beforeWrite()
        val id = chat.id.takeIf { it != 0L } ?: ((stored.maxOfOrNull { it.id } ?: 0) + 1)
        stored = stored.filterNot { it.id == id } + chat.copy(id = id)
        publish()
        return id
    }
    override suspend fun appendMessage(chatId: Long, message: KsenaxStoredMessage): Long {
        if (message.role == KsenaxMessageRole.User) beforeWrite()
        val chat = stored.single { it.id == chatId }
        stored = stored.map { if (it.id == chatId) chat.copy(messages = chat.messages + message) else it }
        publish()
        return chat.messages.size.toLong() + 1
    }
    override suspend fun renameChat(chatId: Long, title: String, updatedAtEpochMillis: Long) {
        stored = stored.map { if (it.id == chatId) it.copy(title = title, updatedAtEpochMillis = updatedAtEpochMillis) else it }
        publish()
    }
    override suspend fun deleteChat(chatId: Long) { stored = stored.filterNot { it.id == chatId }; publish() }
}

/** Проверка допуска без чтения модели с диска.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal class ChatTestVerifier : KsenaxModelIntegrityVerifier {
    var barrier: CompletableDeferred<Unit>? = null
    var verified = true
    override fun isVerifiedInCurrentSession() = verified
    override suspend fun verifyOnce(onStageChanged: (KsenaxGemmaVerificationStage) -> Unit): KsenaxGemmaVerificationResult {
        barrier?.await()
        return KsenaxGemmaVerificationResult.Valid
    }
}
