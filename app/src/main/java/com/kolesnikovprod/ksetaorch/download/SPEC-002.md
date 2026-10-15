# SPEC-002: implementation record

OpenKsenax 0.4. @since 0.4. No commit or push.

## /spec: observed architecture

Download-linked Compose -> MainViewModel commands -> KsenaxModelInstallCoordinator
-> KsenaxModelInstallUseCase -> artifact gateway -> Android DownloadManager.
The ViewModel owns polling, not HTTP. DownloadManager owns HTTP, redirects,
Range/ETag retry, network policy, byte counters and transfer notifications.
Completion receiver -> unique WorkManager finalization -> shared finalizer.
Runtime consumes stable installed paths. No model runtime changes are required.

The report that backgrounding stops HTTP is not reproduced by source inspection.
The demonstrated faults are unsafe installation and incomplete reconciliation.

| Location (relative to this directory) | Evidence/problem | Impact | Priority | Decision |
| --- | --- | --- | --- | --- |
| platform/SingleFileModelDownloadGateway.kt | enqueue deletes installed file/cache and downloads to runtime path | partial runtime, old install loss | BLOCKER | FIX: separate candidates and atomic promotion |
| domain/usecases/KsenaxVoskRuSmallInstallUseCase.kt | deletes runtime before enqueue; temp file presence accepted | loss/partial extraction accepted | BLOCKER | FIX: verified archive, fresh extraction, guarded promotion |
| platform/ModelInstallFinalizationWorker.kt | bounded retry clears PENDING/RUNNING/PAUSED | live transfer orphaned | BLOCKER | FIX: distinguish active transfer from missing/failed task |
| domain/DownloadIdPreferencesStore.kt | apply, id only, no terminal result | process recovery loses failure/cancel reason | ISSUE | FIX: durable task/terminal record and orphan reconciliation |
| domain/InstallCandidateFinalizer.kt | finalizer mutex does not serialize cancel/start filesystem effects | stale finalizer can mutate replacement files | BLOCKER | FIX: per-task candidates and atomic ownership-guarded commit |
| KsenaxModelInstallCoordinator.kt | synchronous query in UI coroutine, stale booleans, terminal null returns | main-thread IO/stale progress | ISSUE | FIX: IO query and coherent recovered snapshots |
| platform/AndroidModelDownloadBackend.kt | notification title is filename; no free-space preflight | poor observability/late ENOSPC | ISSUE | FIX within existing system adapter |
| Download UI | existing state/progress mapper and one polling job | observation independent of system transfer | MINOR | KEEP visual design; inspect read-only |

## /plan

1. Task persistence and reconciliation: contracts, store, use cases; preserve
   API callers, reuse active tasks, retain terminal reason. Evidence: focused
   state/store tests and compileDebugKotlin.
2. Artifact boundary: gateway/backend, pure file installer and ZIP extraction;
   use per-task candidates, verify before rename, preserve installed models.
   Evidence: temporary-file tests for hash/size, cancellation, traversal,
   failed promotion and partial extraction.
3. Background/progress: receiver/scheduler/Worker/coordinator; reconcile using
   system task and files, never delete running task on retry exhaustion.
   Evidence: deterministic fake use-case tests, device checks when possible.
4. Final verification: focused tests -> full unit tests, lint, APK build,
   independent diff review, controlled HF smoke test, acceptance matrix.

## ADR: execution mechanism

Selected: existing DownloadManager for HTTP; WorkManager only for local
verification/installation. These have distinct owners, not competing engines.
Supported versions: app minSdk 28, targetSdk 36. No custom FGS or HTTP client.

DownloadManager provides system progress/indeterminate notification, network
waiting/retry, process-independent transfer and cancellation via remove(id).
Pinned HTTPS URLs remain the original source, never persisted signed redirects.
Custom 200/206/Content-Range parsing is deliberately absent: platform owns it.

UIDT (API 34+) would require a second versioned HTTP engine and notification/job
integration for the same established transfer. No demonstrated need warrants
that migration. A foreground WorkManager downloader or dataSync service adds
foreground permissions, start restrictions/time limits and Android 16 job quota
constraints without improving this storage bug. Keep postprocessing deferrable.

Recovery reconciles saved identity with system rows and verified local files;
cancel aborts the owned task and removes only its staging, never an installed
model. Completed candidates promote only after validation and ownership check.
Force-stop, Doze, scheduler quota and OEM restrictions can delay execution.
No guarantee of uninterrupted throughput or survival of force-stop is made.
DownloadManager exposes no application-controlled pause; no fake pause exists.
System notification appearance/actions/tap routing depend on Android/OEM.

References checked 2026-10-15:
- https://developer.android.com/reference/android/app/DownloadManager
- https://developer.android.com/develop/background-work/background-tasks/uidt
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

## Scope

Production changes stay in download. Download-focused JVM/instrumentation tests
are allowed by the supplied specification. Existing UI is inspected read-only
under the user's narrower contour boundary. Any UI-only catalog/navigation
extension must be reported rather than silently changing another contour.

## /build: completed slices

### 1. Ownership and durable outcomes

Files: contracts/KsenaxModelInstallUseCase.kt, domain/DownloadIdPreferencesStore.kt,
domain/data/KsenaxInstallFailure.kt, domain/data/KsenaxInstallSnapshot.kt,
domain/InstallSnapshots.kt, domain/usecases/SingleFileInstallDelegate.kt.

Submission/adoption and cancellation share a target lock. Commit failure is
reported and memory is rolled back best-effort. Terminal failure/cancellation
survives recreation. A failed replacement preserves a previous verified model.
Recovery scans system tasks on IO, not in synchronous initialSnapshot. That
method only schedules a recovery worker and reads the existing app record.

Evidence: store concurrency, stale ownership, legacy migration, invalid terminal
snapshot, failed commit, local recovery, coordinator cancellation and provider
outage JVM tests. Existing public constructors and original methods retained.

### 2. Staging, verification and atomic publication

Files: platform/AndroidModelDownloadBackend.kt,
platform/SingleFileModelDownloadGateway.kt, platform/VoskRuSmallDownloadGateway.kt,
domain/usecases/KsenaxVoskRuSmallInstallUseCase.kt,
domain/ArtifactFileInstaller.kt, domain/SafeZipExtractor.kt,
domain/FileIntegrityVerifier.kt, domain/InstallSupersededException.kt,
domain/InstallCandidateFinalizer.kt.

Unique per-task partial files live outside runtime model directories. The
backend preflights destination and measured free space. Source URI/destination
are reconciled with the expected artifact. All three trusted size/hash pins
were compared with HF metadata; values agree. Vosk validates the pinned ZIP,
extracts fresh with cancellation/CRC/limits/path checks and guarded directory
publication. Partial temp trees alone are never accepted as installation.
Verification is streamed in 128 KiB buffers. No new HTTP engine was added.

Independent review caught missing first-install runtime parent creation,
stale orphan adoption, legacy completed-row deletion and unconfirmed watchdog
continuation persistence; these were repaired. Active legacy tasks are adopted,
completed legacy rows are not removed just to clear app ownership.

Device testing caught a real Android compatibility error in the initial
FileStore preflight. Removed that probe; ATOMIC_MOVE itself rejects unsupported
cross-filesystem moves. The corrected first-install gateway path and replacement
were subsequently exercised on API 36.

### 3. Background reconciliation and coherent observation

Files: platform/BackgroundModelInstallScheduler.kt,
platform/ModelInstallFinalizationWorker.kt,
platform/ModelDownloadCompletionReceiver.kt, KsenaxModelInstallCoordinator.kt,
domain/DownloadFailureClassifier.kt.

Persisted watchdog starts at submission; per-target recovery is scheduled at
initialSnapshot. Completion receiver is an accelerator, not the sole trigger.
Active/waiting tasks append another check after 30s and wait for its persistence.
Only authoritative FAILED/missing rows enter terminal reconciliation. An
unavailable cursor is an IOException, not a missing task. Exhausted provider
retries preserve identity/files for later broadcast or app recovery.
The system owns HTTP retry/Range and transfer notifications. Titles now show
human model names. UI observation uses IO for provider queries and receives
consistent full snapshots on its own calling context. No new notification
controller, pause button, permissions or foreground service was added.

### 4. Tests and documentation

Added download JVM suites: ArtifactFileInstallerTest, SafeZipExtractorTest,
FileIntegrityVerifierTest, DownloadIdPreferencesStoreTest, InstallLifecycleTest,
DownloadTransferMetricsTest. Added DownloadManagerDeviceTest in the matching
androidTest download package. Updated this report and AGENTS.md.

## /test: measured results, 2026-10-15

- PASS: :app:testDebugUnitTest. Latest XML totals: 324 tests, 0 failures,
  0 errors, 3 skipped. Of these, 77 tests are in the new download package.
  Three symlink tests skip because Windows cannot create the test symlinks.
- PASS: :app:assembleDebug and :app:assembleDebugAndroidTest.
- PASS: git diff --check (line-ending warnings only).
- FAIL outside scope: :app:lintDebug, one PropertyEscape error in
  root local.properties:8; 110 warnings. Download files had no reported errors.
  This file was not edited and the lint rule was not disabled.
- PASS device subset: Honor ELI-NX9, Android API 36. Debug APK was updated with
  adb install -r -t; application data was retained. No installed model target
  was used as test storage.
- PASS network/integrity subset: system DownloadManager downloaded the pinned
  46,236,750-byte Vosk archive into a UUID test directory. Home and am kill were
  issued between submission and verification; pidof confirmed no app process
  before a separate verification invocation. Final SHA-256 matched
  961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4.
  Exact byte count at process termination was not obtainable via run-as, so this
  is not proof of progress at that precise instant or of every kill timing.
- PASS device: actual SingleFileModelDownloadGateway first-install promotion,
  Vosk ZIP extraction/required files, small-file atomic replacement, preservation
  of a completed legacy runtime destination on cancellation.
- PASS notification subset: dumpsys confirmed the exact title of the isolated
  Vosk transfer notification. Notification actions/tap/permission variants were
  not exercised. No signed URLs or other notification contents were logged.
- Test fixture cleanup passed; test DownloadManager rows and UUID directories
  were removed. No full Gemma/FunctionGemma transfer was performed.

Custom fake-HTTP 200/206, Content-Range, ETag and controlled-server integration
tests are NOT APPLICABLE to an app-owned HTTP implementation here: none exists.
Those transport details remain delegated to DownloadManager. Actual range
resume, network switching and signed-URL expiry still require Android tests;
metadata and one successful real transfer do not prove all those conditions.

The first device publication attempt and the first legacy test-fixture setup
failed. Publication was repaired as described above. Legacy fixture notification
visibility was corrected without adding privileged permissions; final targeted
device invocation reported OK (3 tests), including cleanup.

## /review

| Axis | Verdict | Evidence/remaining boundary |
| --- | --- | --- |
| Correctness | ISSUE | JVM tests and device storage/integrity subset pass; complete production UI/Worker end-to-end flow remains to be exercised |
| Architecture | PASS | DownloadManager owns transfer, one shared finalizer owns installation; Worker has a distinct persistence/recovery purpose |
| Robustness | ISSUE | Stale ownership/provider outages/corrupt ZIP covered; Doze, network changes, expiry and OEM restrictions untested |
| Maintainability | PASS | Generic coordinator, existing gateway pattern, pure tested file helpers, no new dependencies or HTTP/service framework |
| Scope compliance | PASS | Only production download, matching test packages and module documentation changed; no inference/chat/UI redesign/commit/push |

### Acceptance matrix

VERIFIED is scoped to the stated evidence, not a claim covering every Android
version or OEM. NOT TESTED means no complete acceptance proof was obtained.

| Criterion | Status | Evidence |
| --- | --- | --- |
| AC-1 screen independence | VERIFIED | System owns transfer; observer cancellation test leaves task untouched |
| AC-2 backgrounding | VERIFIED | Controlled API 36 Home/am kill/relaunch sequence completed with matching bytes/hash; timing limit above |
| AC-3 platform compliance | VERIFIED | DownloadManager + deferrable local WorkManager finalization, no custom FGS |
| AC-4 notification progress | NOT TESTED | System integration/title verified; progress, cancel/tap and permission matrix incomplete |
| AC-5 recovery | NOT TESTED | Store/orphan/ownership JVM tests pass; full network drop/resume/reboot matrix incomplete |
| AC-6 cancellation | VERIFIED | Ownership/stale-cancel tests and device legacy-file preservation; full UI cancellation during extraction still manual |
| AC-7 duplicate prevention | VERIFIED | Concurrent shared-store tests, reuse/adoption under one lock |
| AC-8 safe installation | VERIFIED | Hash/CRC/path tests and real gateway atomic publication into isolated storage |
| AC-9 integrity | VERIFIED | All pins matched HF metadata, full Vosk checksum/extraction verified on device |
| AC-10 catalog extension | NOT TESTED | Transfer/finalizer remain generic; existing UI catalog/factory are explicit extension points, no extra model entry/UI migration attempted |
| AC-11 UI synchronization | NOT TESTED | Producer/fake-coordinator tests and unchanged caller API; full Compose flow not exercised |
| AC-12 maintainability | VERIFIED | Bounded responsibilities and independent source review, reviewed defects repaired |
| AC-13 performance | VERIFIED | Streaming/128 KiB buffers, short-window sampling, no per-buffer persistence or custom chunk downloader |
| AC-14 unrelated behavior | VERIFIED | Changed paths restricted to download production/tests/docs |
| AC-15 verification honesty | VERIFIED | Failures, platform delegation and untested conditions recorded separately |

## /ship

SPEC-002 CODE COMPLETE — DEVICE VALIDATION PENDING

This is not full SPEC-002 verification or a green repository-wide pre-commit
gate: the unrelated local.properties lint error remains, and NOT TESTED items
above remain open. The latest user instruction stops further build/install
runs; all already-started checks completed before handoff.

### User validation checklist

1. Use the latest source to build/install yourself. No installed models need
   deletion: use an actually absent model or a separate test environment.
2. Start from the existing UI, close/minimize Download, press Home, swipe away,
   reopen. Confirm the same task and byte counters, then verified installation.
3. Interrupt/re-enable the network and test allowed/unmetered waiting. Waiting
   should retain bytes/id; check actual resume behavior on this device.
4. Cancel during transfer and during verification/extraction; retry quickly.
   Confirm no duplicate task, stale success or deletion of a previous model.
5. Test screen-off/Doze, notification progress/actions/tap and denied notification
   permission on relevant Android versions. Record force-stop separately.
6. Check a fresh first installation and startup with installed/missing/corrupt
   files. For Vosk, confirm runtime files inside models/vosk, not just a ZIP.

### Remaining limitations

- UI-specific static catalog, paused-stall wording and notification deep-link
  changes were not made under the user's download/storage contour boundary.
- System downloader determines Range retries, redirect limits/timeouts,
  temporary-signed-URL behavior and notification appearance. A permanent
  failed system task requires a deliberate retry with a fresh original URL.
- Legacy Vosk runtime directories are structurally validated; no invented hash
  of an already-extracted legacy tree is claimed. New archives are pinned/hash
  verified before extraction.
- Same-process locks protect app instances; this contour does not advertise
  cross-process writers. Installed runtime files are never rewritten in place.
- Synchronous legacy start/cancel APIs retain their caller compatibility and
  short provider/commit operations; heavy transfer/hash/unzip/recovery queries
  run outside Main. A fully suspend command API would require caller migration.

### Modified files

Production paths relative to this directory:

- AGENTS.md; SPEC-002.md
- KsenaxModelInstallCoordinator.kt
- contracts/KsenaxDownloadGateway.kt; contracts/KsenaxModelInstallUseCase.kt
- domain/DownloadIdPreferencesStore.kt; domain/FileIntegrityVerifier.kt
- domain/InstallCandidateFinalizer.kt; domain/ArtifactFileInstaller.kt
- domain/SafeZipExtractor.kt; domain/DownloadFailureClassifier.kt
- domain/InstallSnapshots.kt; domain/InstallSupersededException.kt
- domain/data/KsenaxInstallSnapshot.kt; domain/data/KsenaxInstallFailure.kt
- domain/usecases/SingleFileInstallDelegate.kt
- domain/usecases/KsenaxVoskRuSmallInstallUseCase.kt
- platform/AndroidModelDownloadBackend.kt; platform/SingleFileModelDownloadGateway.kt
- platform/VoskRuSmallDownloadGateway.kt; platform/BackgroundModelInstallScheduler.kt
- platform/ModelDownloadCompletionReceiver.kt; platform/ModelInstallFinalizationWorker.kt

Tests: app/src/test/java/com/kolesnikovprod/ksetaorch/download/{ArtifactFileInstallerTest,
SafeZipExtractorTest,FileIntegrityVerifierTest,DownloadIdPreferencesStoreTest,
InstallLifecycleTest,DownloadTransferMetricsTest}.kt and
app/src/androidTest/java/com/kolesnikovprod/ksetaorch/download/DownloadManagerDeviceTest.kt.
