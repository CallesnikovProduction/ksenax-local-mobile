# AGENTS.md: `ui/viewmodels`

## Назначение директории

`ui/viewmodels` содержит главный presentation-контур текущего экрана:

- `KsenaxMainUiState.kt` описывает единый immutable-снимок UI;
- `KsenaxMainViewModel.kt` принимает события экрана, координирует сценарии и
  публикует новое состояние.
- `chat/basic/KsenaxBasicChatViewModel.kt` владеет Room-backed Basic-чатом,
  проверкой Gemma и streaming-ответом.
- `chat/agentic/KsenaxAgenticChatViewModel.kt` владеет Room-backed Agentic-чатом,
  проверкой Gemma, pipeline-шагами, отменой и запуском tools в директории чата.
- `chat/temporaric/KsenaxTemporaricChatViewModel.kt` владеет только оперативной
  перепиской сырого режима и принципиально не получает Room repository или
  SavedStateHandle.

Эти файлы сейчас выполняют свою роль. Не нужно переписывать ViewModel,
разносить её по новым слоям или менять форму state без конкретной задачи.
Сначала нужно понять затрагиваемый поток и уже существующие
`ui/controllers`/`ui/helpers`.

## Ментальная модель

Основной поток:

```text
Screen
    -> вызывает публичный on...-метод ViewModel
        -> ViewModel выбирает сценарий
            -> controller / helper / coordinator / use case
        -> ViewModel присваивает новый KsenaxMainUiState
    -> Screen перерисовывается из uiState
```

Читать контур лучше в таком порядке:

```text
KsenaxMainUiState
    -> KsenaxMainViewModel
        -> вызывающий Screen
```

При разборе отдельного сценария после ViewModel перейти в соответствующий
controller, helper или use case.

## `KsenaxMainUiState`

`KsenaxMainUiState` хранит состояние главного экрана:

- текст ввода, выбранный режим и рабочую папку;
- список чатов и активный чат;
- install snapshots Gemma 4 E2B, FunctionGemma и Vosk;
- состояние install overlay и разрешения сетевой загрузки;
- флаги установленных моделей;
- выбранную модель транскрипции;
- выбранную основную текстовую модель отдельно от модели транскрипции;
- voice snapshot и сообщение об ошибке записи.

State меняется через `copy`. Не мутировать вложенные коллекции и не добавлять
в него Android-контекст, controller, coroutine job или другую исполняемую
зависимость.

Вычисляемые свойства `selectedMode`, `activeChat`,
`isAgenticModeSelected` и `activeInstallProgress` дают экрану готовое
presentation-значение. Если вычисление зависит только от полей state и нужно в
нескольких местах UI, его можно разместить рядом. Побочные эффекты здесь
запрещены.

`KsenaxModelDownloadOverlayState` описывает визуальный этап overlay:

```text
Hidden
ModelOffer
Downloading
Unpacking
```

`KsenaxInstallOverlayTarget` связывает UI-представление с Gemma 4 E2B,
FunctionGemma или Vosk. Это presentation-target, а не download backend model.

## `KsenaxMainViewModel`

`KsenaxMainViewModel` служит главным поведенческим узлом UI. Она:

- хранит `uiState` с `private set`;
- принимает публичные события экрана через методы `on...`;
- запускает coroutine-сценарии в `viewModelScope`;
- связывает chat, voice и install-потоки;
- при старте и сохранении настроек передаёт выбранный `contextWindow.tokenCount`
  в `KsenaxModelRuntimeSettingsController`;
- продолжает отложенное действие после установки модели;
- закрывает voice controller в `onCleared`.

ViewModel наследует `AndroidViewModel`, потому что текущие install/voice
зависимости создаются с application context. В проекте пока нет DI для этого
контура: use case, controller и coordinator собираются внутри ViewModel.
Не вводить DI или новый application graph попутно с локальной правкой.

## Сценарии

### Chat

`KsenaxMainViewModel` оставляет у себя выбор режима, voice/install-состояние и
общий список Room-чатов. Basic- и Agentic-сообщения экран направляет через
navigation в соответствующую chat ViewModel.

Для общей боковой панели `KsenaxMainViewModel` также собирает
`KsenaxChatRepository.chats`: все сохранённые чаты преобразуются в единый
presentation state для боковой панели. Историю каждого режима записывает его
chat ViewModel через repository-контракт.

Basic-поток:

```text
KsenaxBasicChatScreen
    -> KsenaxBasicChatViewModel
    -> KsenaxGemmaIntegrityController
    -> KsenaxBasicChatCoordinator
    -> KsenaxChatRepository
    -> Room
```

Agentic-поток:

```text
KsenaxAgenticChatScreen
    -> KsenaxAgenticChatViewModel
    -> KsenaxCompositeModelIntegrityVerifier
    -> KsenaxAgenticWorkController
    -> KsenaxAgenticWorkRuntime
    -> G4 planning -> FunctionGemma one-shot action -> Android executor
    -> KsenaxChatRepository
    -> Room
```

Temporaric-поток:

```text
KsenaxTemporaricChatScreen
    -> KsenaxTemporaricChatViewModel
    -> KsenaxGemmaIntegrityController
    -> KsenaxTemporaricChatCoordinator
    -> KsenaxModelSession.streamEphemeral
```

Temporaric показывает сообщения только из памяти процесса. Он не добавляется в
общий список чатов, не обращается к `KsenaxChatRepository`, не использует
SavedStateHandle и не передаёт предыдущие turn-ы модели.

Streaming delta хранится только в UI state. Итоговый или остановленный ответ
записывается в Room одним сообщением.

Basic screen переиспользует voice-flow `KsenaxMainViewModel`: результат
транскрипции приходит событием и дописывается в draft
`KsenaxBasicChatViewModel`. `selectedSupportedModel` не определяет STT;
микрофон всегда использует независимый `selectedTranscribingModel`.

### Voice

`onMicClick` проверяет текущее voice-состояние, выбирает модель транскрипции и
при необходимости запускает install-сценарий. Запись выполняет
`KsenaxVoiceController`, а преобразование записи в текст делегировано
`KsenaxVoiceInputController`.

Метод с `@RequiresPermission(RECORD_AUDIO)` должен вызываться только после
проверки разрешения на UI-стороне. Не переносить permission launcher или
Composable API во ViewModel.

### Install

ViewModel работает через:

```text
KsenaxInstallCoordinatorSelector
KsenaxInstallUiStateReducer
KsenaxModelInstallCoordinator
KsenaxInstallSnapshot
KsenaxPendingInstallAction
```

Selector выбирает coordinator для UI-target. Reducer читает и обновляет
соответствующий snapshot и overlay-state. Не возвращать эти `when`-маппинги
обратно во ViewModel.

ViewModel хранит один `installObservationJob`, поэтому в текущем UI активно
наблюдается одна установка. Новая установка отменяет предыдущее наблюдение.
Менять эту модель можно только вместе со сценарием параллельных установок.

`pendingInstallAction` сохраняет действие, которое продолжится после успешной
установки. При dismiss оно сбрасывается. После установки ViewModel очищает его
до выполнения продолжения, чтобы действие не запустилось повторно.

В текущем коде `NO_DOWNLOAD_ID` используется при восстановлении сохранённой
загрузки. Не расширять это исключение на остальные install-сценарии. Новая
логика должна работать через coordinator и snapshot, не через download gateway,
backend, `DownloadManager`, `Cursor` или файловые операции.

## Правила изменений

- Сначала определить поток: `event -> ViewModel -> helper/controller -> state`.
- Сохранять `uiState` единственным публичным источником состояния экрана.
- Менять state через `copy` и оставлять setter закрытым.
- Выполнять долгие операции в `viewModelScope`.
- Не хранить `Activity`, `View`, Composable, launcher или NavController.
- Не переносить в ViewModel Canvas/UI-отрисовку.
- Не дублировать логику из `ui/controllers` и `ui/helpers`.
- Не протаскивать download gateway/backend и Android download mechanics.
- Не использовать runtime-путь до подтверждённой установки модели.
- Не добавлять новый флаг, пока существующий snapshot или вычисляемое свойство
  уже отвечает на тот же вопрос.
- При новом публичном событии использовать имя `on...`, совпадающее с действием
  пользователя.
- После изменения проверить исходный UI-event, все переходы state и повторный
  вход в сценарий после configuration change или восстановления загрузки.

## Что трогать без необходимости не нужно

Не проводить попутный рефакторинг секций ViewModel, ручной сборки зависимостей,
install state или controller/helper-границ. Здесь несколько связанных
асинхронных потоков. Даже небольшое перемещение может изменить порядок
обновления UI, отмену job или продолжение pending action.

Короткое правило:

```text
ViewModel принимает событие и координирует сценарий.
Controllers/helpers выполняют узкую работу.
UiState хранит результат, который читает экран.
```
