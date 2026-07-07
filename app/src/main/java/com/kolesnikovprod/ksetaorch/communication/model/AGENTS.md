# AGENTS.md

## Область действия

Этот файл описывает `com.kolesnikovprod.ksetaorch.communication.model`.
Инструкции действуют для всех файлов внутри этой директории, включая
`internal/litert` и `internal/transcription`.

`communication.model` отвечает за model runtime:

- создаёт и закрывает LiteRT-LM `Engine`;
- создаёт stateless, persistent и ephemeral conversations;
- передаёт текст и audio payload в модель;
- возвращает сырой ответ и latency;
- объявляет возможности конкретной session;
- применяет параметры runtime, требующие пересоздания engine.

Этот контур не собирает agent prompt, не разбирает tool-call, не применяет
policy, не исполняет tools и не управляет Android UI.

## Обязательное правило актуальности

Каждое изменение контракта, lifecycle, структуры директорий, параметров
`EngineConfig`, типов request/response или внешнего способа вызова должно
обновлять этот `AGENTS.md` в том же изменении.

Перед завершением работы проверь:

1. Совпадает ли карта файлов с деревом.
2. Совпадают ли примеры с актуальными сигнатурами.
3. Описаны ли новые side effects, блокировки и сбросы состояния.
4. Понимает ли внешний caller, какой метод ему разрешён.
5. Остались ли известные ограничения в разделе технического долга.

## Публичная граница

Внешний код должен зависеть от `KsenaxModelSession`. Конкретный
`LiteRtKsenaxModelSession` нужен composition root для сборки runtime.

```text
KsenaxAndroidApplication
  -> LiteRtKsenaxModelSession
  -> KsenaxModelSession
  -> communication.orchestration / chat coordinators
  -> ViewModel
```

`LiteRtKsenaxModelSession` является стабильным публичным фасадом. Вся работа с
LiteRT-LM живёт в `internal/litert/LiteRtModelSessionEngine`.

Публичные типы сохраняют префикс `Ksenax`, потому что их импортируют другие
контуры. Внутренние реализации не используют project-префикс.

## Карта файлов

| Файл | Ответственность |
| --- | --- |
| `AGENTS.md` | Сжатая карта, правила границы и инструкция для следующих изменений. |
| `KsenaxModelSession.kt` | Главный runtime-контракт. |
| `LiteRtKsenaxModelSession.kt` | Публичный фасад LiteRT-LM session. |
| `KsenaxModelRuntimeConfig.kt` | Изменяемые параметры engine. |
| `KsenaxModelSessionCapabilities.kt` | Возможности модели и runtime. |
| `KsenaxLiteRtAudioBackend.kt` | Выбор LiteRT audio backend. |
| `KsenaxModelRequest.kt` | Общий DTO модельного запроса. |
| `KsenaxModelResponse.kt` | Сырой текст, latency и профиль задачи. |
| `KsenaxModelStreamEvent.kt` | `TextDelta` и итоговый `Completed`. |
| `KsenaxModelTaskProfile.kt` | `ROUTER`, `CHAT`, `VOICE_TRANSCRIPTION`. |
| `KsenaxVoiceMessage.kt` | Ссылка на audio-файл и его metadata. |
| `transcription/KsenaxVoiceTranscriptionPrompt.kt` | Публичный prompt-контракт транскрибации. |
| `internal/litert/LiteRtModelSessionEngine.kt` | Engine, conversations, mutex и валидация. |
| `internal/transcription/RussianVoiceTranscriptionPrompt.kt` | Русский transcription prompt по умолчанию. |
| `MODEL-SESSION MODULE.md` | Подробная учебная заметка. |

Связанный unit-тест:
`app/src/test/java/com/kolesnikovprod/ksetaorch/communication/model/KsenaxModelRuntimeConfigTest.kt`.

## Как выбрать метод session

### Agent router

Используй `askStateless` с профилем `ROUTER`.

Каждый вызов создаёт отдельную conversation. Прошлый JSON-подобный ответ не
попадает в следующий router turn.

```kotlin
val response = modelSession.askStateless(
    KsenaxModelRequest(
        prompt = routerPrompt,
        systemInstruction = routerSystemInstruction,
        profile = KsenaxModelTaskProfile.ROUTER,
    )
)
```

Parser, policy и tool execution работают после получения `response.text` в
`communication.orchestration`.

### Обычный чат

Используй `streamPersistent` с профилем `CHAT`. `askPersistent` собирает тот же
stream в один `KsenaxModelResponse`.

```kotlin
modelSession.streamPersistent(
    KsenaxModelRequest(
        prompt = userText,
        systemInstruction = chatSystemInstruction,
        profile = KsenaxModelTaskProfile.CHAT,
    )
)
```

Flow холодный. Inference начинается только после `collect`. Caller дописывает
каждый `TextDelta` и завершает turn по `Completed`.

### Temporaric

Используй `streamEphemeral(userText)`.

Каждый `collect` создаёт новую conversation без system instruction и без
истории предыдущего вызова.

### Транскрибация

Сначала проверь capability:

```kotlin
if (modelSession.capabilities.supportsAudioInput) {
    val transcript = modelSession.transcribe(voiceMessage).text
}
```

Gemma session создаётся с `KsenaxLiteRtAudioBackend.CPU`. FunctionGemma
создаётся с `audioBackend = null` и отклоняет `transcribe` до запуска runtime.

Контур `voice` записывает WAV. Контур `model` только валидирует готовый файл и
передаёт его в LiteRT-LM.

## Контекстное окно

Внешний caller передаёт runtime-параметры через контракт:

```kotlin
modelSession.configureRuntime(
    KsenaxModelRuntimeConfig(
        maxContextTokens = 4_096,
    )
)
```

`maxContextTokens` напрямую попадает в `EngineConfig.maxNumTokens`.
LiteRT-LM считает это общим бюджетом `input + output` и размером KV-cache.
Параметр не задаёт отдельный input-only limit.

Core default равен `1024`. Явное значение `null` оставляет размер из metadata
модели или дефолт runtime. Положительное число задаёт явный бюджет.

LiteRT-LM не меняет `EngineConfig` у работающего engine. Поэтому
`configureRuntime`:

1. дожидается завершения текущего inference;
2. закрывает persistent conversation и engine;
3. сохраняет новый config;
4. лениво создаёт engine на следующем запросе или `initializeEngine`.

Смена окна сбрасывает runtime chat history. ViewModel должен отменить активный
stream перед настройкой. Если чат хранит сообщения в repository, coordinator
может передать сохранённую историю при следующем turn.

## Как должны взаимодействовать внешние контуры

### Application composition root

Application создаёт долгоживущие session и явно задаёт аппаратные возможности:

```kotlin
LiteRtKsenaxModelSession(
    modelPath = gemmaPath,
    cacheDirPath = gemmaCache,
    audioBackend = KsenaxLiteRtAudioBackend.CPU,
)

LiteRtKsenaxModelSession(
    modelPath = functionGemmaPath,
    cacheDirPath = functionGemmaCache,
    audioBackend = null,
)
```

Composition root знает конкретную реализацию. Остальные контуры получают
`KsenaxModelSession`.

### Orchestration

Orchestration выбирает профиль, собирает prompt, вызывает session, парсит сырой
ответ и применяет policy. Model session не должна получать registry, policy или
Android tool executor.

### ViewModel

ViewModel предпочтительно работает через chat/agent coordinator и не создаёт
`KsenaxModelRequest` вручную. Допустимый узкий вызов model-контракта из настроек:

```kotlin
modelSession.configureRuntime(
    modelSession.runtimeConfig.copy(
        maxContextTokens = selectedContextWindow,
    )
)
```

ViewModel должна считать смену runtime config разрушительной для внутренней
conversation. UI-состояние и сохранённая история принадлежат внешнему контуру.

### Download

Download-контур предоставляет путь к установленной `.litertlm` модели и
runtime-cache. Model-контур не скачивает файлы и не проверяет сетевую политику.

Перед инициализацией session проверяет, что model file существует, читается и
не пуст, а cache directory доступна для записи.

### Voice

Voice-контур записывает и воспроизводит аудио. В model-контур он передаёт
`KsenaxVoiceMessage` с готовым файлом. Android permission, recorder state и
waveform сюда не входят.

## Lifecycle и конкурентность

`LiteRtModelSessionEngine` использует три mutex:

- `convMutex` защищает persistent conversation и порядок chat turns;
- `inferenceMutex` запрещает параллельный inference на одном engine;
- `engineMutex` защищает создание и закрытие native engine.

Операции, которым нужны все ресурсы, держат порядок блокировок:

```text
convMutex -> inferenceMutex -> engineMutex
```

Не меняй порядок. Обратный порядок создаст риск deadlock.

`initializeEngine` идемпотентен. Первый вызов загружает модель, последующие
видят готовый engine.

`resetPersistentConversation` закрывает только chat conversation. Engine
остаётся прогретым.

`close` закрывает conversation и engine. Следующий model-call может снова
создать engine.

Если `Engine.initialize()` падает, реализация закрывает частично созданный
native engine.

Если persistent generation отменяется или падает, реализация очищает
conversation. LiteRT-LM не гарантирует rollback незавершённого turn.

## Валидация request

Runtime обязан падать на границе с понятной ошибкой:

- `ROUTER` принимает непустые prompt и system instruction без audio payload;
- `CHAT` работает только через persistent API и не принимает audio payload;
- `VOICE_TRANSCRIPTION` требует включённый audio backend и валидный файл;
- `maxContextTokens` принимает `null` или положительное число.

Не переносить эти ошибки глубже в native runtime.

## Что нельзя добавлять в model

- prompt blocks и сборку agent prompt;
- JSON parser и tool schema parser;
- policy, risk level и confirmation flow;
- Android permissions, intents и activity results;
- download manager и install state;
- запись аудио, playback и waveform;
- UI state, navigation и Compose;
- chat repository и сохранение истории.

Если новый код отвечает на вопрос «что должна сделать модель или приложение»,
ему нужен `orchestration`. Здесь остаётся только вопрос «как вызвать model
runtime и безопасно владеть его ресурсами».

## Порядок изменения модуля

1. Найди внешние usages публичного типа.
2. Сохрани `Ksenax` у API, который импортируют другие контуры.
3. Перемести runtime-детали в `internal` и убери у них `Ksenax`.
4. Зафиксируй lifecycle и side effects в KDoc.
5. Добавь узкий unit-тест без запуска настоящей модели, если это возможно.
6. Обнови этот `AGENTS.md` и `MODEL-SESSION MODULE.md`.
7. Запусти проверки.

Минимальная проверка:

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

Для изменений streaming, cancellation, audio backend и размера KV-cache нужна
дополнительная проверка на реальном Android-устройстве.

## Технический долг

- `KsenaxModelRequest` допускает недопустимые сочетания profile и payload на
  уровне типов. Сейчас их отсекает runtime-валидация. Sealed request-типы
  потребуют согласованной миграции внешних callers.
- Ошибки передаются исключениями. Typed failures пока отсутствуют.
- `latencyMs` измеряет model-call. Cold start, prompt assembly, parser, policy и
  tool execution в него не входят.
- Поведение streaming, cancellation и больших KV-cache нужно измерять на
  реальном устройстве, а не только unit-тестами.
