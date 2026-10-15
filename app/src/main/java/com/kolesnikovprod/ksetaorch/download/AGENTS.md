# Download contour

OpenKsenax 0.4. New public contracts/KDoc use @since 0.4; historical tags remain.

## Scope and routes

Own model transfer, task persistence, reconciliation, cancellation, staging,
verification and installation. Keep runtime/inference/chat/STT semantics out.
Download-specific JVM and instrumentation tests belong in their matching
download test packages. Follow the user's latest scope when inspecting UI.
No commit/push without explicit request.

Read [SPEC-002.md](SPEC-002.md) for architecture decision, audit, evidence and
acceptance limitations. This file is a routing/invariant map, not a full report.

## Ownership

- UI observes KsenaxModelInstallCoordinator / KsenaxInstallSnapshot and issues
  commands. Cancelling observation never cancels a system transfer.
- AndroidModelDownloadBackend delegates HTTP, redirects, Range/ETag resume,
  waiting/retry, metered/roaming policy and notifications to DownloadManager.
- BackgroundModelInstallScheduler submits a per-target recovery worker at
  initialSnapshot and a task-specific watchdog immediately after durable enqueue.
  Completion receiver accelerates it; delivery of that broadcast is not the
  sole path to installation.
- ModelInstallFinalizationWorker queries on IO. Active/waiting tasks append
  another persisted check after 30s, without deleting bytes/identity. Errors
  querying the provider are bounded retries and retain the task. Missing and
  FAILED system rows are distinct from an unavailable cursor.
- InstallCandidateFinalizer is the sole prepare/validate sequence. A target
  mutex serializes UI and Worker finalizers, with ownership checked again before
  promotion/terminal publication.

## Persistence and races

DownloadIdPreferencesStore serializes short state operations across all
instances for a target. getOrCreate/recoverIfMissing discover/adopt orphans
inside that same lock. Gemma legacy id is visible without a special constructor.
Checked commit persists identity/terminal outcome; no byte-progress writes.
The source URI and destination live in the system row; pinned artifact metadata
lives in gateways. Revalidate both on recovery, never trust a display label.

finishIfMatches records installed/failed/cancelled and clears only the matching
id. NO_DOWNLOAD_ID is valid only for local recovery when no task owns the target.
runIfMatches guards the short atomic filesystem publication. Do not hold this
lock while hashing, streaming or extracting: each of those checks coroutine and
task ownership per buffer. Stale callbacks never delete a replacement task.

Do not replace domain failures with signed URLs or credentials. Keep a stable
KsenaxInstallFailure; permanent 4xx/hash/storage errors are not network retries.
Task outcome and local availability differ: a failed/cancelled replacement must
not hide an existing verified model when observation reconciles local state.

## Artifact boundary

- New downloads: models/.downloads-<target>/<artifact>.<uuid>.partial.
- Runtime paths remain compatible with existing consumers.
- Single-file candidates: exact size + SHA-256, then atomic move under ownership
  guard. Create the runtime parent first. Preserve the old file on failure.
- ATOMIC_MOVE is the filesystem capability check. Do not preflight FileStore:
  it is unsupported on some Android providers. No non-atomic copy fallback.
- Vosk: verify pinned archive size/hash, extract fresh into
  models/.vosk-installing-<downloadId>, validate required nonempty files, publish
  using guarded directory renames with rollback backup. Never recover a temp
  tree solely because the expected filenames exist.
- SafeZipExtractor validates central directory, CRC, containment, duplicates,
  entry/expanded-byte limits and cancellation. Storage IO errors propagate.
- Cleanup is task-local. Installed models and saved voices survive failed
  replacement/cancellation. Explicit deleteLocalArtifacts remains destructive.
- Completed legacy DownloadManager rows can point at runtime files; remove(id)
  would delete the file. Never remove such a row merely to clear its app id.
  Active legacy rows are still recovered to prevent duplicate transfers.
- Reject traversal, symlinks and unavailable external storage; never fall back
  to a relative path. FileTreeDeleter does not follow links.

## State and extension

InstallSnapshots builds consistent producer states. KsenaxDownloadTaskSnapshot
rejects invalid progress; unknown totals remain null. Real transferred bytes,
five-sample throughput and ETA are domain values, formatting belongs to UI.
Completion of HTTP is not installed=true. Only deep validation yields installed.

Ksenax prefix marks cross-contour APIs; internal helpers use natural names.
Keep model URLs/size/hash inside pinned gateway specs. Coordinator has no
model-specific branches. Add a single-file gateway/spec and public use case for
a new model; archive semantics require a real preparation implementation.
Factory target selection and the existing UI catalog are explicit extension
points, not a dynamic external catalog. UI catalog changes need user scope.

## Verification

Use focused download JVM tests first, then full app unit tests/build/lint.
Run isolated Android download fixtures only; never remove/re-download a user's
installed model to test transport. FileStore and external-storage behavior must
be tested on Android, not inferred from Windows/JVM.

See SPEC-002.md for measured commands, counts, device results and remaining
limits. Force-stop, OEM battery policies, notification permission/tap routing,
network switching and real HTTP Range behavior need distinct evidence.
