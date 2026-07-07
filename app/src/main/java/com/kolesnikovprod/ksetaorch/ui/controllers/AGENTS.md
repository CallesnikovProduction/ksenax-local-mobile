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

Инициализирует SAF workspace marker-файлом `there.ksenaxzone` и собирает
новый agentic work runtime вокруг того же `KsenaxTextFileResolver`.
Внутри runtime G4 планирует шаги, FunctionGemma компилирует атомарные
one-shot actions, а Android executor-ы выполняют уже разобранные команды.

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
