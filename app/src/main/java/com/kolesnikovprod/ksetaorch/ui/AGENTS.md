# AGENTS.md для `com.kolesnikovprod.ksetaorch.ui`

## Назначение UI-слоя

Эта папка отвечает за Compose-интерфейс Ksenax: главный экран, состояние домашнего экрана, визуальные компоненты общего экрана и небольшие theme-утилиты.

Главная точка сборки сейчас - `KsenaxHomeScreen.kt`. Его нужно воспринимать не как обычный "экран с кнопками", а как временный orchestration boundary между:

- Compose UI;
- состоянием установки модели;
- локальной conversation с Gemma через LiteRT-LM;
- разрешениями Android;
- allowlisted tool execution;
- визуальными состояниями загрузки, валидации, диалога и busy-режима.

Пока проект находится в исследовательской фазе, допустимо, что `KsenaxHomeScreen.kt` содержит больше логики, чем должен содержать зрелый экран. Но при любых новых изменениях нужно помнить: этот файл уже стал нервным центром, и дальнейшее разрастание лучше делать осознанно.

## Что обнаружено в `KsenaxHomeScreen.kt`

`KsenaxHomeScreen` сейчас совмещает несколько ролей.

### 1. Владелец локального UI-состояния

Экран держит:

- `promptText` - текст на стартовом экране;
- `dialogueInputText` - текст в режиме открытого диалога;
- `uiState` - набор UI-флагов в `HomeUiState`;
- `dialogueMessages` - историю сообщений диалога;
- `nextDialogueMessageId` - счетчик id сообщений;
- `currentDownloadId` - id текущей загрузки модели;
- `pendingTorchRoute` - отложенный route после запроса permission;
- `localConversation` - живой runtime-объект общения с локальной моделью.

Оценка: идея с `HomeUiState` правильная. Она уже убрала пачку отдельных boolean-переменных и сделала обновления через `copy(...)` более читаемыми. Но не все состояние обязано жить в `HomeUiState`: runtime-ресурсы вроде `localConversation`, permission-pending route и download id могут оставаться отдельными, пока нет ViewModel.

### 2. Владелец Android context-bound объектов

Через `LocalContext.current` создаются:

- `KsenaxModelInstallService`;
- `KsenaxFlashlightTool`;
- `KsenaxTorchToolExecutor`.

Хороший момент: сервисы создаются через `remember(...)`, а для долгоживущих объектов используется `context.applicationContext`. Это снижает риск утечек Activity.

### 3. Контур скачивания и валидации модели

`LaunchedEffect(currentDownloadId, installService)` отвечает за:

- проверку уже лежащего на диске candidate-файла;
- включение/выключение `isModelValidating`;
- наблюдение за `DownloadManager` через `queryDownloadSnapshot`;
- обновление `downloadProgress`;
- обработку `SUCCESSFUL`, `FAILED`, `PENDING`, `RUNNING`, `PAUSED`, `UNKNOWN`;
- очистку артефактов после невалидной/упавшей загрузки.

Оценка: логика рабочая и уже достаточно честная для прототипа. Самое хрупкое место - пересечение cancel/interrupted/validation веток. Если пользователь отменяет загрузку, а затем `currentDownloadId` становится `NO_DOWNLOAD_ID`, эффект снова может проверить локальный candidate-файл. В будущей итерации стоит явно развести:

- "пользователь отменил";
- "загрузка технически упала";
- "скачанный файл невалиден";
- "файл найден на диске и проходит проверку".

### 4. Контур локального агента

`sendPromptToLocalAgent(userCommand)` делает:

- защиту от пустого ввода и повторной отправки во время `isRoutingPrompt`;
- открытие диалогового экрана;
- добавление пользовательского сообщения;
- включение busy-флага;
- ленивую инициализацию `KsenaxLocalConversation`;
- отправку команды в модель;
- парсинг ответа в `KsenaxTorchRoute`;
- выполнение route через `executeTorchRoute`;
- выключение busy-флага в `finally`.

Оценка: для исследовательского ядра это хорошая прозрачная версия. Функция уже явно является оркестратором, и в коде верно оставлен TODO на вынос. Когда сценариев станет больше фонарика, эту часть лучше отделить от UI.

### 5. Контур Android permission

`rememberLauncherForActivityResult(RequestPermission())` используется для запроса камеры, потому что фонарик на Android управляется через camera permission.

Экран хранит `pendingTorchRoute`, чтобы после permission callback продолжить действие.

Оценка: подход нормальный. Важно сохранять защиту от повторного исполнения:

```kotlin
val route = pendingTorchRoute
pendingTorchRoute = null
```

Это правильный предохранитель.

### 6. Контур отрисовки

В конце файла UI разделен на два режима:

- если `uiState.isConversationOpen`, показывается `AgentDialogueWindow`;
- иначе показывается стартовый экран через `Scaffold`, `KsenaxHeader`, `AgentPromptComposer`, `LocalAgentBottomBar`.

Поверх обоих режимов может появляться:

- `ModelIndexingOverlay`, если `uiState.isModelValidating`.

Оценка: композиция понятная. Хорошо, что визуальные компоненты уже вынесены в `ui.generalscreen`, а `KsenaxHomeScreen` только прокидывает параметры.

## Архитектурная оценка без сахарной глазури

Текущий `KsenaxHomeScreen.kt` - это уже не просто composable. Это "экран-контроллер". Для прототипа это нормально, особенно когда цель - руками понять Compose, download lifecycle, permission callback и локальную модель. Комментарии очень подробные, иногда избыточные, но на этой стадии они работают как учебная трассировка мышления. Это не стыдно, это способ вытащить скрытую механику наружу.

Что хорошо:

- состояние постепенно собирается в `HomeUiState`;
- side effects не выполняются прямо в теле composable, а вынесены в `LaunchedEffect`, `DisposableEffect`, callbacks и coroutine launch;
- runtime-ресурсы создаются через `remember`;
- UI-компоненты уже вынесены из главного файла;
- есть ясная граница allowlisted tool execution;
- комментарии объясняют не только "что", но и "почему".

Что начинает давить:

- `KsenaxHomeScreen` уже знает слишком много о скачивании, валидации, permission, conversation, route parsing и tool execution;
- функции внутри composable становятся похожи на controller/service-методы;
- download-state machine пока выражена набором boolean-флагов, а не sealed state;
- `LaunchedEffect` с долгим циклом и чтением `uiState` требует аккуратности, чтобы не получить устаревшие решения при recomposition;
- комментариев много настолько, что позже они могут начать мешать видеть саму структуру.

Итоговая личная оценка: это не "ужасный Compose", а честный исследовательский экран, в котором человек реально разбирался, где что живет. Он уже перешел точку "я просто накидал UI" и стал местом, где видна архитектурная мысль. Но это также файл, который скоро попросится на разгрузку.

## Правила для дальнейших изменений в этой папке

### Не раздувать `KsenaxHomeScreen` без необходимости

Если новая логика относится к:

- скачиванию модели;
- проверке файла;
- route parsing;
- tool execution;
- conversation lifecycle;
- permission orchestration;

сначала подумать, можно ли вынести это в отдельный controller/service/state holder, а в `KsenaxHomeScreen` оставить только связывание UI и callbacks.

### Не трогать `OnDownloaded`/`OnNonDownloaded` без причины

Компоненты в `ui.generalscreen` уже отвечают за визуальные состояния. Если нужно поменять внешний workflow, сначала менять параметры и state в `KsenaxHomeScreen`, а не ломать визуальные компоненты.

### Обновлять `HomeUiState` через `copy`

Предпочтительный стиль:

```kotlin
uiState = uiState.copy(
    isDownloading = true,
    isDownloadInterrupted = false,
)
```

Не возвращаться к россыпи отдельных boolean-переменных в `KsenaxHomeScreen`, если поле действительно является UI-состоянием экрана.

### Разделять UI-state и runtime-state

Можно держать в `HomeUiState`:

- флаги видимости;
- progress;
- busy-state;
- error/cancel/interrupted flags;
- режимы экрана.

Не обязательно держать в `HomeUiState`:

- `Context`;
- сервисы;
- executor-ы;
- живую `KsenaxLocalConversation`;
- permission launcher;
- объекты route, ожидающие callback.

### С осторожностью работать с `LaunchedEffect`

`LaunchedEffect(currentDownloadId, installService)` - это side-effect download lifecycle. Если меняются ключи эффекта или логика cancel/interrupted, проверять сценарии:

- приложения запущено без модели;
- найден candidate-файл;
- файл валиден;
- файл невалиден;
- старт загрузки;
- пауза/ожидание;
- успешная загрузка;
- ошибка загрузки;
- отмена пользователем;
- перезапуск приложения с сохраненным download id.

### Комментарии можно оставлять учебными, но не превращать в шум

Сейчас подробные комментарии полезны. В дальнейшем при стабилизации кода лучше сохранять комментарии там, где есть:

- Android lifecycle nuance;
- permission callback;
- Compose side-effect;
- state-machine branch;
- локальная модель или tool execution;
- защита от повторного действия.

Комментарии вида "прибавляем 1" или "тут переменная" позже можно будет снять, когда код станет привычным.

## Будущая разгрузка файла

Когда появится время, естественные кандидаты на вынос:

1. `sendPromptToLocalAgent` -> agent/orchestrator controller.
2. `executeTorchRoute` + permission bridge -> tool execution coordinator.
3. download loop из `LaunchedEffect` -> download state holder или ViewModel.
4. `dialogueMessages` operations -> dialogue state holder.
5. `HomeUiState` booleans download-группы -> sealed download UI state.

Возможный будущий вид:

```text
KsenaxHomeScreen
├── читает HomeUiState
├── вызывает callbacks
├── рисует AgentPromptComposer / AgentDialogueWindow
└── не знает деталей DownloadManager и LiteRT-LM initialization
```

Но пока для курсового/исследовательского этапа текущая концентрация логики допустима: она помогает видеть весь вертикальный сценарий от кнопки до локального tool execution.

## Краткая ментальная модель файла

`KsenaxHomeScreen.kt` сейчас отвечает за вопрос:

> Что должен видеть пользователь и какой локальный процесс нужно запустить, когда пользователь нажал на UI?

Он не должен навсегда отвечать за вопрос:

> Как именно устроены все низкоуровневые процессы?

Именно туда стоит двигать архитектуру дальше.
