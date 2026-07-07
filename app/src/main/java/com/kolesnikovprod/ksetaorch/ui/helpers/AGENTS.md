# AGENTS.md — `ui/helpers`

## Назначение папки

Файлы в `ui/helpers` — небольшие помощники для `ViewModel` и UI-слоя. Они
выносят из `ViewModel` повторяющееся сопоставление install-target-ов, чистые
преобразования UI-state, описание отложенных пользовательских действий и
UI-обвязку Android permissions.

На текущем этапе эти helpers хорошо выполняют свою роль. Не следует
переписывать, укрупнять, переносить или превращать их в дополнительный
архитектурный слой без конкретной проблемы и отдельной задачи. Перед любым
изменением нужно сначала прочитать вызывающую `ViewModel`, соответствующий
UI-state и сам helper. Изменять только минимально необходимый объём.

Текущие роли:

- `KsenaxInstallCoordinatorSelector` хранит соответствие между
  `KsenaxInstallOverlayTarget` и нужным
  `KsenaxModelInstallCoordinator`, чтобы `ViewModel` не держала прямой
  `when`-маппинг Gemma/FunctionGemma/Vosk. Он не запускает скачивание сам.
- `KsenaxInstallUiStateReducer` читает и заменяет install snapshot для
  выбранной цели и выполняет чистые переходы overlay-состояния. Он не
  скачивает, не отменяет загрузку, не валидирует артефакты и не работает с
  файловой системой.
- `KsenaxPendingInstallAction` типобезопасно хранит действие, которое нужно
  продолжить после успешной установки: отправить сохранённое сообщение или
  выбрать модель транскрипции.
- `permissions/PermissionUtils.kt` содержит UI-помощники для launcher-ов
  Android permissions/SAF и преобразование выбранного URI в display-only
  путь. Display path не является гарантированным файловым путём и не заменяет
  исходный `Uri`.

## Главная граница: `download -> ViewModel`

`ViewModel` не управляет загрузкой напрямую. Она управляет состоянием экрана и
просит `download`-контур выполнить install-сценарий.

Полный поток ответственности:

```text
ViewModel / Compose binding
    -> ui/helpers
    -> KsenaxModelInstallCoordinator
        -> KsenaxModelInstallUseCase
            -> target-specific install use case
                -> KsenaxDownloadGateway
                    -> AndroidModelDownloadBackend
                        -> DownloadManager
```

`ViewModel` и helpers работают с верхним install API. Gateway, backend,
Android download mechanics, checksum, архивы и файловая хирургия остаются
внутри `download`.

Короткая формула:

```text
ViewModel видит install lifecycle.
ui/helpers упрощают presentation-логику ViewModel.
download скрывает Android download mechanics.
```

Если `ViewModel` или helper захотели тип из нижней части контура, сначала нужно
добавить безопасную операцию на уровне use case или coordinator-а, а не
протаскивать нижний тип наверх.

## Разрешённая витрина `download`

`ViewModel` и относящиеся к ней helpers могут знать верхний install-слой:

```kotlin
import com.kolesnikovprod.ksetaorch.download.KsenaxModelInstallCoordinator
import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.CoordinatorFileCheckState
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallSnapshot
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.KsenaxInstallTargetPurpose
import com.kolesnikovprod.ksetaorch.download.domain.usecases.Gemma4E2BInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.usecases.VoskRuSmallInstallUseCase
```

Ожидаемая публичная поверхность находится в следующих частях download-контура:

```text
download/KsenaxModelInstallCoordinator.kt
download/contracts/KsenaxModelInstallUseCase.kt
download/domain/KsenaxInstallSnapshot.kt
download/domain/KsenaxInstallTarget.kt
download/domain/usecases/Gemma4E2BInstallUseCase.kt
download/domain/usecases/VoskRuSmallInstallUseCase.kt
```

Compose-экран может дополнительно использовать:

```kotlin
import com.kolesnikovprod.ksetaorch.download.compose.BindInstallState
```

`BindInstallState` не является бизнес-логикой и не рисует UI. Это
lifecycle-мост, который через `LaunchedEffect` запускает
`KsenaxModelInstallCoordinator.observeInstallState(...)`.

## Запрещённая зона

`ViewModel` и helpers не должны импортировать, создавать или напрямую вызывать:

```kotlin
KsenaxInstallDownloadIdStore
KsenaxDownloadGateway
KsenaxDownloadEnqueuer
Gemma4E2BDownloadGatewayImpl
VoskRuSmallDownloadGatewayImpl
AndroidModelDownloadBackend
DownloadTaskSnapshot
KsenaxDownloadState
NO_DOWNLOAD_ID
android.app.DownloadManager
android.database.Cursor
java.io.File
java.util.zip.ZipInputStream
```

Внутри `download` также должны оставаться:

```text
DownloadManager.Request
DownloadManager.Query
raw model URLs
archive file names
SHA256
zip extraction
zip-slip defense
raw File deletion
Vosk zip root directory
Vosk directory structure checks
.litertlm validation
downloadId persistence and interpretation
```

`ViewModel` и helpers не должны:

- создавать `DownloadManager.Request` или `DownloadManager.Query`;
- читать Android `Cursor`;
- самостоятельно polling-ить download task;
- сравнивать `downloadId` с `NO_DOWNLOAD_ID`;
- считать SHA256;
- распаковывать Vosk zip;
- искать root directory внутри архива;
- проверять структуру Vosk-модели;
- решать, валиден ли `.litertlm`;
- удалять файлы и директории через `File`;
- создавать gateway или backend вручную;
- использовать `DownloadTaskSnapshot` напрямую;
- знать URL модели или имя скачиваемого архива.

Если один из этих пунктов понадобился presentation-слою, это сигнал расширить
верхний install-контракт.

## Контракт coordinator-а

`KsenaxModelInstallCoordinator` превращает жизненный цикл установки в
безопасное состояние экрана. Его поверхность для `ViewModel`:

```kotlin
fun initialSnapshot(): KsenaxInstallSnapshot

fun startDownload(
    currentSnapshot: KsenaxInstallSnapshot,
): KsenaxInstallSnapshot

fun cancelDownload(
    currentSnapshot: KsenaxInstallSnapshot,
): KsenaxInstallSnapshot

suspend fun observeInstallState(
    initialSnapshot: KsenaxInstallSnapshot,
    onSnapshotChanged: (KsenaxInstallSnapshot) -> Unit,
)
```

`ViewModel` может держать coordinator как поле, вызывать `startDownload` и
`cancelDownload` по пользовательскому действию, принимать новые snapshots и
маппить их в UI-state. Наблюдение за download task выполняет coordinator через
use case.

Для каждой устанавливаемой модели текущий MVP без DI может держать пару:

```kotlin
private val gemmaInstallUseCase = Gemma4E2BInstallUseCase(appContext)
val gemmaInstallCoordinator =
    KsenaxModelInstallCoordinator(gemmaInstallUseCase)

private val voskInstallUseCase = VoskRuSmallInstallUseCase(appContext)
val voskInstallCoordinator =
    KsenaxModelInstallCoordinator(voskInstallUseCase)
```

И начальное состояние:

```kotlin
var gemmaInstallSnapshot = gemmaInstallCoordinator.initialSnapshot()
    private set

var voskInstallSnapshot = voskInstallCoordinator.initialSnapshot()
    private set
```

Если позже появится DI или application graph, `ViewModel` может получать
готовые coordinator/use case снаружи. Граница контракта от этого не меняется.

Кнопка установки обращается к coordinator, а не к use case напрямую:

```kotlin
fun onGemmaInstallClick() {
    val nextSnapshot =
        if (gemmaInstallSnapshot.isDownloading) {
            gemmaInstallCoordinator.cancelDownload(gemmaInstallSnapshot)
        } else {
            gemmaInstallCoordinator.startDownload(gemmaInstallSnapshot)
        }

    onGemmaInstallSnapshotChanged(nextSnapshot)
}
```

Для Vosk действует тот же сценарий:

```kotlin
fun onVoskInstallClick() {
    val nextSnapshot =
        if (voskInstallSnapshot.isDownloading) {
            voskInstallCoordinator.cancelDownload(voskInstallSnapshot)
        } else {
            voskInstallCoordinator.startDownload(voskInstallSnapshot)
        }

    onVoskInstallSnapshotChanged(nextSnapshot)
}
```

Допустим и прямой присваивающий вариант внутри `ViewModel`:

```kotlin
gemmaInstallSnapshot = nextSnapshot
```

## `KsenaxInstallSnapshot` — UI-язык install-контура

Главный DTO между `download` и `ViewModel`:

```kotlin
val currentDownloadId: Long
val isInstalled: Boolean
val isDownloading: Boolean
val isInterrupted: Boolean
val isCancelled: Boolean
val isValidating: Boolean
val downloadProgress: Float
val hasCandidate: CoordinatorFileCheckState
val isValidInstallation: CoordinatorFileCheckState
```

Смысл полей:

- `isInstalled` — runtime artifact прошёл install-проверку;
- `isDownloading` — coordinator наблюдает активную загрузку;
- `isInterrupted` — загрузка пропала, упала или кандидат оказался невалидным;
- `isCancelled` — пользователь отменил загрузку;
- `isValidating` — download-контур проверяет локальный артефакт;
- `downloadProgress` — значение `0f..1f`, проценты вычисляет UI;
- `hasCandidate` — быстрая проверка наличия кандидата;
- `isValidInstallation` — глубокая проверка готового runtime artifact;
- `currentDownloadId` — техническое значение внутри snapshot: `ViewModel`
  хранит его, но не интерпретирует самостоятельно.

Эти поля можно переводить в `KsenaxMainUiState`/`HomeUiState`, состояние
кнопок, progress bar, overlay, текст статуса и доступность действий. Composable
не должен выводить смысл состояния из `currentDownloadId`.

Минимальная мапа в presentation state:

```kotlin
uiState = uiState.copy(
    isDownloadRemembered = snapshot.isInstalled,
    isDownloading = snapshot.isDownloading,
    isDownloadInterrupted = snapshot.isInterrupted,
    isDownloadCancelled = snapshot.isCancelled,
    isModelValidating = snapshot.isValidating,
    downloadProgress = snapshot.downloadProgress,
)
```

Если экран показывает этапы проверки, добавить presentation-поля для:

```kotlin
snapshot.hasCandidate
snapshot.isValidInstallation
```

Сам UI не проверяет файлы и не вычисляет причины невалидности.

## `CoordinatorFileCheckState`

Состояния проверки:

```kotlin
CoordinatorFileCheckState.SUCCESS
CoordinatorFileCheckState.NON_CONFIRMED
CoordinatorFileCheckState.LOADING
CoordinatorFileCheckState.FAILURE
```

`NON_CONFIRMED` не означает ошибку. Это состояние до подтверждения.

Допустимое presentation-отображение:

```text
LOADING -> spinner
SUCCESS -> check
FAILURE -> error
NON_CONFIRMED -> нейтральное состояние
```

## Runtime-пути: контролируемое исключение

Единственное нормальное исключение из правила «работать через coordinator» —
runtime-пути после успешной установки. Их можно получать из конкретного
install use case, только когда snapshot сообщает, что установка валидна.

Gemma:

```kotlin
gemmaInstallUseCase.getInstalledPath()
gemmaInstallUseCase.getGemma4E2BModelPath()
gemmaInstallUseCase.getGemma4E2BCacheDirPath()
gemmaInstallUseCase.getGemma4E2BSavedVoicesDirPath()
```

`getInstalledPath()` и `getGemma4E2BModelPath()` возвращают путь к `.litertlm`
файлу. Cache dir нужен LiteRT-LM runtime, saved voices dir — voice-контуру.

Допустима безопасная use-case операция:

```kotlin
gemmaInstallUseCase.clearGemma4E2BSavedVoices()
```

При этом `ViewModel` не должна сама удалять эту директорию через `File`.

Vosk:

```kotlin
voskInstallUseCase.getInstalledPath()
```

Для Vosk это путь к финальной директории модели, сейчас:

```text
models/vosk
```

`ViewModel` не должна знать имя zip-файла, URL, root directory внутри архива
или правила структурной проверки Vosk.

## Target-aware UI

Несколько установок нужно различать через `KsenaxInstallTarget`, а не через
имена файлов:

```kotlin
KsenaxInstallTarget.GEMMA_4_E2B
KsenaxInstallTarget.VOSK_RU_SMALL
```

Target предоставляет:

```kotlin
id
displayName
storageDirectoryName
purpose
```

Текущие purpose:

```kotlin
KsenaxInstallTargetPurpose.LOCAL_REASONER
KsenaxInstallTargetPurpose.SPEECH_TO_TEXT
```

Gemma отвечает за локальный reasoner/runtime агента, Vosk — за offline
speech-to-text. Это разные install targets с единым install lifecycle.

Для двух целей допустимо:

```text
gemmaInstallUseCase + gemmaInstallCoordinator + gemmaInstallSnapshot
voskInstallUseCase  + voskInstallCoordinator  + voskInstallSnapshot
```

При дальнейшем расширении предпочтителен target-aware state:

```kotlin
data class InstallPresentationState(
    val target: KsenaxInstallTarget,
    val snapshot: KsenaxInstallSnapshot,
)
```

или:

```kotlin
Map<KsenaxInstallTarget, KsenaxInstallSnapshot>
```

Это не даёт UI распухнуть от `isGemmaDownloading`,
`isVoskDownloading`, `isAnotherModelDownloading`.

Текущие helpers уже делают локальный шаг в эту сторону:
`KsenaxInstallCoordinatorSelector` выбирает coordinator по UI-target, а
`KsenaxInstallUiStateReducer` выбирает и обновляет соответствующий snapshot.
Не дублировать эти `when`-маппинги обратно во `ViewModel`.

## Правила для изменений в `ui/helpers`

- Считать helpers частью presentation-границы и помощниками `ViewModel`, а не
  местом для download-бизнес-логики.
- Сохранять текущую узкую ответственность каждого файла.
- Предпочитать чистые функции и типобезопасные модели.
- Не переносить в helpers Android download mechanics, filesystem operations,
  checksum, распаковку или валидацию runtime artifacts.
- Не заставлять Composable интерпретировать внутренние download-типы.
- Не дублировать существующий selector/reducer mapping во `ViewModel`.
- Не рефакторить работающие helpers «на будущее» без реального сценария.
- При добавлении нового install target расширять selector/reducer и безопасную
  верхнюю витрину согласованно.
- После изменения проверять вызывающую `ViewModel`, UI-state и все ветки
  соответствующего target.

Итоговый запрос `ViewModel` к download-контуру:

```text
start
cancel
observe
give me safe state
give me runtime path after successful install
```

То, что download-контур не отдаёт `ViewModel`:

```text
backend
gateway
filesystem surgery
checksum
archive internals
Android cursor
```
