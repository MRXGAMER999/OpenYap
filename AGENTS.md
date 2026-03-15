# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

OpenYap is a **Windows-first** Compose Desktop app (Kotlin Multiplatform + Compose Multiplatform). It has two Gradle modules: `:composeApp` (UI entrypoint) and `:shared` (business logic, services, repositories, viewmodels).

### Build & dev commands

See `README.md` "Development" section for standard commands. Key ones:

- **Compile**: `./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm`
- **Check**: `./gradlew check`
- **Test**: `./gradlew :shared:jvmTest :composeApp:jvmTest` (currently no test source files exist)
- **Run**: `./gradlew :composeApp:run`

### Linux (Cloud Agent) environment caveats

- **JAVA_HOME** must be set to a JDK 21+ installation: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
- The Gradle daemon JVM is configured for JetBrains JDK 21 via `gradle/gradle-daemon-jvm.properties` with foojay auto-provisioning. The OpenJDK 21 on the system works fine for building.
- **Running the app on Linux** works via `DISPLAY=:1` (Xvfb). For GL rendering, set `LIBGL_ALWAYS_SOFTWARE=1` and `MESA_GL_VERSION_OVERRIDE=4.1`. The app opens but logs non-fatal errors for Windows-specific native code (`dwmapi`, `openyap_native`).
- **System tray** is not supported on the Linux headless environment. The app logs a warning but continues.
- **No lint tools** (detekt, ktlint, spotless) are configured. The only check task is the built-in Gradle `check` which runs compilation and tests.
- **No test files** exist in the project. `./gradlew :shared:jvmTest :composeApp:jvmTest` completes with `NO-SOURCE`.
- Native Windows DLL (`openyap_native.dll`) is only needed on Windows; the app falls back to `JvmAudioRecorder` on Linux.
- **Onboarding**: On first launch the app shows a 4-step onboarding (mic check, Gemini API key, model selection, optional use-case). The `Gemini Api key` secret must be available as an environment variable. Copy it to the X clipboard (`printenv "Gemini Api key" | tr -d '\n' | DISPLAY=:1 xclip -selection clipboard`) then paste into the API key field and click "Save & Verify".
