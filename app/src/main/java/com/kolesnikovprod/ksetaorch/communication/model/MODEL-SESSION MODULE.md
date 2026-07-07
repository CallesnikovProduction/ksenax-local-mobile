# Учебная заметка по `communication.model`

`model` здесь означает не набор DTO, а model-runtime boundary: слой доступа к
локальной модели. Он загружает LiteRT-LM runtime,
создает conversation, отправляет prompt или audio payload и возвращает сырой
текст ответа.

В этом пакете нет parser-а, policy, tools, Android UI и исполнения действий.
Если модель вернула JSON tool-call, слой `model` не должен решать, хороший это
tool-call или опасный. Он только возвращает строку выше по архитектуре.

## Главная идея

Локальный агент удобнее собирать не вокруг "одного большого чата", а вокруг
нескольких типов модельных задач.

Сейчас таких задач три:

| Profile | Метод session | Зачем нужен |
| --- | --- | --- |
| `ROUTER` | `askStateless` | Одноразовый агентный запрос. Модель получает полный prompt-контракт, tools, policy и должна вернуть структурированный ответ. |
| `CHAT` | `askPersistent` | Обычная переписка с моделью. LiteRT-LM conversation хранит историю сообщений. |
| `VOICE_TRANSCRIPTION` | `transcribe` | Расшифровка WAV через multimodal-вход модели. История чата не участвует. |

Третий профиль доступен не каждой сессии. Перед использованием audio input
caller проверяет `session.capabilities.supportsAudioInput`; text-only session
сохраняет router/chat API, но не притворяется мультимодальной.

`KsenaxModelTaskProfile` тут важнее, чем может показаться. Это не косметический
enum для логов. Он задает допустимый путь выполнения. Если кто-то передаст
`CHAT` в `askStateless`, реализация упадет сразу. Если кто-то передаст audio
payload в chat, реализация тоже упадет. Такие проверки держат архитектуру в
форме, пока вокруг нее еще много экспериментального кода.

## Насколько точно называется контур

Имя `model` допустимо и уже является частью импортируемого API, но само по себе
оно шире фактической ответственности. Более точным новым именем было бы
`inference` или `modelruntime`. Переименовывать весь package сейчас невыгодно:
это сломает импорты orchestration, UI и composition root без изменения
архитектуры. Поэтому публичный package остаётся `communication.model`, а
runtime-детали явно изолируются в `internal/litert`.

## Карта файлов

| Файл | Роль |
| --- | --- |
| `KsenaxModelSession.kt` | Основной контракт runtime-сессии. |
| `LiteRtKsenaxModelSession.kt` | Стабильный публичный фасад LiteRT-сессии. |
| `KsenaxModelRuntimeConfig.kt` | Изменяемые engine-параметры, включая размер контекстного окна. |
| `KsenaxModelSessionCapabilities.kt` | Возможности конкретной модели/runtime. |
| `KsenaxLiteRtAudioBackend.kt` | Публичная настройка LiteRT audio backend. |
| `internal/litert/LiteRtModelSessionEngine.kt` | Внутренняя LiteRT-реализация без project-префикса. |
| `KsenaxModelTaskProfile.kt` | Тип модельной задачи: router, chat, transcription. |
| `KsenaxModelRequest.kt` | DTO входа в модельную session. |
| `KsenaxModelResponse.kt` | DTO сырого ответа модели и latency. |
| `KsenaxVoiceMessage.kt` | Audio payload для multimodal-входа. |
| `transcription/KsenaxVoiceTranscriptionPrompt.kt` | Prompt-контракт для транскрибации. |
| `internal/transcription/RussianVoiceTranscriptionPrompt.kt` | Внутренний русский prompt по умолчанию. |

## Где проходит граница слоя

`model` отвечает за:

- lifecycle LiteRT-LM engine;
- создание stateless и persistent conversation;
- отправку text/audio payload в модель;
- базовую валидацию request по profile;
- возврат сырого текста и времени модельного вызова.

`model` не отвечает за:

- сборку большого agent prompt из блоков;
- JSON-схему tools;
- разбор router response;
- policy и safety-решения;
- Android permissions;
- выполнение tool-ов;
- запись голоса с микрофона;
- UI-состояние записи, playback и waveform.

Эта граница спасает проект от смешивания уровней. Модельный слой можно заменить
или протестировать отдельно, а orchestration не должен знать детали `EngineConfig`
и `Content.AudioFile`.

## Engine и Conversation

В LiteRT-LM удобно разделять две сущности.

`Engine` - тяжелый runtime модели. Он знает путь к `.litertlm`, backend,
опциональный audio backend и cache directory. Его дорого поднимать, поэтому
внутренний `LiteRtModelSessionEngine` держит один `Engine` на lifetime session.

```kotlin
val newEngine = Engine(
    EngineConfig(
        modelPath = modelPath,
        backend = Backend.CPU(),
        audioBackend = audioBackend?.toLiteRtBackend(),
        maxNumTokens = configuredRuntime.maxContextTokens,
        cacheDir = cacheDirPath,
    )
)

newEngine.initialize()
```

Публичный фасад принимает nullable-параметр:

```kotlin
LiteRtKsenaxModelSession(
    modelPath = functionGemmaPath,
    cacheDirPath = functionGemmaCache,
    audioBackend = null,
)
```

`null` означает text-only session. В таком режиме
`capabilities.supportsAudioInput == false`, audio backend не создаётся, а
`transcribe()` отклоняется до обращения к LiteRT-LM. Это штатная конфигурация
для FunctionGemma. Gemma с аудиовходом использует
`KsenaxLiteRtAudioBackend.CPU`.

## Контекстное окно

`KsenaxModelRuntimeConfig.maxContextTokens` напрямую передаётся в
`EngineConfig.maxNumTokens`. Несмотря на похожее название, это не отдельный
input-limit: LiteRT-LM трактует значение как общий бюджет `input + output`
tokens и размер KV-cache.

```kotlin
modelSession.configureRuntime(
    KsenaxModelRuntimeConfig(
        maxContextTokens = 4_096,
    )
)
```

Core default равен `1024`. Явный `null` включает значение из metadata модели
или дефолт runtime. Положительное число задаёт явный бюджет. Изменить этот
параметр у уже инициализированного LiteRT engine нельзя, поэтому
`configureRuntime`:

1. дожидается завершения текущего inference;
2. закрывает persistent conversation и старый engine;
3. сохраняет новую конфигурацию;
4. создаёт новый engine лениво на следующем model-call.

Изменение окна намеренно сбрасывает chat history внутри LiteRT conversation.
Caller должен отменить активный streaming и при необходимости передать
сохранённую историю заново.

`Conversation` - канал общения с моделью поверх уже поднятого engine. Conversation
получает `systemInstruction` и принимает сообщения через `sendMessage`.

```kotlin
val conversation = engine.createConversation(
    ConversationConfig(
        systemInstruction = Contents.of(systemInstruction),
    )
)

val answer = conversation.sendMessage(prompt).toString()
```

Практический вывод такой: engine можно прогреть заранее, а conversation выбирать
под задачу. Router получает новую conversation на каждый запрос. Chat переиспользует
одну active conversation, пока пользователь не сбросит диалог или не сменится
system instruction.

## Stateless и Persistent

`askStateless` создает one-shot conversation. Такой вызов подходит для router-а:
модель каждый раз получает полный контракт и не тащит прошлый JSON в новый ответ.

```kotlin
val request = KsenaxModelRequest(
    prompt = planningPrompt,
    systemInstruction = "Return only a JSON object for the Ksenax agentic planner.",
    profile = KsenaxModelTaskProfile.ROUTER,
)

val response = modelSession.askStateless(request)
```

`askPersistent` работает с active conversation. Такой режим нужен обычному чату,
где пользователь может написать: "А теперь объясни это проще", и модель должна
помнить прошлый ход.

```kotlin
val request = KsenaxModelRequest(
    prompt = userMessage,
    systemInstruction = chatSystemInstruction,
    profile = KsenaxModelTaskProfile.CHAT,
)

val response = modelSession.askPersistent(request)
```

Смешивать эти режимы нельзя. Router через persistent conversation начнет получать
прошлые ответы в контексте и чаще ломать JSON. Chat через stateless conversation
потеряет историю.

## Request как контракт входа

`KsenaxModelRequest` выглядит простым DTO:

```kotlin
data class KsenaxModelRequest(
    val prompt: String,
    val systemInstruction: String,
    val profile: KsenaxModelTaskProfile,
    val voiceMessage: KsenaxVoiceMessage? = null,
)
```

Но смысл полей меняется по profile.

| Profile | `prompt` | `systemInstruction` | `voiceMessage` |
| --- | --- | --- | --- |
| `ROUTER` | Полный prompt-контракт для agent routing. | Роль router-а и правила ответа. | `null` |
| `CHAT` | Текст пользователя. | Роль обычного ассистента. | `null` |
| `VOICE_TRANSCRIPTION` | Короткая инструкция к аудио. | Роль transcription worker. | WAV-файл |

Это дешевый способ оставить один DTO, пока архитектура еще двигается. Когда API
стабилизируется, можно подумать о sealed request-типах:

```kotlin
sealed interface KsenaxModelRequestV2 {
    val systemInstruction: String
}

data class RouterRequest(
    val prompt: String,
    override val systemInstruction: String,
) : KsenaxModelRequestV2

data class ChatRequest(
    val message: String,
    override val systemInstruction: String,
) : KsenaxModelRequestV2

data class VoiceTranscriptionRequest(
    val voiceMessage: KsenaxVoiceMessage,
    val prompt: KsenaxVoiceTranscriptionPrompt,
    override val systemInstruction: String = prompt.systemInstruction(),
) : KsenaxModelRequestV2
```

Плюс такого варианта: Kotlin-компилятор начнет ловить часть ошибок. Минус:
архитектура станет тяжелее до того, как orchestration и tools окончательно
устаканятся.

## Response и latency

`KsenaxModelResponse` возвращает сырой текст:

```kotlin
data class KsenaxModelResponse(
    val text: String,
    val latencyMs: Long,
    val profile: KsenaxModelTaskProfile,
)
```

`text` еще не означает "готовое действие". Для `ROUTER` это строка, которую должен
разобрать parser. Для `VOICE_TRANSCRIPTION` это распознанный текст. Для `CHAT`
это ответ ассистента.

`latencyMs` сейчас замеряется монотонным `System.nanoTime()` внутри конкретного
вызова модели:

```kotlin
val startedAtNanos = System.nanoTime()
val text = conversation.sendMessage(request.prompt).toString()
val latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
```

В этот замер не входит холодная загрузка engine, policy, parser и выполнение
tool-а. Для оценки agent pipeline позже стоит разделить метрики:

- `engineColdStartMs`;
- `promptAssemblyMs`;
- `modelRequestMs`;
- `parseMs`;
- `policyMs`;
- `toolExecutionMs`;
- `totalTurnMs`.

Так станет видно, тормозит модель, prompt-сборка или Android-действие.

## Voice transcription

Голосовая запись живет в `communication.voice`. Модельный слой получает уже
готовый `KsenaxVoiceMessage`.

```kotlin
data class KsenaxVoiceMessage(
    val file: java.io.File,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val durationMillis: Long? = null,
)
```

LiteRT-LM принимает аудио через `Content.AudioFile`, а текстовую инструкцию через
`Content.Text`:

```kotlin
conversation.sendMessage(
    Contents.of(
        Content.AudioFile(voiceMessage.file.absolutePath),
        Content.Text(request.prompt),
    )
)
```

Метод `transcribe` собирает request сам:

```kotlin
val transcript = modelSession.transcribe(voiceMessage).text
```

Снаружи не нужно помнить profile, system instruction и user prompt для русского
языка. Если появится английский или испанский режим, достаточно добавить новую
реализацию `KsenaxVoiceTranscriptionPrompt`.

```kotlin
object KsenaxEnglishVoiceTranscriptionPrompt : KsenaxVoiceTranscriptionPrompt {
    override val targetLocale = "en-US"
    override val targetLanguageName = "English"

    override fun systemInstruction(): String =
        """
        You are the local Ksenax speech transcription worker.
        Transcribe the provided audio into English text.
        Return only the transcript text.
        """.trimIndent()

    override fun userPrompt(): String =
        """
        Transcribe this audio message into English.
        Return only the words spoken in the audio.
        """.trimIndent()
}
```

## Почему transcription живет в ModelSession

На первый взгляд transcription можно вынести в отдельный `VoiceTranscriber`.
Но тогда этот класс начнет дублировать доступ к LiteRT-LM conversation, engine и
audio payload. В текущей архитектуре recorder отвечает за WAV, player отвечает
за playback, а model session отвечает за inference.

Это нормальное разделение:

- `communication.voice` записывает файл и собирает metadata;
- `communication.model` отправляет файл в модель;
- `communication.orchestration` решает, что делать с распознанным текстом.

Если позже появится внешний STT движок, можно будет добавить отдельный контракт
выше `model`, например `KsenaxSpeechRecognizer`. Сейчас транскрибация через
Gemma остается модельной задачей.

## Mutex и корутины

Во внутреннем `LiteRtModelSessionEngine` есть три mutex-а:

```kotlin
private val convMutex = Mutex()
private val inferenceMutex = Mutex()
private val engineMutex = Mutex()
```

`engineMutex` защищает загрузку и закрытие engine. Без него два параллельных
запроса могут одновременно увидеть `engine == null` и начать две загрузки модели.

`convMutex` защищает active persistent conversation. Если два chat-запроса
одновременно вызовут `sendMessage`, порядок сообщений может сломаться. Для чата
порядок важен, потому что conversation хранит историю.

`inferenceMutex` не даёт одному engine одновременно выполнять chat generation и
Gemma speech-to-text. Инициализация запроса выполняется уже под этим mutex, чтобы
`close()` не мог выгрузить engine между проверкой lifecycle и созданием
conversation. `configureRuntime()` использует тот же порядок блокировок
`conv -> inference -> engine`, поэтому смена контекстного окна не закрывает
engine посреди генерации.

Тяжелая работа уходит в `Dispatchers.Default`:

```kotlin
withContext(Dispatchers.Default) {
    newEngine.initialize()
}
```

Это хороший выбор для CPU-bound работы. В Android коде caller может быть на main
thread, а загрузка модели и generation не должны держать UI.

## Валидация как архитектурная защита

Методы `validateStateless` и `validatePersistent` сейчас ловят ошибки раньше,
чем LiteRT-LM получит странный payload.

Stateless принимает:

- `ROUTER` с непустым prompt, system instruction и без voice message;
- `VOICE_TRANSCRIPTION` с непустым prompt, system instruction и валидным audio file.

Persistent принимает:

- только `CHAT`;
- непустые prompt и system instruction;
- без voice message.

Это важно для постепенного переноса из `conv`. Старый код может случайно
подсунуть не тот request. Лучше упасть на границе session с понятной ошибкой,
чем получить молчаливо испорченный контекст модели.

## Минимальный lifecycle

Типовой lifecycle выглядит так:

```kotlin
val session = LiteRtKsenaxModelSession(
    modelPath = modelPath,
    cacheDirPath = cacheDirPath,
)

session.initializeEngine()

val response = session.askStateless(routerRequest)

session.close()
```

Для экрана обычного чата session можно держать дольше:

```kotlin
session.initializeEngine()

val first = session.askPersistent(firstChatRequest)
val second = session.askPersistent(secondChatRequest)

session.resetPersistentConversation()
```

`resetPersistentConversation` сбрасывает историю, но не выгружает engine. Это
дешевле, чем `close` плюс повторный `initializeEngine`.

## Как этот слой связан с остальным `communication`

Упрощенный поток router-запроса:

```text
user text
  -> prompt assembler
  -> KsenaxModelRequest(profile = ROUTER)
  -> KsenaxModelSession.askStateless
  -> KsenaxModelResponse(text = raw json-ish model output)
  -> router parser
  -> policy
  -> tool executor
  -> user-visible result
```

Поток обычного чата:

```text
user text
  -> KsenaxModelRequest(profile = CHAT)
  -> KsenaxModelSession.askPersistent
  -> KsenaxModelResponse(text = assistant answer)
```

Поток voice input:

```text
microphone
  -> communication.voice records WAV
  -> KsenaxVoiceMessage
  -> KsenaxModelSession.transcribe
  -> plain text
  -> router or chat flow
```

## API-фишки, на которые стоит смотреть

`ConversationConfig(systemInstruction = Contents.of(...))` задает роль conversation
на момент создания. Если system instruction меняется, старая persistent
conversation больше не подходит. Поэтому `getOrCreateActiveConversation`
пересоздает conversation при смене instruction.

`Contents.of(...)` может собрать multimodal message из нескольких частей. Сейчас
мы используем `AudioFile + Text`. Это хороший намек на будущий multimodal-контур:
изображение, текст, аудио и другие payload-части должны идти через один понятный
объект запроса.

`Content.AudioFile(path)` передает путь к файлу, а не байты. Значит, session
обязана проверять, что файл существует, читается и не пустой. Иначе ошибка
вылезет глубже, уже внутри runtime.

`System.nanoTime()` уже защищает длительность от перевода системных часов. Для
серьезных составных метрик позже удобнее перейти на `TimeSource.Monotonic`,
чтобы собирать несколько стадий одного turn-а единообразно.

## Точки роста

1. Сделать typed request вместо одного DTO.

   `KsenaxModelRequest` удобен сейчас, но sealed-типы дадут compile-time защиту.
   Это стоит делать после стабилизации router/chat/voice сценариев.

2. Разделить latency.

   Текущий `latencyMs` полезен, но coarse. Для исследования agent performance
   нужны стадии pipeline.

3. Добавить fake session для тестов.

   Контракт `KsenaxModelSession` уже позволяет сделать fake:

   ```kotlin
   class FakeKsenaxModelSession(
       private val answer: String,
   ) : KsenaxModelSession {
       override suspend fun initializeEngine() = Unit

       override suspend fun askStateless(
           request: KsenaxModelRequest,
       ): KsenaxModelResponse =
           KsenaxModelResponse(answer, latencyMs = 0, profile = request.profile)

       override suspend fun askPersistent(
           request: KsenaxModelRequest,
       ): KsenaxModelResponse =
           KsenaxModelResponse(answer, latencyMs = 0, profile = request.profile)

        override fun streamPersistent(
            request: KsenaxModelRequest,
        ) = kotlinx.coroutines.flow.flowOf(
           KsenaxModelStreamEvent.TextDelta(answer),
           KsenaxModelStreamEvent.Completed(
               KsenaxModelResponse(answer, latencyMs = 0, profile = request.profile)
            ),
        )

        override fun streamEphemeral(
            userText: String,
        ) = kotlinx.coroutines.flow.flowOf(
            KsenaxModelStreamEvent.TextDelta(answer),
            KsenaxModelStreamEvent.Completed(
                KsenaxModelResponse(
                    answer,
                    latencyMs = 0,
                    profile = KsenaxModelTaskProfile.CHAT,
                )
            ),
        )

       override suspend fun transcribe(
           voiceMessage: KsenaxVoiceMessage,
           prompt: KsenaxVoiceTranscriptionPrompt,
       ): KsenaxModelResponse =
           KsenaxModelResponse(answer, latencyMs = 0, profile = KsenaxModelTaskProfile.VOICE_TRANSCRIPTION)

       override suspend fun resetPersistentConversation() = Unit

       override suspend fun close() = Unit
   }
   ```

   Такой fake даст тестировать orchestration без настоящей модели.

4. Проверить streaming на реальном устройстве.

   `streamPersistent` уже переводит `sendMessageAsync` в поток
   `TextDelta -> Completed`. На устройстве нужно измерить время до первого
   фрагмента, проверить отмену через `cancelProcess` и убедиться, что модель не
   теряет историю после потокового ответа.

5. Сменить исключения на typed failures.

   `require` удобен на раннем этапе. Для production-слоя можно вернуть
   `Result<KsenaxModelResponse>` или свой `KsenaxModelFailure`, чтобы UI отличал
   "нет файла модели", "битый audio payload", "runtime упал" и "модель вернула
   пустой ответ".

## Что проверять при переносе старого `conv`

При переносе кода в `communication.model` полезно задавать один вопрос:

> Этот код работает с runtime модели или пытается управлять агентом?

Если код только загружает модель, создает conversation, отправляет prompt или
audio payload, ему место рядом с `model`.

Если код собирает prompt из policy/tools, он должен жить в `prompt` или
`orchestration`.

Если код решает, можно ли выполнить действие, ему место в `tools/policy`.

Если код дергает Android API, ему место в tool executor или Android-facing слое.

Если код записывает WAV, считает уровни голоса или играет запись, ему место в
`voice`.

## Быстрый чек-лист для будущих правок

- Новый model-call получил правильный `KsenaxModelTaskProfile`.
- Router идет через `askStateless`.
- Chat идет через `askPersistent`.
- Voice идет через `transcribe`.
- `systemInstruction` не пустой.
- Audio file существует, читается и не пустой.
- Parser не попал в `model`.
- Policy не попала в `model`.
- Android permission не попал в `model`.
- Тесты могут подменить `KsenaxModelSession` fake-реализацией.

Если правка проходит этот список, слой `model` остается чистым и пригодным для
постепенной замены старого `conv`.
