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

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **OpenYap** (1730 symbols, 2551 relationships, 0 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/OpenYap/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/OpenYap/context` | Codebase overview, check index freshness |
| `gitnexus://repo/OpenYap/clusters` | All functional areas |
| `gitnexus://repo/OpenYap/processes` | All execution flows |
| `gitnexus://repo/OpenYap/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## CLI

- Re-index: `npx gitnexus analyze`
- Check freshness: `npx gitnexus status`
- Generate docs: `npx gitnexus wiki`

<!-- gitnexus:end -->
