# AGENTS.md for `storage/chat`

## Назначение

Эта директория хранит постоянную историю чатов Ksenax через Room и SQLite.
Она отвечает за схему базы, SQL-запросы, транзакции и преобразование данных
между Room entity и chat domain.

Текущая версия контура: `0.2`.

Рабочая граница по умолчанию:

```text
app/src/main/java/com/kolesnikovprod/ksetaorch/storage/chat
```

Не редактировать UI, ViewModel, application graph и соседние storage-пакеты,
если пользователь не запросил интеграцию шире этой директории. Читать внешние
call sites можно для проверки реального потока.

## Состояние интеграции

Room-контур подключён к отдельному destination обычного Basic-чата.

Активные UI-потоки:

```text
Basic:
KsenaxBasicChatScreen
    -> KsenaxBasicChatViewModel
    -> KsenaxChatRepository
    -> Room / SQLite

Main side panel:
KsenaxMainViewModel
    -> KsenaxChatRepository.chats
    -> domain-to-presentation mapper
    -> KsenaxSidePanel

Agentic:
KsenaxAgenticChatScreen
    -> KsenaxAgenticChatViewModel
    -> KsenaxAgentCoordinator
    -> KsenaxChatRepository
    -> Room / SQLite
```

Basic- и Agentic-чаты сохраняются в Room и отображаются в side panel на всех
chat destination. У Agentic-чата в строке `chats` также сохраняются SAF tree
URI и отображаемый путь рабочей директории. `NULL` в `workspace_tree_uri`
означает default workspace `Documents/ksenax-workspace`; непустой URI означает
конкретную SAF-папку. Все tool-вызовы чата собираются вокруг одного resolver-а.

`Temporaric` намеренно не является storage-режимом: он отсутствует в
`KsenaxStoredChatMode`, Room converters и таблицах. Его сообщения существуют
только в process-scoped UI state и не должны добавляться в этот контур.

Целевой поток:

```text
UI event
    -> chat ViewModel
    -> KsenaxChatRepository
    -> RoomKsenaxChatRepository
    -> KsenaxChatDao
    -> KsenaxChatDatabase
    -> SQLite

SQLite change
    -> DAO Flow
    -> repository domain models
    -> ViewModel UI state
    -> Compose UI
```

## Быстрый выбор файлов

Не читать всю директорию перед каждой правкой. Выбирать минимальный маршрут:

| Задача | Открыть сначала | Открывать дальше только если нужно |
|---|---|---|
| Понять публичный chat-storage API | `domain/KsenaxChatRepository.kt` | domain-модели |
| Понять данные, доступные ViewModel | `domain/model/KsenaxStoredChat.kt`, `KsenaxStoredMessage.kt` | repository-контракт |
| Изменить сценарий сохранения | `data/RoomKsenaxChatRepository.kt` | DAO и затронутые entity |
| Добавить или изменить SQL | `data/local/KsenaxChatDao.kt` | entity, repository |
| Изменить таблицу или индекс | соответствующая entity | database, schema JSON, migration |
| Изменить связь chat-message | обе entity и `KsenaxChatWithMessages.kt` | DAO, repository |
| Изменить enum роли или режима | domain enum и `KsenaxChatTypeConverters.kt` | migration старых строк |
| Изменить создание базы | `KsenaxChatDatabase.kt` | application composition вне scope |
| Подключить Room к ViewModel | repository-контракт и нужная ViewModel | application graph/factory, UI mapper |
| Только объяснить архитектуру | этот файл | `ROOM-CHAT-STORAGE-GUIDE.md` для глубокого разбора |

`ROOM-CHAT-STORAGE-GUIDE.md` содержит большой учебный материал. Не загружать его
целиком для обычной правки. Открывать нужный раздел поиском по заголовку.

## Карта ответственности

### `domain`

Публичная граница chat-storage.

- `KsenaxChatRepository.kt`: операции, доступные верхним слоям.
- `KsenaxStoredChat.kt`: сохранённый чат без Room/UI зависимостей.
- `KsenaxStoredMessage.kt`: сохранённое сообщение и его роль.

Разрешённые зависимости:

```text
Kotlin stdlib
kotlinx.coroutines Flow
chat domain types
```

Запрещены:

```text
android.content.Context
androidx.room.*
Room entity
DAO
Compose
UI models/state
```

### `data`

Реализация domain-контракта.

- `RoomKsenaxChatRepository.kt`: транзакции, mapping entity/domain,
  сортировка сообщений, сценарии create/update/append/rename/delete.

Repository знает о domain и Room. Остальное приложение знает только о
repository-контракте.

### `data/local`

Локальная Room-механика.

- `KsenaxChatDatabase.kt`: состав базы, версия, converters, создание экземпляра.
- `KsenaxChatDao.kt`: SQL и простые операции над таблицами.
- `KsenaxChatTypeConverters.kt`: enum ↔ SQLite `TEXT`.

### `data/local/entity`

Форма таблиц и Room relations.

- `KsenaxChatEntity.kt`: строка `chats`.
- `KsenaxMessageEntity.kt`: строка `chat_messages`.
- `KsenaxChatWithMessages.kt`: составной результат чтения chat + messages.

Entity не покидают `storage/chat/data`.

## Граница ViewModel

ViewModel получает:

```text
KsenaxChatRepository
Flow<List<KsenaxStoredChat>>
Flow<KsenaxStoredChat?>
KsenaxStoredChat
KsenaxStoredMessage
Long ID после записи
```

ViewModel не получает:

```text
KsenaxChatDatabase
KsenaxChatDao
KsenaxChatEntity
KsenaxMessageEntity
KsenaxChatWithMessages
androidx.room.*
названия таблиц и колонок
SQLite-запросы
```

ViewModel отвечает за:

- collection `Flow` в своём lifecycle;
- выбор активного чата;
- преобразование domain-моделей в presentation state;
- запуск `saveChat`, `appendMessage`, `deleteChat` из coroutine;
- временное UI-состояние: input, loading, streaming, selection, ошибки;
- координацию ответа модели и сохранения результата.

ViewModel не должна:

- выполнять SQL;
- управлять транзакциями Room;
- создавать новый объект базы;
- вычислять следующую `position`;
- удалять сообщения перед заменой истории;
- сортировать Room entity;
- зависеть от Android Room annotations.

## Граница UI

Compose UI получает presentation state и callbacks:

```text
KsenaxMainUiState или отдельный ChatUiState
UI-модели чата и сообщения
onSendMessage(...)
onChatSelected(...)
onDeleteChat(...)
onNewChat(...)
```

UI не получает `KsenaxStoredChat` как обязательную архитектурную модель, если
экрану уже нужна собственная UI-форма. Mapping domain → UI держать возле
ViewModel/presentation mapper, а не внутри Room repository.

UI не должен:

- импортировать `storage.chat.data.*`;
- импортировать `androidx.room.*`;
- вызывать DAO или database;
- открывать транзакции;
- назначать постоянные database ID;
- решать, как `Basic/Agentic` или message role записываются в SQLite;
- хранить единственную копию постоянной истории в composable state.

UI может решать:

- как отрисовать роль;
- как выделить `isFinalAgenticStep`;
- какой чат выбран;
- какие диалоги, подтверждения и анимации показать;
- когда отправить событие во ViewModel.

## Контракт repository

`KsenaxChatRepository` является единственным storage API для ViewModel:

```kotlin
val chats: Flow<List<KsenaxStoredChat>>

fun observeChat(chatId: Long): Flow<KsenaxStoredChat?>

suspend fun saveChat(chat: KsenaxStoredChat): Long

suspend fun appendMessage(
    chatId: Long,
    message: KsenaxStoredMessage,
): Long

suspend fun renameChat(
    chatId: Long,
    title: String,
    updatedAtEpochMillis: Long,
)

suspend fun deleteChat(chatId: Long)
```

Правила:

- Добавлять метод только под реальный use case верхнего слоя.
- Не зеркалировать каждый DAO-метод в repository без потребности.
- Не возвращать entity, DAO, cursor или Room result наружу.
- Не принимать UI-модели в repository.
- Ошибки persistence преобразовывать на границе, если UI должен различать их.
  Не протаскивать SQLite exception как публичный контракт без решения.

## DAO и repository: где живёт логика

DAO содержит:

- один SQL-запрос;
- простую insert/update/delete операцию;
- Room relation query;
- вычисление, которое естественно выполняется SQL.

Repository содержит:

- несколько DAO-вызовов в одном сценарии;
- `withTransaction`;
- mapping entity ↔ domain;
- порядок шагов сохранения;
- сортировку relation-результата;
- storage-level решение create/update.

Не помещать domain/UI решения в DAO. Не размазывать одну транзакцию по
ViewModel.

## Схема и связь данных

Одна база:

```text
ksenax_chats.db
```

Две таблицы:

```text
chats 1 ---- N chat_messages
```

Связь:

```text
chat_messages.chat_id -> chats.id
```

Инварианты:

- каждое сообщение принадлежит существующему чату;
- удаление чата удаляет его сообщения через `ON DELETE CASCADE`;
- `(chat_id, position)` уникален;
- позиции одного чата начинаются с `0` и возрастают;
- repository сортирует relation messages по `position`;
- добавление сообщения обновляет `chats.updated_at_epoch_millis`;
- assistant-сообщение может хранить `generation_duration_millis`;
- Agentic-чат хранит `workspace_tree_uri` и `workspace_display_path`; nullable
  tree URI выбирает default Documents-backend;
- Basic-чат оставляет оба workspace-поля `NULL`;
- список чатов идёт по `updated_at_epoch_millis DESC`;
- `id = 0L` означает ещё не сохранённую domain/entity модель.

Не создавать отдельную Room-базу или таблицу для каждого чата.

## Транзакции

Операция требует `database.withTransaction`, если промежуточное состояние не
должно быть видимо или допустимо.

Текущие транзакции:

- `saveChat`: create/update chat → delete old messages → insert full history;
- `appendMessage`: calculate position → insert message → update chat timestamp.

`@Transaction` на relation query защищает составное чтение chat + messages.

Не переносить transaction ownership во ViewModel. Не выполнять
`deleteMessages()` и `insertMessages()` отдельными внешними вызовами.

## Room и schema migration

Любое изменение таблицы требует проверить:

```text
@Entity
@Database(version)
Migration
app/schemas/.../<version>.json
старые сохранённые данные
```

Правила:

- Не менять entity и оставлять прежнюю версию базы.
- Не повышать version без migration.
- Не добавлять `fallbackToDestructiveMigration()` для истории чатов без прямого
  решения пользователя: fallback удалит данные.
- Хранить экспортированные schema JSON.
- Добавлять индекс под подтверждённый query pattern, а не «на всякий случай».
- При переименовании колонки сохранять данные migration-скриптом.

Converters сохраняют enum через `.name`. Переименование `Basic`, `Agentic`,
`User`, `Assistant`, `System` или `Tool` требует data migration. Иначе чтение
старых строк упадёт в `enumValueOf`.

## Правила изменения моделей

### Новое поле чата

Проверить:

1. относится ли поле к persistence или остаётся UI-состоянием;
2. `KsenaxStoredChat`;
3. `KsenaxChatEntity`;
4. mapping в `RoomKsenaxChatRepository`;
5. database migration и schema version;
6. UI mapping только при необходимости отображения.

### Новое поле сообщения

Проверить:

1. `KsenaxStoredMessage`;
2. `KsenaxMessageEntity`;
3. оба направления mapping;
4. migration;
5. нужно ли поле для всех ролей или только для tool/agentic payload.

Не запихивать структурированные tool calls, permission decisions и execution
status в `text`, если по ним нужны запросы или отдельное отображение. Сначала
описать domain-модель и жизненный цикл.

### Новый запрос

Порядок:

1. сформулировать use case верхнего слоя;
2. решить, нужен ли новый repository-метод;
3. добавить минимальный DAO query;
4. вернуть domain-модель через repository;
5. добавить индекс только при обоснованной выборке;
6. проверить generated schema, если схема изменилась.

## Ownership базы

`KsenaxChatDatabase.create(context)` создаёт экземпляр, но не реализует
singleton.

Целевой владелец:

```text
Application / DI application scope
    -> one KsenaxChatDatabase
    -> one RoomKsenaxChatRepository
    -> ViewModel factory / DI
```

Не создавать базу:

- в composable;
- на каждое сообщение;
- на каждый чат;
- в каждом repository-методе;
- в ViewModel без application-scoped composition.

Использовать `applicationContext`. Базу обычно держать весь срок жизни процесса.

## Scope fence для типовых задач

Если пользователь просит изменить только persistence:

- работать внутри `storage/chat`;
- внешние ViewModel/UI только читать;
- сообщить, что интеграция осталась незавершённой.

Если пользователь просит подключить Room к экрану:

- разрешён более широкий анализ application graph, ViewModel и UI models;
- сначала показать будущий поток данных;
- не делать Room entity частью UI state;
- заменить in-memory source только после определения mapping и lifecycle.

Если пользователь просит UI-функцию без нового постоянного поля:

- не менять Room «для симметрии»;
- оставить изменение в presentation.

Если пользователь просит объяснение:

- сначала ответить по этому файлу;
- открывать учебную заметку разделами;
- не сканировать весь UI и communication без вопроса об интеграции.

## Документация

- KDoc писать по-русски.
- Для новых публичных классов этого контура использовать:

```kotlin
@since 0.2
@author Stephan Kolesnikov
```

- Для публичных методов добавлять `@since 0.2`.
- Описывать ответственность и важный инвариант, а не пересказывать имя класса.
- Обновлять `ROOM-CHAT-STORAGE-GUIDE.md`, если изменилась архитектура, схема,
  data flow или порядок интеграции.
- Обновлять этот `AGENTS.md`, если изменились границы слоёв или активный маршрут.

## Проверка

После Kotlin, Room annotation, DAO или mapping-правок запускать из корня:

```powershell
.\gradlew.bat compileDebugKotlin
```

Проверять:

- KSP завершился без Room errors;
- SQL ссылается на существующие таблицы и колонки;
- schema JSON обновился при изменении схемы;
- обе стороны mapping содержат новые поля;
- transaction охватывает весь составной сценарий;
- верхние слои не импортируют `storage.chat.data` или `androidx.room`.

После правки только Markdown достаточно проверить структуру файла, ссылки на
актуальные классы и отсутствие ложного заявления о подключённом Room UI.

## Стоп-сигналы

Остановиться и пересобрать план, если правка требует:

- передать DAO или entity во ViewModel;
- вызвать database из composable;
- создать Room-базу на каждый чат;
- изменить entity без migration;
- удалить schema JSON;
- использовать destructive migration для пользовательской истории;
- хранить UI-only state в таблице;
- сделать UI-модель аргументом DAO;
- распределить одну storage-транзакцию между несколькими слоями;
- возвращать удалённый in-memory mock вместо Room-backed chat ViewModel.
