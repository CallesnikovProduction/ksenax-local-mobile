# AGENTS.md

## Назначение

`storage` хранит текстовые артефакты Ksenax: заметки, markdown-файлы, plain-text файлы и другие результаты агентных tools.

Tool не выбирает папку и не работает с Android API напрямую. Tool вызывает `KsenaxTextFileResolver`, получает DTO и показывает пользователю понятный результат. Storage берёт на себя Android-реализм: `File`, `MediaStore`, SAF, URI, разрешения и ошибки доступа.

## Пакеты и файлы

- `KsenaxTextFileResolver.kt` - главный контракт текстового storage, enum форматов и общие helper-ы.
- `dto/KsenaxTextFileLocation.kt` - место файла плюс `KsenaxTextFileStorageKind`.
- `dto/KsenaxTextFileReadResult.kt` - результат чтения: `Success`, `NotFound`, `Failure`.
- `dto/KsenaxTextFileWriteResult.kt` - результат записи: `Success`, `Failure`.
- `resolve/text/FallbackPrivateTextArtifactStorage.kt` - fallback в `Context.filesDir/<directoryName>`.
- `resolve/text/DocumentsFolderTextFileManager.kt` - видимая папка `Documents\ksenax-workspace`; временный MVP-backend.
- `resolve/text/UserSelectedTextWorkspaceStorage.kt` - SAF-backed рабочая папка, выбранная пользователем и сохранённая в Agentic-чате.

## Контракт Resolver

`KsenaxTextFileResolver` даёт общий API для всех backend-ов:

- `val destinationDescription: String` - строка для UI и логов. Не парсить как путь и не использовать как backend-id.
- `suspend fun readText(fileName: String): KsenaxTextFileReadResult` - читает только одиночное имя файла внутри текущей директории.
- `suspend fun writeText(fileName: String, text: String, format: KsenaxTextFileFormat): KsenaxTextFileWriteResult` - пишет текст и сверяет имя с форматом.
- `fun String.isSafeSingleFileName(): Boolean` - safety-check имени файла. Отклоняет пустые строки, `.`, `..`, завершающую точку, `/` и `\`.

Контракт считает `fileName` именем файла, а не путём. Подпапки пока запрещены. Если понадобится `folder/note.md`, сначала опиши policy: где можно создавать подпапки, как нормализовать имена и как возвращать location.

## Форматы

`KsenaxTextFileFormat` сейчас поддерживает:

- `MARKDOWN`: `md`, `markdown`, MIME `text/markdown`.
- `PLAIN_TEXT`: `txt`, MIME `text/plain`.

`KsenaxTextFileFormat.resolveFileName(fileName)` возвращает:

- `MissingExtension` для `note`.
- `Supported(format, extension)` для `note.md`, `note.markdown`, `note.txt`.
- `UnsupportedExtension(extension)` для `note.pdf` и других неподдерживаемых расширений.

При записи backend может добавить расширение из `format`, если расширения нет. Если расширение есть и конфликтует с `format`, backend должен вернуть `Failure`. Не превращать `note.pdf` в `note.pdf.md`.

## Marker рабочей области

Каждый backend, который пишет в директорию, создаёт `there.ksenaxzone`.

Имя, MIME и текст marker-файла лежат в `KsenaxTextFileResolver`:

- `WORKSPACE_ZONE_FILE_NAME = "there.ksenaxzone"`.
- `WORKSPACE_ZONE_MIME_TYPE = "application/octet-stream"`.
- `WORKSPACE_ZONE_DOCUMENT_TEXT` - короткая памятка для пользователя.
- `File.ensureWorkspaceZoneMarkerFile()` - helper для обычных `File`-директорий.

Marker нужен, чтобы пользователь и UI видели: папка инициализирована как рабочая область Ksenax. Это не конфиг, не секрет и не база состояния. Новый marker получает текущую директорию в конце текста. Существующему marker-у storage сохраняет содержимое и дописывает отсутствующую строку директории. SAF и MediaStore оставляют один документ с точным именем `there.ksenaxzone`, удаляя только его дубликаты.

Generic MIME выбран намеренно: с `text/plain` Android provider сопоставляет MIME с расширением и превращает нестандартное имя в `there.ksenaxzone.txt`, а при конфликте создаёт `there.ksenaxzone (N).txt`. Текст внутри marker-а по-прежнему записывается как UTF-8.

`KsenaxTextFileResolver.isWorkspaceZoneMarkerDisplayName()` распознаёт точное имя и старые provider-имена с `.txt`. При следующей инициализации MediaStore/SAF backend сохраняет текст первого marker-а, создаёт канонический `there.ksenaxzone` и удаляет legacy-дубликаты. Инициализация защищена общим process-local lock, потому что разные чаты могут одновременно создать resolver для одной директории.

## DTO

`KsenaxTextFileLocation`:

- `displayPath` - человекочитаемое место файла. Не использовать как стабильный machine path.
- `storageKind` - машинная категория backend-а.
- `uri` - строковый Android URI, если backend его выдаёт.

`KsenaxTextFileStorageKind`:

- `APP_PRIVATE` - `Context.filesDir`.
- `MEDIA_STORE` - Android 10+ через MediaStore.
- `PUBLIC_DOCUMENTS` - legacy File API для публичной Documents-папки.
- `SAF` - папка, выбранная пользователем через Storage Access Framework.
- `ANDROID_FOLDER` - reserved signal для policy-слоя, если пользователь выбрал системную Android-папку.

`KsenaxTextFileReadResult`:

- `Success(text, location)` - файл прочитан как UTF-8.
- `NotFound(fileName)` - файла нет в текущем backend-е.
- `Failure(message)` - ожидаемая ошибка доступа, формата или storage API.

`KsenaxTextFileWriteResult`:

- `Success(location)` - файл записан.
- `Failure(message)` - запись не выполнена.

## Backend API

`FallbackPrivateTextArtifactStorage(context, directoryName = "tool-artifacts")`:

- Хранит файлы в `context.applicationContext.filesDir/directoryName`.
- Возвращает `APP_PRIVATE`.
- Создаёт `there.ksenaxzone` через `File.ensureWorkspaceZoneMarkerFile()`.
- Нужен как надёжный fallback, когда пользовательская папка ещё не выбрана или SAF недоступен.

`DocumentsFolderTextFileManager(context)`:

- Пишет в `Documents\ksenax-workspace`.
- Android 10+ использует `MediaStore.Files` и `RELATIVE_PATH`.
- Android 9- использует `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)`.
- Возвращает `MEDIA_STORE` на Android 10+ и `PUBLIC_DOCUMENTS` на Android 9-.
- Создаёт `there.ksenaxzone`: через MediaStore на Android 10+, через `File` на Android 9-.
- Используется Agentic-чатом по умолчанию, если пользователь не выбрал SAF-папку.
- Это MVP-backend для ручной проверки. Целевая UX-модель идёт через SAF.

`UserSelectedTextWorkspaceStorage(context, workspaceTreeUri, workspaceDisplayName)`:

- Работает с уже выбранным SAF `treeUri`.
- Не запускает picker внутри `readText()` или `writeText()`.
- `initializeWorkspaceZone()` создаёт `there.ksenaxzone` и возвращает `KsenaxTextFileWriteResult`.
- `buildDirectoryPickerIntent()` создаёт `Intent.ACTION_OPEN_DOCUMENT_TREE` с read/write/persistable/prefix flags.
- `takePersistableReadWritePermission(context, treeUri, resultFlags)` сохраняет долгоживущий доступ к выбранной папке.
- `writeText()` ищет документ по имени, создаёт его при отсутствии и пишет через `ContentResolver.openOutputStream(uri, "wt")`.
- `readText()` ищет прямого ребёнка выбранной папки и читает через `ContentResolver.openInputStream(uri)`.
- Возвращает `SAF`; реальная capability лежит в `KsenaxTextFileLocation.uri`.

## UI Flow Для SAF

1. UI запускает `UserSelectedTextWorkspaceStorage.buildDirectoryPickerIntent()`.
2. Пользователь выбирает папку.
3. UI получает `treeUri` и `resultFlags`.
4. UI вызывает `takePersistableReadWritePermission(context, treeUri, resultFlags)`.
5. UI сохраняет `treeUri.toString()` в настройках приложения.
6. UI создаёт `UserSelectedTextWorkspaceStorage(context, treeUri, displayName)`.
7. UI вызывает `initializeWorkspaceZone()` и показывает результат.
8. Tools дальше работают только через `KsenaxTextFileResolver`.

## Правила Правок

- Не протаскивать `Context`, `ContentResolver`, `Uri`, `MediaStore`, `DocumentsContract` в tools и orchestration.
- Не использовать `destinationDescription` как путь или backend-id.
- Не добавлять поддержку нового расширения без обновления `KsenaxTextFileFormat`.
- Не затирать пользовательский текст в существующем `there.ksenaxzone`; разрешено идемпотентно дописать строку текущей директории.
- Не возвращать marker-у MIME `text/plain`: это снова включит автоматическую генерацию `.txt` и числовых дубликатов в Android provider-е.
- Не усекать существующий пользовательский файл напрямую. При overwrite сначала писать новый текст во временный sibling-файл или temporary-документ, затем заменять исходный файл.
- Не бросать ожидаемые Android storage errors наружу. Возвращать DTO `Failure`.
- Не трогать чужие модули в этом чате без явного запроса. Сейчас старые вызовы в `ObsidianNotesTool` и `KsenaxHomeViewModel` могут не совпадать с новым API.

## Долги На 2026-06-19

- Решить, нужен ли отдельный policy-check для выбора опасных папок вроде Android/system roots.
- Синхронизировать старые consumers (`ObsidianNotesTool`, `KsenaxHomeViewModel`) с новым `KsenaxTextFileResolver`.

## Overwrite Policy

MVP-запись через прямой `writeText()` или `openOutputStream(uri, "wt")` поверх существующего файла опасна: сбой в середине записи может оставить пустой или частично записанный файл.

Текущая политика:

- `FallbackPrivateTextArtifactStorage` пишет sibling temp-файл рядом с target и заменяет target через `Files.move`.
- `DocumentsFolderTextFileManager` на Android 9- делает такой же temp-file replace через `Files.move`.
- `DocumentsFolderTextFileManager` на Android 10+ пишет temporary MediaStore-документ, переименовывает старый файл в backup, переименовывает temporary-документ в исходное имя и удаляет backup.
- `UserSelectedTextWorkspaceStorage` делает ту же схему через `DocumentsContract.renameDocument`.

Для `File` backend-ов сначала пробуй `StandardCopyOption.ATOMIC_MOVE`, затем fallback на `REPLACE_EXISTING`, если filesystem не поддерживает atomic move. Для MediaStore и SAF честной POSIX-атомарности нет, поэтому используй staged replace с backup и cleanup.
