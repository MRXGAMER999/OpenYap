# OpenYap PR Review — Issues & M3 Expressive Migration Candidates

Reviewed against the `cursor/navigation-rail-material-design-6a59` branch (M3 Expressive shell redesign + Koin DI refactor + Groq pipeline + recording overlay).

---

## Part 1 — PR Issues

### 1. Bugs

#### B-01 · `RecordingViewModel.stopRecording()` / `cancelRecording()` not guarded by `recordingMutex` — **HIGH**

`startRecording()` is wrapped in `recordingMutex.withLock`, but `stopRecording()` (line 312) and `cancelRecording()` (line 642) are not. A rapid hold-release from the hotkey manager can interleave `stopRecording` with a new `startRecording`, causing `durationJob` to be cancelled mid-assignment, `currentRecordingPath` to be nulled while a new recording begins, or the overlay to be dismissed and re-shown in the wrong order.

**File:** `shared/.../viewmodel/RecordingViewModel.kt`
**Fix:** Wrap both methods in the same `recordingMutex.withLock`.

---

#### B-02 · `RecordingViewModel.onCleared()` does not stop active recording or clean temp files — **HIGH**

`onCleared()` only calls `clearSessionContext()`. It does not cancel `durationJob`, stop an active recording (`audioRecorder.stopRecording()`), or delete temp files (`currentRecordingPath`, `currentProcessingPath`). While `viewModelScope` cancellation handles coroutine jobs, the audio recorder resource is never released and temp WAV/WEBM files leak on disk.

**File:** `shared/.../viewmodel/RecordingViewModel.kt:692`
**Fix:** In `onCleared()`, cancel `durationJob`, call `audioRecorder.stopRecording()` defensively, and delete orphaned temp files.

---

#### B-03 · `hotkeyManager` close is always null in Quit handler — **HIGH**

In `main.kt:169`, `(hotkeyManager as? java.io.Closeable)?.close()` — the `HotkeyManager` interface does not extend `Closeable`, so this cast always evaluates to `null`. The native hotkey listener thread and its Win32 message pump are never shut down on application exit.

**File:** `composeApp/.../main.kt:169`
**Fix:** Cast to the concrete `WindowsHotkeyManager` which implements `Closeable`, or have `HotkeyManager` extend `Closeable`.

---

#### B-04 · `GeminiClient.rewriteText()` may extract thinking output instead of final answer — **MEDIUM**

With `thinkingConfig` enabled (Gemini 2.5 models), the response may contain a reasoning/thought part before the actual text part. The code uses `.firstOrNull { it.text != null }` which takes the first text part — this could be the internal reasoning, not the final answer.

**File:** `shared/.../service/GeminiClient.kt:173-178`
**Fix:** Use `.lastOrNull { it.text != null }` or filter parts by a `thought` flag if the API exposes one.

---

#### B-05 · `GeminiClient.executeWithRetry()` retries non-transient 4xx errors — **MEDIUM**

The retry loop catches all non-`CancellationException` exceptions, including `GeminiException("API error (401)")` and `GeminiException("API error (400)")`. Invalid API keys or malformed requests should not be retried. Contrast with `GroqWhisperClient` which correctly only retries transient (429/5xx/timeout/IO) errors.

**File:** `shared/.../service/GeminiClient.kt:255-271`
**Fix:** Only retry on transient errors (5xx, 429, timeout, `IOException`). Propagate 4xx errors immediately.

---

#### B-06 · `OnboardingViewModel.saveApiKey()` blocks event channel on `fetchJob?.join()` — **MEDIUM**

`saveApiKey()` calls `fetchModels(trimmed)` which calls `fetchJob?.join()`. Since `saveApiKey` runs inside the single-consumer channel collector, the `join()` blocks all other onboarding events (mic permission checks, use-case selection, etc.) until the network fetch completes. The UI appears unresponsive.

**File:** `shared/.../viewmodel/OnboardingViewModel.kt:175`
**Fix:** Launch the fetch in a separate coroutine or use a non-blocking coordination pattern.

---

#### B-07 · Unsafe cast `overlayController as ComposeOverlayController` — **MEDIUM**

`main.kt:175` casts the DI-provided `OverlayController` to the concrete `ComposeOverlayController`. If DI configuration changes, this throws `ClassCastException` at runtime. The purpose of the `OverlayController` interface is defeated.

**File:** `composeApp/.../main.kt:175`
**Fix:** Expose `uiState: StateFlow` through the `OverlayController` interface, or bind the concrete class directly in DI.

---

#### B-08 · `RecordingOverlay` mutates state during composition — **MEDIUM**

`if (flashMessage != null) lastFlashMessage = flashMessage` writes to a `mutableStateOf` directly in the composition body. This violates Compose's side-effect policy and can cause unpredictable recomposition behavior.

**File:** `composeApp/.../ui/component/RecordingOverlay.kt`
**Fix:** Move the assignment into a `SideEffect` or `LaunchedEffect` block.

---

#### B-09 · `PrimaryShellAction` fakes disabled state — accessibility bug — **MEDIUM**

`SmallExtendedFloatingActionButton` uses `onClick = { if (enabled) onClick() }` with `Modifier.alpha(0.38f)` to visually simulate disabled. Screen readers still announce it as interactive and it remains keyboard-focusable.

**File:** `composeApp/.../ui/navigation/AppShell.kt`
**Fix:** Use the `enabled` parameter of the composable (if available), or add `Modifier.semantics { disabled() }` and `Modifier.focusProperties { canFocus = false }`.

---

#### B-10 · `ReducedMotion.jvm.kt` — no timeout on `process.waitFor()` — **MEDIUM**

`windowsAnimationsEnabled()` launches `reg query` via `ProcessBuilder` and calls `process.waitFor()` without a timeout. If the registry query hangs, the calling thread blocks indefinitely, freezing the Compose theme initialization.

**File:** `composeApp/.../ui/theme/ReducedMotion.jvm.kt`
**Fix:** Use `process.waitFor(timeout, TimeUnit)` with a reasonable limit (e.g., 2 seconds).

---

#### B-11 · `WindowsPasteAutomation.pasteJna()` does not check `SendInput` return value — **MEDIUM**

`SendInput` returns the number of events inserted. If it returns 0, the input was blocked (e.g., by UIPI / UAC). The user sees no paste with no error message.

**File:** `shared/.../platform/WindowsPasteAutomation.kt:110`
**Fix:** Check the return value and surface an error to the user.

---

#### B-12 · `deleteAllData()` in `OpenYapDatabase` is not transactional — **MEDIUM**

Deletes from 6 tables in sequence with no `@Transaction` annotation. If the process crashes mid-way, the database is left in a partially cleared state.

**File:** `shared/.../database/OpenYapDatabase.kt:28-35`
**Fix:** Add `@Transaction` annotation to `deleteAllData()`.

---

#### B-13 · `RoomSettingsRepository` — `loadSettings()` and `saveSettings()` bypass `settingsMutex` — **MEDIUM**

`updateSettings()` correctly uses `settingsMutex`, but the public `loadSettings()` and `saveSettings()` methods are unguarded. External callers doing manual load-modify-save bypass the atomic update guarantee.

**File:** `shared/.../repository/RoomSettingsRepository.kt`
**Fix:** Make `saveSettings()` internal/private, or route all writes through `updateSettings()`.

---

#### B-14 · `SettingsViewModel` — `Slider.onValueChange` fires repository writes on every drag pixel — **LOW**

Volume sliders emit `SettingsEvent` on every drag movement, causing excessive I/O if the handler writes to disk.

**File:** `composeApp/.../ui/screen/SettingsScreen.kt`
**Fix:** Use local state for drag + `onValueChangeFinished` for persistence.

---

#### B-15 · `OnboardingScreen` — `micSettingsUnavailable` snackbar won't re-show — **LOW**

The boolean flag stays `true` after first trigger; `LaunchedEffect` won't re-fire without a unique event ID.

**File:** `composeApp/.../ui/screen/OnboardingScreen.kt`
**Fix:** Use a counter or event-based trigger instead of a boolean.

---

### 2. Race Conditions

#### R-01 · `RecordingViewModel.sessionContextEntries` (`ArrayDeque`) — data race — **MEDIUM**

Mutated in `rememberSessionContext()` and `pruneExpiredSessionContext()` from the processing coroutine, and in `clearSessionContext()` from `onCleared()` on the main thread. `ArrayDeque` is not thread-safe.

**File:** `shared/.../viewmodel/RecordingViewModel.kt`
**Fix:** Use a `ConcurrentLinkedDeque`, or serialize access via `recordingMutex`.

---

#### R-02 · `SettingsViewModel` — concurrent toggle coroutines can lose updates — **MEDIUM**

Multiple settings toggle methods launch independent coroutines. Each performs read-modify-write via `settingsRepository.updateSettings`. Rapid sequential toggles can interleave and lose one update.

**File:** `shared/.../viewmodel/SettingsViewModel.kt`
**Fix:** Use a `Mutex` or serial event channel for settings mutations.

---

#### R-03 · `WindowsHotkeyManager.switchToFallback()` races with `captureNextHotkey()` — **MEDIUM**

`switchToFallback()` sets `usingFallback` inside `synchronized(fallbackLock)` but then manipulates native calls outside the lock. Concurrent `captureNextHotkey()` can observe stale `usingFallback = false`.

**File:** `shared/.../platform/WindowsHotkeyManager.kt`
**Fix:** Extend the synchronized block or use the existing `controlMutex` consistently.

---

#### R-04 · `ComposeOverlayController.flashMessage()` — `synchronized` blocks coroutine thread — **LOW**

Uses JVM `synchronized(flashLock)` which is not coroutine-aware. If called from a coroutine on a shared dispatcher, the monitor lock can block the underlying thread.

**File:** `composeApp/.../platform/ComposeOverlayController.kt`
**Fix:** Replace with `kotlinx.coroutines.sync.Mutex`.

---

### 3. Leaked Resources

#### L-01 · `JnaWindowsHotkeyManager` — dedicated thread leaks if `close()` not called — **HIGH**

`newSingleThreadContext("HotkeyThread")` creates a dedicated thread. If the manager is garbage-collected without `close()`, this thread persists for the lifetime of the JVM process.

**File:** `shared/.../platform/JnaWindowsHotkeyManager.kt:55`
**Fix:** Ensure `close()` is always called (see B-03), or use a `PhantomReference`-based safety net.

---

#### L-02 · `JvmAudioRecorder` — `CoroutineScope` never cancelled — **HIGH**

`scope = CoroutineScope(Dispatchers.IO + SupervisorJob())` on line 32 is never cancelled. The class has no `close()` or `Closeable` implementation.

**File:** `shared/.../platform/JvmAudioRecorder.kt:32`
**Fix:** Implement `Closeable` and cancel the scope in `close()`.

---

#### L-03 · `WindowsHotkeyManager` — scope leaks if `close()` not called — **MEDIUM**

`scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` is only cancelled in `close()`. Same shutdown gap as L-01.

**File:** `shared/.../platform/WindowsHotkeyManager.kt:37`

---

#### L-04 · `SettingsViewModel` — resource stream not closed in `refresh()` — **MEDIUM**

`getResourceAsStream("/version.properties")?.bufferedReader()?.readText()` — the underlying `InputStream` is never closed. Should use `.use { ... }`.

**File:** `shared/.../viewmodel/SettingsViewModel.kt:134`

---

#### L-05 · `HttpClientFactory` — new `HttpClient` per call — **MEDIUM**

Each call to `createGeminiClient()` etc. creates a new `HttpClient` with its own connection pool. If factory methods are called multiple times, old clients are never closed.

**File:** `shared/.../platform/HttpClientFactory.kt`
**Fix:** Ensure clients are singletons via DI and closed on shutdown.

---

### 4. Architecture — JVM Code That Belongs in Native C++

#### A-01 · `JnaWindowsHotkeyManager` — WH_KEYBOARD_LL hook in JVM thread

Windows low-level keyboard hooks must return within ~300ms or Windows silently removes the hook. GC pauses, JIT compilation, or contention on the hook thread can cause the hook to be dropped. The native DLL already provides `openyap_start_hotkey_listener()` — the JNA fallback carries this inherent risk.

**File:** `shared/.../platform/JnaWindowsHotkeyManager.kt`

---

#### A-02 · `WindowsForegroundAppDetector` — multiple JNA/JNI round-trips

Makes 4+ JNA calls (`GetForegroundWindow`, `GetWindowThreadProcessId`, `OpenProcess`, `QueryFullProcessImageName`, `GetWindowText`) which each cross the JNI boundary. The native DLL could do this in a single native call.

**File:** `shared/.../platform/WindowsForegroundAppDetector.kt`

---

#### A-03 · `WindowsPasteAutomation` — clipboard + SendInput via JNA

`pasteJna()` sets the clipboard and then sends `Ctrl+V` through 4 `INPUT` structures via JNA. The native DLL's `openyap_paste_text` does this atomically in C++ with no JNI overhead.

**File:** `shared/.../platform/WindowsPasteAutomation.kt`

---

### 5. Performance

#### P-01 · `RecordingViewModel.readFileBytes()` loads entire audio into memory — **MEDIUM**

The audio file is read into `ByteArray`, then Base64-encoded by `GeminiClient.processAudio()` (1.33x expansion). Peak memory is ~2.33x the audio file size. Multi-minute recordings at 48 kHz can be tens of MB.

**File:** `shared/.../viewmodel/RecordingViewModel.kt:881`, `shared/.../service/GeminiClient.kt:197`
**Fix:** Consider streaming or enforcing a max recording duration.

---

#### P-02 · `NativeAudioRecorder.trimSilence()` — per-frame `copyOfRange` allocations — **MEDIUM**

Each VAD frame allocates `samples.copyOfRange(start, end)`. For a 60s recording at 48 kHz, that's ~3000 frame copies. A pre-allocated frame buffer would eliminate this.

**File:** `shared/.../platform/NativeAudioRecorder.kt`

---

#### P-03 · `HomeHeroCard` — idle pulse animation runs in all states — **LOW**

`rememberInfiniteTransition` for `idlePulseAlpha` keeps ticking during Recording/Processing/Success/Error states when the mic icon isn't visible. Wastes CPU on invisible animation.

**File:** `composeApp/.../ui/navigation/HomeHeroCard.kt`
**Fix:** Gate the transition behind `state == Idle`.

---

#### P-04 · `OnboardingScreen` — 4 `rememberInfiniteTransition` instances tick for locked steps — **LOW**

Infinite transitions for locked/complete steps waste CPU. Gate behind `stepState == StepState.ACTIVE`.

**File:** `composeApp/.../ui/screen/OnboardingScreen.kt`

---

#### P-05 · `RecordingOverlay.WaveformBars` — allocates `barOffsets` list on every recomposition — **LOW**

**File:** `composeApp/.../ui/component/RecordingOverlay.kt`
**Fix:** Wrap in `remember`.

---

### 6. Missing Error Handling / Edge Cases

#### E-01 · `GeminiClient.listModels()` silently falls back on auth failure — **MEDIUM**

Catches all exceptions and returns `DEFAULT_MODELS`. An invalid API key appears to succeed (user sees models, thinks key is valid).

**File:** `shared/.../service/GeminiClient.kt:84`

---

#### E-02 · `EntityMappers` — silent fallback on deserialization errors — **LOW**

Multiple `try/catch(_: Exception)` blocks silently fall back to defaults when database values can't be parsed. Data corruption or schema changes go unnoticed.

**File:** `shared/.../database/EntityMappers.kt`

---

#### E-03 · `StatsScreen` — outer `Column` lacks `verticalScroll()` — **LOW**

Content can overflow with many top apps or small windows.

**File:** `composeApp/.../ui/screen/StatsScreen.kt`

---

#### E-04 · `HistoryScreen` / `DictionaryScreen` / `CustomizationScreen` — `LazyColumn` missing `weight(1f)` — **LOW**

No height constraint; content may overflow without scrolling in edge cases.

**Files:** `composeApp/.../ui/screen/HistoryScreen.kt`, `DictionaryScreen.kt`, `CustomizationScreen.kt`

---

#### E-05 · `main.kt` — swallowed exception in audio preload — **LOW**

`catch (_: Exception) {}` at line 142 silently swallows all preload failures. At minimum log the error.

**File:** `composeApp/.../main.kt:142`

---

#### E-06 · `DatabaseFactory` — no fallback migration strategy — **LOW**

If a user has a database version > 6 (e.g., from a dev build), Room throws `IllegalStateException` and the app won't start. Consider adding `fallbackToDestructiveMigration()`.

**File:** `shared/.../database/DatabaseFactory.kt`

---

### 7. Compose-Specific Issues

#### C-01 · `AppShell.contentVisibility` entrance animation is one-shot

`MutableTransitionState(false).apply { targetState = true }` fires once. Navigating to onboarding and back doesn't reset it — the entrance animation won't replay.

**File:** `composeApp/.../ui/navigation/AppShell.kt`

---

#### C-02 · `AppShell.navigateTo` mutates `backStack` non-atomically

`backStack.clear()` then `backStack.add(route)` creates a brief intermediate empty-list state. If `NavDisplay` reads between writes, the screen could briefly flash empty.

**File:** `composeApp/.../ui/navigation/AppShell.kt`
**Fix:** Replace the list and use a single `backStack.replaceAll(listOf(route))` pattern.

---

#### C-03 · `CustomizationScreen` — `remember` keyed on `Map` reference equality

`remember(appTones, appPrompts)` keys on `Map` reference equality. May recompute on structurally identical but referentially new maps.

**File:** `composeApp/.../ui/screen/CustomizationScreen.kt`

---

#### C-04 · `AppTheme` — `LightColorScheme` missing surface container tokens

`DarkColorScheme` sets all 7 surface container slots; light scheme uses M3 defaults. Causes visual inconsistency since the app uses `surfaceContainerHigh`.

**File:** `composeApp/.../ui/theme/AppTheme.kt`

---

#### C-05 · `Routes.kt` — `primaryRailRoutes = railRoutes.take(7)` is fragile

Hardcoded take count silently changes behavior if routes are added/removed.

**File:** `composeApp/.../ui/navigation/Routes.kt`

---

---

## Part 2 — Material 3 Expressive Migration Candidates

The app already uses `MaterialExpressiveTheme` + `MotionScheme.expressive()` and `WideNavigationRail` with `WideNavigationRailItem`. Below are the remaining high-value upgrades.

### Theme & Foundation

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-01 | `LightColorScheme` (manual) | `expressiveLightColorScheme()` | Ensures full expressive token coverage including surface container slots currently missing from the light scheme. |

### Navigation

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-02 | `WideNavigationRail` (already adopted) | Add `ModalWideNavigationRail` variant | When collapsed on narrower windows, the rail could overlay content rather than permanently consuming width. Already have `rememberWideNavigationRailState()` — add modal behavior for compact layouts. |

### Floating Action Buttons

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-03 | `SmallExtendedFloatingActionButton` (record/stop toggle in `AppShell`) | `ToggleFloatingActionButton` + `Modifier.animateFloatingActionButton` | The FAB already toggles between Mic/Stop icons — a `ToggleFloatingActionButton` gives animated container size, corner radius, and color transitions driven by checked state. |
| M-04 | `MediumExtendedFloatingActionButton` (referenced in `AppShell`) | `MediumFloatingActionButton` with `FloatingActionButtonMenu` | If additional actions are planned alongside the primary record action, a `FloatingActionButtonMenu` provides staggered reveal animations for secondary actions. |

### Progress & Loading Indicators

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-05 | `CircularProgressIndicator` (loading splash in `AppShell`) | `CircularWavyProgressIndicator` or `ContainedLoadingIndicator` | App splash/loading screen; wavy indicator adds premium feel. `RecordingOverlay` already uses `CircularWavyProgressIndicator` — this makes them consistent. |
| M-06 | `CircularProgressIndicator` (Settings "fetching models" spinner) | `CircularWavyProgressIndicator` | Visual consistency with the rest of the app's expressive loading states. |
| M-07 | `LinearProgressIndicator` (Onboarding step progress) | `LinearWavyProgressIndicator` | Onboarding is the first-run experience — a wavy indicator gives a more engaging, branded feel during setup. |

### Buttons — Shape Morphing

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-08 | `Button` (Settings save/apply, Onboarding CTA) | `Button(..., shapes = ButtonDefaults.shapes())` | Adds press shape-morphing for free — more tactile interaction without custom animation code. |
| M-09 | `FilledTonalButton` (Stats, UserInfo, Settings) | `FilledTonalButton(..., shapes = ButtonDefaults.shapes())` | Same shape-morphing benefit for tonal button instances. |
| M-10 | `OutlinedButton` (Settings destructive actions) | `OutlinedButton(..., shapes = ButtonDefaults.shapes())` | Shape-morphing makes destructive action buttons feel more deliberate. |
| M-11 | `TextButton` (dialog dismiss buttons) | `TextButton(..., shapes = ButtonDefaults.shapes())` | Consistent expressive press feel across all button types. |

### Icon Buttons — Shape Morphing

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-12 | `IconButton` (rail toggle, menu hamburger, setting controls) | `IconButton(..., shapes = IconButtonDefaults.shapes())` | Adds press shape-morphing to icon actions throughout the app. |

### Toggle Patterns

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-13 | `FilterChip` used as toggle chips (Onboarding use-case selection) | `ToggleButton` / `TonalToggleButton` | Use-case chips behave as toggles — `ToggleButton` provides animated shape transitions between selected/unselected states with proper semantics. |
| M-14 | `Switch` toggles (Settings boolean options) | `ToggleButton` (for grouped option cards) | Where settings are presented as cards rather than simple rows, `ToggleButton` gives richer visual feedback. Keep `Switch` for standard on/off rows. |

### Menus

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-15 | `DropdownMenu` + `DropdownMenuItem` (AppShell overflow menu) | `DropdownMenuGroup` + expressive `DropdownMenuItem` with `supportingText` | Groups menu items by category (profile, actions) and adds supporting text for clarity. |
| M-16 | `ExposedDropdownMenuBox` + `DropdownMenuItem` (Settings model pickers) | Expressive `DropdownMenuItem` with `supportingText` | Model selection items can show the model ID as supporting text beneath the display name. |

### List Items

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-17 | `OutlinedCard` rows (History entries, Dictionary entries, Customization entries) | `SegmentedListItem` | Entries form grouped/connected lists — `SegmentedListItem` provides built-in connected visual treatment, checked/selected states, and proper list group semantics. |

### Top App Bar

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-18 | No explicit `TopAppBar` (screens use `Scaffold` with custom header rows) | `MediumFlexibleTopAppBar` or expressive `TopAppBar(title, subtitle)` | Settings and other screens have manual title/subtitle rows — a flexible top app bar provides proper collapse/expand behavior with scroll. |

### Search

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-19 | Custom `OutlinedTextField` search (Dictionary filter) | `AppBarWithSearch` or `ExpandedDockedSearchBarWithGap` | Dictionary filtering uses a plain text field — a proper search bar provides animated expand/collapse, scrim, and search-bar semantics. |

### Split Button

| # | Current | Recommended | Reason |
|---|---------|-------------|--------|
| M-20 | Primary action + separate dropdown trigger (Customization "add" flows) | `SplitButtonLayout` | Where a primary action pairs with a dropdown for alternate modes, `SplitButtonLayout` provides the correct visual grouping and interaction model. |

---

## Priority Summary

### Must Fix Before Merge (HIGH)
- **B-01** `stopRecording`/`cancelRecording` missing mutex
- **B-02** `onCleared()` does not clean up active recording
- **B-03** `hotkeyManager` close always null
- **L-01** Hotkey thread leaks
- **L-02** `JvmAudioRecorder` scope never cancelled

### Should Fix (MEDIUM)
- **B-04** Gemini thinking output extraction
- **B-05** Retry on non-transient errors
- **B-06** Onboarding event channel blocked by fetch
- **B-07** Unsafe cast of `OverlayController`
- **B-08** State mutation during composition
- **B-09** Fake disabled FAB accessibility
- **B-10** No timeout on `process.waitFor()`
- **B-11** `SendInput` return value unchecked
- **B-12** Non-transactional `deleteAllData()`
- **B-13** Settings mutex bypass
- **R-01** `sessionContextEntries` data race
- **R-02** Concurrent settings toggle races
- **R-03** `switchToFallback` race
- **L-04** Resource stream not closed
- **L-05** `HttpClient` per call leak
- **P-01** Full audio file in memory + base64

### Top M3 Expressive Wins (Post-Merge)
- **M-03** `ToggleFloatingActionButton` for record/stop FAB
- **M-05/06/07** Wavy progress indicators across app (consistency)
- **M-08–M-12** Shape-morphing button overloads (one-liner upgrades)
- **M-15** `DropdownMenuGroup` for overflow menus
- **M-17** `SegmentedListItem` for history/dictionary/customization lists
