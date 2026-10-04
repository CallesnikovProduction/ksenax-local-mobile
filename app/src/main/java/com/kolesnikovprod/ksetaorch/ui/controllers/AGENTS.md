# AGENTS.md — `ui/controllers`

## Назначение папки

Контроллеры в этой директории — помощники для `ViewModel`. Они выносят из неё
локальные, цельные сценарии работы с чатами и голосовым вводом, чтобы
`ViewModel` сохраняла роль верхнего координатора пользовательских событий и
публикации UI-state.

На текущем этапе контроллеры хорошо выполняют свою роль. Не нужно переносить,
объединять, дробить или оборачивать их дополнительными абстракциями без
конкретной проблемы и отдельной задачи. Перед изменением сначала читать
вызывающую `ViewModel`, используемые модели состояния и сам контроллер.

## Текущие ответственности

### `KsenaxAgenticWorkController`

По отдельной команде инициализирует SAF workspace marker-файлом
`there.ksenaxzone` и собирает agentic work runtime. Создание runtime не
открывает workspace: заметки получают `KsenaxTextFileResolver` лениво при
первом файловом действии. Поэтому проверка моделей и быстрые системные действия
не зависят от доступности каталога заметок.

Внутри runtime FunctionGemma предлагает конкретную функцию, планирование,
уточнение или отказ через native functions. Keywords не выбирают kit.
На planned-пути G4 строит
план, после чего каждый шаг отдельно компилирует FunctionGemma. Android
executor-ы получают только разобранные и проверенные команды.
Контроллер пишет в Android Log только стадии, имя функции, latency и категорию
результата — без текста UP, аргументов и содержимого заметок.

Контроллер не хранит выбранную директорию. `treeUri` и отображаемый путь
принадлежат Room-записи Agentic-чата и передаются при создании runtime.

### `KsenaxVoiceInputController`

Помогает `ViewModel` завершить сценарий записанного голосового ввода:

- выбирает обработку по типу `KsenaxRecordedVoiceInput`;
- для Vosk получает установленный runtime-путь через
  `VoskRuSmallInstallUseCase` и запускает транскрипцию;
- для Gemma передаёт WAV в общую `KsenaxModelSession.transcribe`;
- возвращает директорию сохранения voice-файлов для выбранной
  `KsenaxTranscribingModel` через соответствующий install use case.

Контроллер использует публичные runtime-path методы install use case. Он не
должен сам вычислять пути, проверять установку, скачивать модели или работать с
внутренностями download backend.

### `KsenaxModelRuntimeSettingsController`

Получает числовой размер контекстного окна от `KsenaxMainViewModel` и через
публичный `KsenaxModelSession.configureRuntime` применяет его ко всем
response-сессиям. Контроллер сериализует повторные сохранения настроек, но не
хранит UI-state и не импортирует LiteRT-LM.

UI-enum `KsenaxContextWindow` преобразуется в `tokenCount` во ViewModel.
`communication/model` получает только `KsenaxModelRuntimeConfig`.

### `KsenaxDownloadStallTracker`

Получает уже опубликованные install snapshots и отмечает отсутствие движения
`downloadedBytes` в течение двух минут. Tracker не опрашивает DownloadManager,
не запускает отдельный speed test и использует монотонное время. Новый
download id, движение байтов или завершение задачи сбрасывают окно простоя.

### `modelvalidation`

Подпакет `ui/controllers/modelvalidation` содержит файловый model gate и
foreground-session кэш:

- `KsenaxGemmaIntegrityController` последовательно проверяет наличие и
  целостность конкретного install-target-а;
- `KsenaxCompositeModelIntegrityVerifier` объединяет несколько обязательных
  моделей Agentic-контура;
- `KsenaxModelVerificationSessionRegistry` запоминает успешную проверку по
  стабильному `KsenaxInstallTarget.id` только пока приложение находится в
  foreground.
- `KsenaxPostInstallValidationController` после install-handoff повторно
  подтверждает presence/integrity через публичный install use case, проверяет
  достижимость реального runtime через переданный probe и только затем пишет
  результат в foreground-session registry.

`KsenaxAndroidApplication` владеет единым registry. `ProcessLifecycleOwner`
очищает его при `ON_STOP`; рекомпозиция и смена destination внутри приложения
кэш не сбрасывают. Успешная глубокая проверка install-контура может отметить
тот же target готовым без повторного SHA-256 чтения.

## Граница с `ViewModel`

Правильный поток:

```text
UI event
    -> ViewModel
        -> подходящий ui/controller
            -> локальный результат или обновлённый UI-state
        -> ViewModel публикует состояние и продолжает сценарий
```

`ViewModel` отвечает за:

- приём пользовательских событий;
- выбор момента вызова контроллера;
- coroutine/lifecycle-сценарий верхнего уровня;
- публикацию итогового UI-state;
- связь с inference, install и другими контурами.

Контроллер отвечает только за порученный ему локальный сценарий. Он не является
второй `ViewModel`, глобальным orchestrator-ом или хранилищем observable
UI-state.

## Правила изменений

- Считать эти классы рабочими помощниками `ViewModel`.
- Сохранять узкую ответственность каждого контроллера.
- Не дублировать их логику обратно во `ViewModel`.
- Не передавать в контроллеры Composable, NavController или UI-launcher-ы.
- Не добавлять Android download mechanics, filesystem validation, checksum или
  распаковку моделей.
- Для преобразований `KsenaxMainUiState` сохранять immutable-подход через
  `copy`, не мутировать коллекции на месте.
- Не смешивать chat-state операции и voice-input сценарии в одном контроллере.
- Не рефакторить работающий код «на будущее» без подтверждённого сценария.
- При изменении контракта проверять все места вызова во `ViewModel`.

Короткое правило:

```text
ViewModel координирует сценарий.
ui/controllers выполняют узкую вспомогательную работу.
```
