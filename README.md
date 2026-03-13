# OpenYap

> Hold a hotkey, speak naturally, and paste polished text into the app you are already using.

![Platform](https://img.shields.io/badge/platform-Windows%20Desktop-0f766e?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-1f2937?style=for-the-badge)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.10.2-0b3b2e?style=for-the-badge)
![AI](https://img.shields.io/badge/AI-Gemini%20%2B%20Groq-cb6e17?style=for-the-badge)

OpenYap is a Windows-first desktop app built with Kotlin Multiplatform and Compose Multiplatform.
It records your voice, transcribes it, refines the result with AI, and pastes the final text back into the active app.

## Why it exists

Typing polished text is slower than saying what you mean.

OpenYap turns quick voice capture into writing that feels ready to send, whether you are replying in chat, drafting notes, or writing longer-form text in another app.

## What it does

- Global hold-to-record hotkey on Windows
- Floating recording overlay with live visual feedback
- AI transcription and rewriting with Gemini and Groq
- Per-app tone and prompt customization
- Phrase expansion from profile data and custom dictionary entries
- Recording history and usage stats
- System tray support, start minimized, and launch-on-startup options

## How the flow works

1. Hold your configured hotkey.
2. Speak naturally.
3. Release to stop recording.
4. OpenYap transcribes and refines the text.
5. The final result is pasted into the active Windows app.

## Highlights

### AI modes

- `Gemini` for end-to-end transcription and rewrite
- `Groq Whisper` for transcription
- `Groq -> Gemini correction` for a two-step cleanup flow

### Personalized writing

- App-aware prompts based on the foreground application
- Custom per-app tone and instruction presets
- User profile and dictionary-driven phrase expansion
- Dictionary auto-learning from observed text

### Desktop-native behavior

- Tray-first workflow
- Quiet close-to-tray behavior
- Native Windows integrations for hotkeys, paste automation, permissions, and startup
- Native audio pipeline with JVM fallback

## Tech stack

- Kotlin `2.3.0`
- Compose Multiplatform `1.10.2`
- Material 3 Expressive UI
- Ktor client + CIO
- Kotlinx Serialization, Coroutines, and DateTime
- JNA + native Windows bridge for platform integrations

## Project structure

```text
.
|- composeApp/   Desktop app entrypoint, Compose UI, packaging
|- shared/       Shared models, services, repositories, viewmodels
|- native/       Native Windows audio bridge and prebuilt DLL
```

## Getting started

### Requirements

- Windows
- JDK `21+`
- A full JDK with `jpackage` available through `JPACKAGE_JAVA_HOME`, `JAVA_HOME`, or `PATH`
- Gemini and/or Groq API keys

### Run locally

```powershell
.\gradlew.bat :composeApp:run
```

### Package an MSI

```powershell
.\gradlew.bat :composeApp:packageMsi
```

The desktop packaging flow expects the native DLL at `native/prebuilt/windows-x64/openyap_native.dll`.

## First-run setup

Onboarding guides the user through:

- microphone permission
- Gemini API key entry
- model selection
- entering the main app

Additional settings, including Groq keys, hotkey changes, startup behavior, and audio checks, are managed in-app.

## Privacy and local data

- API keys are stored locally
- On Windows, secure credentials use DPAPI-backed storage
- App data is persisted locally as JSON under `%APPDATA%/OpenYap`
- Do not commit keys, recordings, or personal data

## Architecture notes

- Two Gradle modules: `:composeApp` and `:shared`
- Current targets are JVM desktop only
- UI follows a Compose + ViewModel style
- Business logic, repositories, and service clients live in `shared`
- Dependencies are wired manually instead of through a DI framework

## Development

Useful commands:

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :composeApp:jvmTest
```

## Status

OpenYap is actively shaped around a focused Windows desktop workflow: capture voice fast, clean it up, and paste polished text with minimal interruption.
