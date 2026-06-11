# Ksenax Orchestration

Ksenax is an experimental local-first Android application for testing an on-device agent workflow with Gemma-family models, LiteRT-LM, and a strict Kotlin-side tool execution layer.

The project is intentionally open-source friendly: the repository contains the Android app source code, Gradle configuration, UI resources, and public project documentation. Large model files, local IDE state, signing keys, generated APKs, and machine-specific configuration are not committed.

## Current Status

This is an intermediate MVP version. The app is being developed as a research-oriented Android shell for a local agent:

- model artifacts are downloaded at runtime instead of being bundled into the APK;
- the app keeps model execution local on the device where possible;
- Android actions are expected to go through explicit Kotlin allowlists and permission checks;
- generated APKs are intended for GitHub Releases, not for the source tree.

## Repository Contents

The public repository is expected to include:

- `app/src/main` - Android app source, manifest, Compose UI, and resources;
- `app/src/test` and `app/src/androidTest` - test sources;
- `gradle/`, `gradlew`, `gradlew.bat` - Gradle wrapper and version catalog;
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` - project build configuration;
- `AGENTS.md` - shared project concept for agent-oriented development;
- `metadocs/` - public technical notes prepared for the repository.

The repository intentionally does not include:

- Android Studio local state;
- local SDK paths;
- Gradle/Kotlin build caches;
- generated APK/AAB files;
- signing keys or release credentials;
- downloaded model weights or runtime model caches;
- private research notes that are not cleaned for publication yet.

## Requirements

To build the project from source, install:

- Android Studio or Android SDK command-line tools;
- an Android SDK version compatible with the project `compileSdk`;
- JDK 21, or let Gradle provision the configured toolchain;
- Git.

Android Studio will normally generate `local.properties` automatically when the project is opened. That file contains a local SDK path and must stay untracked.

## Build From Source

Clone the repository:

```powershell
git clone <repo-url>
cd KsetaAgentic-Application-Project
```

Build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

On Linux or macOS:

```bash
./gradlew :app:assembleDebug
```

The generated debug APK will appear under `app/build/outputs/`. The whole `app/build/` directory is ignored by Git.

## Install On A Connected Device

Enable Developer Options and USB debugging on the Android device, connect it with a data-capable USB cable, then run:

```powershell
.\gradlew.bat :app:installDebug
```

You can also open the project in Android Studio and press Run after selecting the connected device.

## Release Builds

Release APKs should be published through GitHub Releases, not committed to the repository.

For local release experiments:

```powershell
.\gradlew.bat :app:assembleRelease
```

A real public release should be signed with a release keystore. Keep all signing files and passwords outside the repository. Use GitHub Actions secrets or another secure local setup if release automation is added later.

## Model Files

Gemma/LiteRT-LM model files are large and are not part of this repository. The app is designed around runtime model delivery: the APK stays small, while model artifacts are downloaded and verified separately on the device.

Do not commit files such as:

- `.litertlm`
- `.gguf`
- `.onnx`
- `.safetensors`
- `.tflite`

Model licenses and usage terms are separate from the application source license. Anyone forking the project should review the terms of the model provider before redistributing model artifacts.

## Git Ignore Policy

The root `.gitignore` is configured to keep the source tree clean for open-source publication:

- build outputs are ignored through `**/build/`;
- local Android Studio and Gradle state are ignored;
- `local.properties` is ignored because it contains a machine-specific SDK path;
- APK/AAB artifacts are ignored and should go to GitHub Releases;
- keystores, signing config, env files, and local secrets are ignored;
- downloaded model files and local model directories are ignored;
- Codex/local assistant artifacts are ignored;
- `meta-docs/` is ignored until those research notes are cleaned for public reading.

## Forking Notes

Forks are welcome to change the UI, add new local tools, replace the model backend, or experiment with agent orchestration. The important safety rule is that model output should not be executed directly. It should be parsed, validated against an allowlist, checked against Android permissions, and only then mapped to real device actions.

## License

The project is intended to be full open source. Add a `LICENSE` file before the first public release so downstream users know the exact terms.

