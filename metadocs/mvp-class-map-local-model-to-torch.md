# MVP class map: локальная модель -> JSON -> фонарик

Эта заметка нужна, чтобы утром быстро восстановить в голове, как сейчас устроено приложение. Это не финальная архитектура и не "идеальный clean architecture". Это карта текущего MVP: пользователь вводит команду, локальная модель выбирает tool call, приложение парсит JSON и включает или выключает фонарик.

## Главная идея текущего MVP

Сейчас приложение делает один вертикальный сценарий:

```text
Пользователь
 -> вводит "включи фонарик"
 -> нажимает "Отправить"
 -> MainActivity отправляет команду в KsenaxLocalConversation
 -> LiteRT-LM модель возвращает JSON
 -> KsenaxTorchRouteParser разбирает JSON
 -> KsenaxTorchToolExecutor выбирает Android-tool
 -> KsenaxFlashlightTool включает фонарик через CameraManager
```

Модель не получает прямой доступ к Android API. Она только возвращает JSON с именем tool. Реальное действие выполняет Kotlin-код после allowlist-проверки.

## Как читать проект утром

Самый быстрый порядок чтения:

1. `MainActivity.kt`
2. `KsenaxLocalConversation.kt`
3. `KsenaxTorchActionRouter.kt`
4. `KsenaxFlashlightTool.kt`
5. `KsenaxModelInstallService.kt`
6. `KsenaxDownloadWrapper.kt`
7. `KsenaxModelDownloader.kt`
8. UI-компоненты из `ui/generalscreen`

Почему так: сначала нужно понять пользовательский поток, потом модельный runtime, потом JSON-router, потом Android-tool, и только после этого механику скачивания модели.

## Большая схема

```text
app/src/main/java/com/kolesnikovprod/ksetaorch
|
|-- MainActivity.kt
|   Экран, состояние MVP, запуск скачивания, запуск модели, выполнение route.
|
|-- conv/
|   |-- KsenaxLocalConversation.kt
|       Обертка над LiteRT-LM Engine + Conversation.
|
|-- tools/
|   |-- KsenaxTorchActionRouter.kt
|   |   System prompt, route-модели, JSON parser, executor для фонарика.
|   |
|   |-- KsenaxFlashlightTool.kt
|       Реальное Android-действие: CameraManager.setTorchMode(...).
|
|-- download/
|   |-- KsenaxModelInstallService.kt
|   |   Facade для экрана: старт/отмена загрузки, проверка файла, путь модели.
|   |
|   |-- KsenaxDownloadWrapper.kt
|   |   "Паспорт" Gemma 4 E2B: URL, имя файла, размер, SHA256.
|   |
|   |-- KsenaxModelDownloader.kt
|   |   Низкий слой DownloadManager + файлы + SHA256.
|   |
|   |-- KsenaxDownloader.kt
|       Минимальный контракт "умею поставить файл в загрузку".
|
|-- ui/generalscreen/
|   Визуальные Compose-компоненты главного экрана.
```

## Главный runtime-flow: отправка команды

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/MainActivity.kt`

Когда модель уже скачана и проверена, `AgentPromptComposer` показывает поле ввода и кнопку отправки.

При нажатии:

```text
onSend
 -> берет promptText.trim()
 -> очищает поле ввода
 -> ставит isRoutingPrompt = true
 -> создает KsenaxLocalConversation, если ее еще нет
 -> initialize() запускает LiteRT-LM Engine
 -> ask("Команда пользователя:\n$userCommand")
 -> KsenaxTorchRouteParser.parse(modelResponse)
 -> executeTorchRoute(route)
 -> KsenaxTorchToolExecutor.execute(route)
 -> KsenaxFlashlightTool.turnFlashlightOn/Off()
```

Важно: `KsenaxLocalConversation` сохраняется в `localConversation`, чтобы не грузить модель заново на каждое сообщение. Engine тяжелый, его нельзя создавать на каждый клик без нужды.

## `MainActivity`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/MainActivity.kt`

`MainActivity` сейчас содержит две сущности:

```kotlin
class MainActivity : ComponentActivity()
private fun KsenaxHomeScreen()
```

`MainActivity.onCreate()` делает только Android-старт:

```text
enableEdgeToEdge()
setContent { MaterialTheme { KsenaxHomeScreen() } }
```

`KsenaxHomeScreen()` сейчас является главным MVP-оркестратором экрана. Он держит:

```text
promptText              текст, который ввел пользователь
isDownloadRemembered    модель считается установленной
isDownloading           идет загрузка модели
isDownloadInterrupted   загрузка/проверка сорвалась
isDownloadCancelled     пользователь отменил загрузку
isModelIndexing         идет проверка файла модели
isRoutingPrompt         команда сейчас у модели/action-router
downloadProgress        прогресс DownloadManager
currentDownloadId       id активной системной загрузки
localConversation       живой LiteRT-LM runtime
pendingTorchRoute       route, который надо повторить после camera permission
```

Что хорошо помнить: `MainActivity` сейчас временно перегружен. В нем живут UI-state, загрузка модели, запуск runtime и исполнение tool. Это нормально для первого MVP, но позже это стоит вынести во ViewModel/state holder.

## `AgentPromptComposer`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/ui/generalscreen/AgentPromptComposer.kt`

Это визуальный компонент нижней части экрана.

Он сам не знает про LiteRT-LM, JSON, фонарик, DownloadManager и Android permissions. Он только выбирает, что показать:

```text
если isDownloaded == false -> кнопка скачивания модели
если isDownloaded == true  -> поле ввода prompt + кнопка "Отправить"
```

Когда `isSending == true`, кнопка показывает:

```text
Маршрутизирую...
```

Это означает, что команда уже ушла в локальную модель/action-router, и повторный клик временно заблокирован.

## `KsenaxLocalConversation`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/conv/KsenaxLocalConversation.kt`

Это тонкая обертка над LiteRT-LM:

```text
modelPath
 -> EngineConfig
 -> Engine
 -> initialize()
 -> createConversation(systemInstruction)
 -> sendMessage(prompt)
```

Главные методы:

```kotlin
suspend fun initialize()
suspend fun ask(prompt: String): String
fun close()
```

`initialize()`:

```text
создает Engine
инициализирует модель
создает Conversation с systemInstruction
сохраняет engine/conversation в поля класса
```

`ask()`:

```text
проверяет, что conversation уже создана
отправляет prompt в модель
возвращает ответ модели как String
```

Почему там `Dispatchers.Default`: запуск модели и генерация не должны выполняться на UI-потоке. Иначе интерфейс может зависнуть.

Важная мысль: это не Android-tool слой. Этот класс только разговаривает с моделью.

## `KSENAX_TORCH_ROUTER_SYSTEM_PROMPT`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/tools/KsenaxTorchActionRouter.kt`

Это техническая инструкция для модели. В текущем MVP она специально сужена до фонарика:

```text
разрешено:
- torch_on
- torch_off
- refuse

запрещено:
- Wi-Fi
- будильники
- календарь
- звонки
- секреты/API keys
- root-действия
- управление чужими приложениями
- любые tools не из allowlist
```

Почему так жестко: для первого MVP нужно доказать маленький безопасный контур. Если модель попытается придумать `open_wifi_panel`, `call_user`, `delete_file` или что-то еще, парсер и executor это не выполнят.

## `KsenaxTorchRoute`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/tools/KsenaxTorchActionRouter.kt`

Это Kotlin-представление того, что выбрала модель:

```kotlin
sealed interface KsenaxTorchRoute {
    data object TorchOn
    data object TorchOff
    data class Refused(val reason: String)
}
```

Модель возвращает сырой JSON. Приложение не должно носить этот JSON по всему коду. Поэтому JSON сразу превращается в понятный Kotlin-route.

## `KsenaxTorchRouteParser`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/tools/KsenaxTorchActionRouter.kt`

Это защита между моделью и Android-действиями.

Он делает:

```text
1. достает JSON-объект из текста модели;
2. парсит его через JSONObject;
3. смотрит refusal;
4. смотрит tool_calls[0].name;
5. принимает только torch_on, torch_off, refuse;
6. все остальное превращает в Refused.
```

Почему это важно: model output нельзя исполнять напрямую. Даже если system prompt хороший, модель может ошибиться, добавить текст, придумать tool или вернуть мусор. Parser обязан быть строгим.

Утренний ответ на вопрос "где гарантия, что модель не выполнит лишнее?":

```text
В KsenaxTorchRouteParser и KsenaxTorchToolExecutor.
Parser принимает только allowlist.
Executor умеет выполнить только TorchOn/TorchOff.
```

## `KsenaxTorchToolExecutor`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/tools/KsenaxTorchActionRouter.kt`

Это простой исполнитель route:

```text
TorchOn  -> flashlightTool.turnFlashlightOn()
TorchOff -> flashlightTool.turnFlashlightOff()
Refused  -> KsenaxToolResult.Failure(reason)
```

Executor не спрашивает модель и не парсит JSON. Он получает уже проверенный route.

## `KsenaxFlashlightTool`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/tools/KsenaxFlashlightTool.kt`

Это реальный Android-tool для фонарика.

Он работает через:

```kotlin
CameraManager.setTorchMode(cameraId, enabled)
```

Перед действием проверяет:

```text
у устройства есть FEATURE_CAMERA_FLASH
приложению выдан Manifest.permission.CAMERA
есть камера с FLASH_INFO_AVAILABLE == true
лучше выбрать back-facing камеру
```

Методы:

```kotlin
fun turnFlashlightOn(): KsenaxToolResult
fun turnFlashlightOff(): KsenaxToolResult
fun setFlashlightEnabled(enabled: Boolean): KsenaxToolResult
```

Важно: этот класс сам не показывает permission dialog. Он возвращает `PermissionRequired`, а UI-слой уже решает, как запросить разрешение.

## `KsenaxToolResult`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/tools/KsenaxFlashlightTool.kt`

Единый результат выполнения локального tool:

```kotlin
Success(message)
Failure(message)
PermissionRequired(permission, message)
```

Зачем нужен:

```text
tool не должен бросать наружу все Android-исключения;
UI должен получить понятный итог;
permission-case должен быть отдельным, а не обычной ошибкой.
```

В `MainActivity.executeTorchRoute()` если результат `PermissionRequired`, запускается Android permission request для `CAMERA`. Если пользователь разрешил доступ, route повторяется.

## `KsenaxModelInstallService`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/download/KsenaxModelInstallService.kt`

Это facade между экраном и модельной установкой.

Экран обращается к нему, когда нужно:

```text
получить сохраненный downloadId
стартовать загрузку Gemma 4 E2B
отменить загрузку
очистить download artifacts
проверить candidate file
проверить валидный файл через size/SHA256
удалить файл модели
получить путь к .litertlm файлу
опросить DownloadManager по downloadId
```

Самый важный метод для нового MVP:

```kotlin
fun getGemma4E2BModelPath(): String
```

Он дает путь, который потом передается в `KsenaxLocalConversation`.

## `KsenaxDownloadWrapper`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/download/KsenaxDownloadWrapper.kt`

Это "паспорт" конкретной модели Gemma 4 E2B.

Здесь лежат:

```text
GEMMA_4_E2B_MODEL_NAME
GEMMA_4_E2B_URL
GEMMA_4_E2B_FILE_NAME
GEMMA_4_E2B_SIZE_BYTES
GEMMA_4_E2B_SHA256
```

Именно wrapper превращает общий downloader в операции конкретной модели:

```text
downloadGemma4E2B()
hasGemma4E2BFile()
hasValidGemma4E2BFile()
deleteGemma4E2BFile()
getGemma4E2BModelPath()
```

Почему это удобно: если потом появится Gemma 3n или FunctionGemma, для них можно сделать аналогичный паспорт, не переписывая низкий downloader.

## `KsenaxModelDownloader`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/download/KsenaxModelDownloader.kt`

Это низкоуровневый Android downloader.

Он знает про:

```text
DownloadManager
DownloadManager.Request
app-specific external files dir
models/<modelName>/<fileName>
удаление файла
проверку exists/length
проверку size/SHA256
```

Он не знает про:

```text
UI
Compose
LiteRT-LM
фонарик
action-router
конкретный prompt
```

Главное правило: `STATUS_SUCCESSFUL` от DownloadManager еще не означает, что модель рабочая. Поэтому после загрузки всегда идет проверка размера и SHA256.

## `KsenaxDownloader`

Файл: `app/src/main/java/com/kolesnikovprod/ksetaorch/download/KsenaxDownloader.kt`

Минимальный контракт:

```kotlin
fun download(url: String, fileName: String, modelName: String): Long
```

Он нужен, чтобы верхние слои не зависели напрямую от конкретной реализации скачивания. Сейчас реализация - `KsenaxModelDownloader` через Android `DownloadManager`.

## UI-компоненты

Папка: `app/src/main/java/com/kolesnikovprod/ksetaorch/ui/generalscreen`

### `KsenaxHeader`

Рисует заголовок `Ksenax` и подпись про Gemma-4. Логики модели нет.

### `KsenaxNebulaBackground`

Рисует фон через Compose `Canvas`. Логики модели нет.

### `LocalAgentBottomBar`

Рисует подпись "Работает локально на вашем устройстве". Логики модели нет.

### `ModelIndexingOverlay`

Показывается во время проверки файла модели. Сейчас "индексация" означает size/SHA256-проверку, а не настоящий embedding-index.

### `KsenaxIndexingLogo`

Анимированный текст `Ksenax` для overlay проверки модели.

## Что происходит при permission на камеру

В `AndroidManifest.xml` уже есть:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Но manifest-разрешение не равно runtime-разрешению. На современных Android пользователь должен разрешить камеру отдельно.

Поток такой:

```text
модель выбрала torch_on
 -> executor вызывает flashlight tool
 -> tool видит, что CAMERA permission нет
 -> возвращает PermissionRequired
 -> MainActivity сохраняет pendingTorchRoute
 -> запускает RequestPermission
 -> пользователь разрешает
 -> MainActivity повторяет pendingTorchRoute
 -> фонарик включается
```

Если пользователь отказал, route не выполняется.

## Что происходит, если модель вернула плохой JSON

`KsenaxTorchRouteParser` не падает наружу обычной ошибкой. Он возвращает:

```kotlin
KsenaxTorchRoute.Refused("JSON от модели не удалось разобрать.")
```

После этого executor превратит это в:

```kotlin
KsenaxToolResult.Failure(reason)
```

А экран покажет короткий `Toast`.

## Что сейчас является MVP, а не финальной архитектурой

Сейчас для скорости MVP несколько вещей живут в `MainActivity`:

```text
UI-state
download polling
model initialization
prompt routing
permission request
tool execution
Toast output
```

Это нормально на этапе "доказать вертикальный срез". Следующий аккуратный шаг:

```text
MainActivity/Compose -> только UI и события
KsenaxHomeViewModel -> состояние экрана и user actions
KsenaxAgentOrchestrator -> prompt -> route -> execute
KsenaxLocalConversation -> LiteRT-LM
tools/* -> Android tools
download/* -> установка модели
```

Но прямо сейчас не надо перепрыгивать туда, пока не проверен реальный запуск модели на телефоне.

## Ночные ответы на типовые вопросы

### Где приложение понимает, что модель скачана?

В `KsenaxModelInstallService.hasValidGemma4E2BFile()`, через wrapper/downloader. Проверяются размер и SHA256.

### Где берется путь к модели?

`KsenaxModelInstallService.getGemma4E2BModelPath()`.

### Где лежит runtime-кэш модели?

`KsenaxModelInstallService.getGemma4E2BCacheDirPath()`.

Физически это папка рядом с `.litertlm` файлом:

```text
Android/data/<package>/files/models/gemma-4-e2b/cashed
```

Этот путь передается в `EngineConfig(cacheDir = ...)`, чтобы runtime-мусор LiteRT-LM
не разъезжался по неявным директориям.

### Где запускается локальная модель?

В `MainActivity.onSend`, через `KsenaxLocalConversation.initialize()`.

### Где задается технический prompt для action-router?

В `KSENAX_TORCH_ROUTER_SYSTEM_PROMPT`.

### Где пользовательский prompt уходит в модель?

В `conversation.ask("Команда пользователя:\n$userCommand")`.

### Где JSON модели превращается в действие?

В `KsenaxTorchRouteParser.parse(modelResponse)`.

### Где стоит allowlist tools?

В `KsenaxTorchRouteParser` и `KsenaxTorchToolExecutor`.

### Где реально включается фонарик?

В `KsenaxFlashlightTool.setFlashlightEnabled()`, через `CameraManager.setTorchMode(...)`.

### Почему модель не может включить что-то кроме фонарика?

Потому что текущий parser принимает только `torch_on`, `torch_off`, `refuse`, а executor умеет исполнить только route фонарика.

### Почему при первом запуске может быть долго?

Потому что `KsenaxLocalConversation.initialize()` поднимает LiteRT-LM Engine и открывает `.litertlm` модель. Это тяжелая операция.

### Почему после первого запуска быстрее?

Потому что `localConversation` сохраняется и переиспользуется, пока экран жив.

## Главная ментальная формула

```text
download/* отвечает за "модель есть на диске и целая".
conv/* отвечает за "сказать что-то локальной модели".
tools/* отвечает за "безопасно превратить route в Android-действие".
ui/* отвечает за "нарисовать экран".
MainActivity сейчас временно склеивает все это в MVP-flow.
```
