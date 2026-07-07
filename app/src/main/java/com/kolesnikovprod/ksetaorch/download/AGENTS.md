# AGENTS.md for `com.kolesnikovprod.ksetaorch.download`

## Scope

This directory owns installation of local model artifacts: enqueue, progress,
resume, cancel, local paths, preparation, validation, and deletion.

Default write scope is only:

```text
app/src/main/java/com/kolesnikovprod/ksetaorch/download
```

Outside this directory, only mechanical import/type-name migration is allowed
unless the user explicitly widens scope. Do not alter UI/runtime/voice behavior
from this module task.

Current module version: `0.3`.

Version rule: scan local KDoc and Markdown before documenting. Use the highest
observed module version. A new public capability may bump the minor version;
formatting and import cleanup may not.

## Naming Boundary

The `Ksenax` prefix marks types intentionally published to other contours.
Every public or plausibly reusable cross-contour type must start with `Ksenax`.

Internal implementation types use direct natural names without the prefix:

```text
AndroidModelDownloadBackend
DownloadIdStore
SingleFileInstallDelegate
SingleFileModelDownloadGateway
ModelArtifactSpec
Gemma4E2BDownloadGateway
FunctionGemmaDownloadGateway
VoskRuSmallDownloadGateway
```

Do not add `Impl` to private/internal concrete classes when the concrete role is
already clear. Do not expose an internal helper only to avoid a small delegate.

Current public surface:

```text
KsenaxModelInstallCoordinator
KsenaxModelInstallUseCase
KsenaxDownloadGateway
KsenaxDownloadEnqueuer
KsenaxDownloadPolicy
KsenaxDownloadTaskSnapshot
KsenaxDownloadState
KsenaxInstallSnapshot
KsenaxInstallCheckState
KsenaxInstallTarget
KsenaxInstallTargetPurpose
KsenaxModelFilePresenceChecker
KsenaxGemma4E2BInstallUseCase
KsenaxFunctionGemmaInstallUseCase
KsenaxVoskRuSmallInstallUseCase
```

## Architecture

```text
ViewModel / app controller
    -> KsenaxModelInstallCoordinator
    -> KsenaxModelInstallUseCase
    -> target-specific public use case
    -> KsenaxDownloadGateway
    -> internal target gateway
    -> AndroidModelDownloadBackend
    -> Android DownloadManager + app-specific external files
```

Layers:

- `contracts`: cross-contour install/download promises.
- `domain`: public state, targets, policy, radar, persistence, and use cases.
- `platform`: Android filesystem/DownloadManager mechanics and pinned artifact
  metadata.

`KsenaxModelInstallCoordinator` is generic. It must not branch on Gemma,
FunctionGemma, or Vosk. Target-specific differences belong below
`KsenaxModelInstallUseCase`.

`SingleFileInstallDelegate` and `SingleFileModelDownloadGateway` are shared only
by ready-to-run single-file artifacts. Vosk stays separate because ZIP
preparation is real domain behavior, not single-file boilerplate.

## Download Lifecycle

The upper caller works only with `KsenaxModelInstallCoordinator` and
`KsenaxInstallSnapshot`. Normal UI/API usage is:

```text
initialSnapshot()
startDownload(currentSnapshot, policy)
observeInstallState(startedSnapshot, onSnapshotChanged)
cancelDownload(currentSnapshot)  # only on explicit cancellation
```

The UI must not call a gateway, `DownloadManager`, checksum code, unzip code, or
`prepareInstallCandidate()` directly.

### 1. Construction

The app creates one target-specific public use case and passes it to a generic
coordinator:

```text
Ksenax...InstallUseCase
    -> internal target gateway
    -> AndroidModelDownloadBackend
```

The use case owns install semantics and saved `downloadId`. The gateway owns
artifact constants and file navigation. The backend owns Android APIs.

### 2. Enqueue

`startDownload(...)` forwards `KsenaxDownloadPolicy` to the use case. The use
case configures metered/roaming flags and asks its gateway to enqueue.

For ready single-file models, `SingleFileModelDownloadGateway` removes the old
model file and runtime cache before creating a new request. Vosk removes its
archive, final model directory, and temporary extraction directory before a new
install.

`AndroidModelDownloadBackend` creates `DownloadManager.Request`, writes into:

```text
<external-files>/models/<target-directory>/<artifact-file>
```

The returned system ID is immediately persisted by `DownloadIdStore`. It means
only "Android accepted this task", never "model installed".

### 3. Observation And Resume

`KsenaxModelInstallCoordinator.observeInstallState(...)` chooses one route:

- saved ID exists: poll the Android task every 500 ms;
- no saved ID: inspect local artifacts and recover/validate an existing install.

This is why a process restart does not automatically lose an active download.
`initialSnapshot()` restores the target-specific ID from preferences, and the
caller starts observation again.

`AndroidModelDownloadBackend` maps platform cursor data into
`KsenaxDownloadTaskSnapshot`:

```text
PENDING / RUNNING / PAUSED / UNKNOWN -> keep observing
SUCCESSFUL                           -> prepare and validate
FAILED                               -> clear ID and partial artifacts
missing task                         -> mark interrupted and clear artifacts
```

Progress is transfer progress only. It must not be reused as unzip or integrity
progress.

### 4. Preparation

After download success, coordinator emits:

```text
isDownloading = false
preparationState = LOADING
```

Then it calls `prepareInstallCandidate()` through the use-case contract.

For Gemma and FunctionGemma, the artifact is already a runtime `.litertlm`
file. `SingleFileInstallDelegate` only confirms that the candidate exists.

For Vosk, preparation is a real installation stage:

1. confirm a complete readable ZIP;
2. extract into `models/.vosk-installing`;
3. reject zip-slip paths outside the temporary directory;
4. validate required Vosk files;
5. replace `models/vosk` with the prepared archive root;
6. remove temporary extraction data.

Vosk gateway may convert a complete readable ZIP into a successful download
snapshot when vendor `DownloadManager` status remains stale. This fallback must
inspect ZIP structure, not just file existence or UI percentage.

### 5. Validation And Final State

Coordinator always validates after preparation:

- Gemma / FunctionGemma: exact byte size and SHA256;
- Vosk: required runtime directory structure.

Only a successful validation produces:

```text
isInstalled = true
isValidInstallation = SUCCESS
preparationState = SUCCESS
```

The saved download ID is then removed, while the validated runtime artifact
stays on disk. Failed preparation or validation marks the snapshot interrupted
and deletes target artifacts so a corrupt candidate cannot survive as an
installed model.

### 6. Cancellation

Explicit cancellation removes the Android task by its ID, clears the saved ID,
deletes partial target artifacts, and emits `isCancelled = true`. Cancellation
is different from interruption: it represents a user decision, not a failed
network or validation stage.

### 7. Runtime Handoff

After `isInstalled == true`, another contour may obtain the runtime path only
through the public use case:

```text
getInstalledPath()
```

Model-specific convenience paths, such as Gemma runtime cache or saved voices,
also stay on the public use case. Runtime code must not rebuild `models/...`
paths or import an internal gateway.

## Active Targets

Gemma 4 E2B:

```text
target: GEMMA_4_E2B
purpose: LOCAL_REASONER
runtime: models/gemma-4-e2b/openksenax_gemma_4_e2b_it.litertlm
validation: exact size + SHA256
```

FunctionGemma 270M Mobile Actions:

```text
target: FUNCTION_GEMMA_270M
purpose: ACTION_ROUTER
runtime: models/functiongemma-270m/openksenax_functiongemma_270m_mobile_actions_q8_ekv1024.litertlm
source revision: 82d0f654a6270c518d16c600edce3136221b3347
size: 284426240
sha256: 92109695f911d1872fa8ae07c1e3ff0ed70f2c3d1690d410ec6db8587c2ab409
validation: exact size + SHA256
```

The selected FunctionGemma repository is public and downloadable by Android
without credentials. Do not silently switch to
`litert-community/functiongemma-270m-ft-mobile-actions`: that repository is
currently gated and a plain DownloadManager request receives an authentication
failure. FunctionGemma is an action router, not a dialogue replacement.

Vosk Russian Small:

```text
target: VOSK_RU_SMALL
purpose: SPEECH_TO_TEXT
download: models/vosk/openksenax_vosk-model-small-ru-0.22.zip
temporary extraction: models/.vosk-installing
runtime: models/vosk/{am,conf,graph,ivector,...}
validation: required directory structure
```

Vosk completion may be inferred from a readable ZIP central directory when
DownloadManager status is stale. A partial ZIP must never trigger preparation.

## Invariants

- A `downloadId` means queued, not installed.
- `NO_DOWNLOAD_ID == -1L` means no active remembered task.
- IDs are persisted only through `DownloadIdStore`.
- Coordinator calls `prepareInstallCandidate()` before final validation.
- `preparationState` reports preparation; `downloadProgress` reports transfer.
- A model is installed only after `hasValidInstallation()` succeeds.
- Single-file models validate exact size and SHA256 off the main thread.
- Archive extraction runs off the main thread and defends against zip-slip.
- Failed/cancelled/invalid tasks clear their saved ID and partial artifacts.
- Successful tasks clear only the saved ID; validated runtime files remain.
- URLs are HTTPS and pinned to immutable revisions.
- Model storage remains app-specific external storage.

## Extension Checklist

For another ready `.litertlm` model:

1. Add `KsenaxInstallTarget` and purpose.
2. Add an internal gateway extending `SingleFileModelDownloadGateway`.
3. Pin immutable revision, exact byte size, and SHA256.
4. Add a public `Ksenax...InstallUseCase` using
   `SingleFileInstallDelegate`.
5. Keep it detached from UI until the user requests wiring.

For an archive or multi-file model, do not force it into the single-file
helpers. Add target-specific preparation and validation.

## Design Reality

This is a real layered install subsystem, not demo-only downloader code. Its
boundaries support multiple model formats, process restart, target-specific
validation, cancellation, cleanup, and UI-safe state without exposing Android
cursor mechanics upward.

It is not finished production infrastructure yet:

- coordinator transitions are still a hand-written polling state machine;
- preparation/validation flow exists in both local-recovery and active-download
  branches and should eventually share one internal finalization function;
- low-level exceptions mostly collapse into Boolean failure, so diagnostics are
  weaker than the state model;
- `KsenaxDownloadGateway` is intentionally broad and should not grow unrelated
  runtime responsibilities;
- `KsenaxModelFilePresenceChecker` is only a fast presence heuristic, never an
  integrity or installation verdict;
- coordinator transitions, persistence recovery, checksum failure, and Vosk
  extraction need focused automated tests before calling the contour
  production-hardened.

Do not add abstraction merely to hide these facts. Improve the concrete weak
point when a real test, fourth artifact type, or diagnostic requirement makes
the change pay for itself.

## Verification

From repository root:

```powershell
.\gradlew.bat compileDebugKotlin
```

Also:

- scan for old renamed symbols;
- verify pinned URLs return `200` without authentication;
- compare remote content length and SHA metadata with constants;
- inspect every public declaration: published names use `Ksenax`, internal names
  do not;
- do not report unrelated dirty worktree files as module changes.
