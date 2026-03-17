# OpenYap UI Code Review Report

## File 1: `AppShell.kt`

### Part 1 — Issues

1. **Fake disabled state on `PrimaryShellAction` (accessibility bug)**
   `SmallExtendedFloatingActionButton` is never truly disabled. The composable uses `onClick = { if (enabled) onClick() }` combined with `Modifier.alpha(0.38f)` to visually simulate a disabled state. Screen readers will still announce the button as interactive, and it remains focusable/clickable via keyboard. Should use a proper Material `enabled` parameter or wrap in a `Box` with `Modifier.clickable(enabled = ...)`.

2. **`navigateTo` mutates `backStack` non-atomically (potential blank frame)**
   `backStack.clear()` then `backStack.add(route)` are two separate state writes on a `SnapshotStateList`. While the snapshot system batches them before the next recomposition in practice, any observer reading between the two writes would see an empty list. If `NavDisplay` or an interceptor reads the empty state, the screen could briefly flash empty. A safer pattern is `backStack.apply { clear(); add(route) }` or using `removeRange`/`set` to keep at least one entry at all times.

3. **`contentVisibility` entrance animation is one-shot**
   `MutableTransitionState(false).apply { targetState = true }` fires once. If the user navigates to Onboarding and back (the `if (!onboardingState.isComplete) ... return` path), the `contentVisibility` is not reset — the entrance animation will not replay on re-entry.

4. **`latestResultText` state has subtle reset race**
   `var latestResultText by remember(state.lastResultText) { ... }` resets every time `state.lastResultText` changes. The `LaunchedEffect(state.recordingState)` also writes to `latestResultText`. If `lastResultText` changes in the state object while the recording state is `Recording(durationSeconds=0)`, the `remember` will first restore the new value, then the LaunchedEffect will null it out. This causes a visual flash of the text followed by immediate disappearance.

5. **`isPrimaryActionEnabled` name is misleading**
   The computed value returns `true` for every state except `Processing`. The name suggests it checks general readiness, but it's really an "is not processing" flag. Rename to `isNotProcessing` or invert.

6. **`koinInject` inside `entry<Route.X>` lambdas is called on every recomposition of the NavDisplay content**
   ViewModels injected via `koinInject<HistoryViewModel>()` etc. inside `entry { }` blocks are resolved every time the content recomposes. If Koin's scope doesn't cache these, new instances are created each time. Verify that ViewModels are scoped as singletons or use `koinInject` with a remember-based caching scope.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `CircularProgressIndicator` (loading splash) | `CircularWavyProgressIndicator` or `LoadingIndicator` | Line 152 |
| `SmallExtendedFloatingActionButton` (record/stop toggle) | `ToggleFloatingActionButton` (checked = isRecording) | `PrimaryShellAction` |
| `IconButton` (menu hamburger) | Expressive `IconButton` with `IconButtonShapes` | `ShellTopBar` |
| `DropdownMenu` + `DropdownMenuItem` | `DropdownMenuGroup` + expressive `DropdownMenuItem` | `ShellRouteMenu` |

---

## File 2: `AppRail.kt`

### Part 1 — Issues

1. **No issues found.** Clean, well-structured composable. `WideNavigationRail` is used correctly.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `IconButton` (rail toggle) | Expressive `IconButton` with `IconButtonShapes` | `RailToggleButton` |

Already uses `WideNavigationRail` — no rail migration needed.

---

## File 3: `AppShellLayout.kt`

### Part 1 — Issues

1. **No issues found.** Pure layout resolution logic with no composables or state.

### Part 2 — M3 Expressive Candidates

None — no UI components in this file.

---

## File 4: `HomeHeroCard.kt`

### Part 1 — Issues

1. **`idlePulseAlpha` infinite transition runs unconditionally**
   The `rememberInfiniteTransition("idleMicPulse")` and its `animateFloat` run continuously regardless of whether the mic icon is visible (it's only shown in `Idle` state). The transition keeps ticking during `Recording`, `Processing`, `Success`, and `Error` states, wasting CPU/battery. Guard with a conditional or use `AnimatedVisibility`'s built-in state to skip the animation.

2. **Clickable `Surface` mic button lacks merged semantic content description**
   The `Surface(onClick = ...)` at line 325 relies on the child `Icon`'s `contentDescription` for accessibility. In Compose, clickable modifiers merge child semantics, but to be safe and clear, the content description should be set on the clickable surface itself via `Modifier.semantics { contentDescription = ... }`.

3. **`@OptIn(ExperimentalAnimationApi::class)` is unnecessary**
   The file uses `AnimatedContent` and `AnimatedVisibility` which are stable. The `ExperimentalAnimationApi` opt-in is not required and should be removed to reduce API surface coupling.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| Custom `Surface(onClick=...)` circle button (record/stop) | `ToggleFloatingActionButton` (checked = isRecording) | Line 324-349 |
| `AssistChip` (disabled, info badges) | Informational `Surface` badges or `SegmentedListItem` | Lines 251-303 |
| `ElevatedCard` (latest result) | Already expressive — optionally wrap text in `SegmentedListItem` | Line 358 |

---

## File 5: `Routes.kt`

### Part 1 — Issues

1. **`primaryRailRoutes = railRoutes.take(7)` is fragile**
   `railRoutes` has exactly 7 items, so `take(7)` returns all of them. If a route is added or removed, this silently changes behavior. Either use `railRoutes` directly (since they're identical) or add a compile-time assertion.

### Part 2 — M3 Expressive Candidates

None — data-only file.

---

## File 6: `RecordingOverlay.kt`

### Part 1 — Issues

1. **Side effect during composition: `lastFlashMessage` assignment (bug)**
   Line 130: `if (flashMessage != null) lastFlashMessage = flashMessage` mutates state during composition body. This is a side effect that should be in `LaunchedEffect` or `SideEffect`. While it stabilizes after one extra recomposition (the value converges), it violates Compose's requirement that composition be side-effect-free, and under concurrent composition or strict mode this could cause issues.

2. **`ErrorBar` uses theme-dependent colors instead of overlay palette (visual inconsistency)**
   `ErrorBar` uses `MaterialTheme.colorScheme.error` for icon tint and text color, while `SuccessBar` and `ActiveBar` use hard-coded overlay palette colors (`OverlaySuccess`, `OverlayAccent`). Since the overlay is documented as "always dark glass, independent of app theme," the error bar will look inconsistent — its red will change with the app's light/dark mode while everything else stays fixed. Define an `OverlayError` color constant.

3. **`FlashMessageRow` also uses theme-dependent error color**
   Same issue: the `errorColor` at line 271 reads from `MaterialTheme.colorScheme.error`, creating theme-dependence in an otherwise theme-independent overlay.

4. **`WaveformBars` allocates a `List` on every recomposition**
   `val barOffsets = (0 until barCount).map { ... }` creates a new list every recomposition. While `rememberInfiniteTransition` handles its own lifecycle, the list allocation is wasteful. Wrap with `remember` or use individual `val` declarations.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `CircularWavyProgressIndicator` | Already M3 Expressive ✓ | Line 170 |

The overlay is a custom floating component — standard M3 component replacements don't directly apply.

---

## File 7: `SettingsScreen.kt`

### Part 1 — Issues

1. **Shadowed `reducedMotion` variable**
   `val reducedMotion = reducedMotionEnabled()` at line 98 (top-level) is shadowed by another `val reducedMotion = reducedMotionEnabled()` at line 146 (inside "Input controls" card). Redundant and confusing. Remove the inner one and use the outer.

2. **`Slider.onValueChange` fires ViewModel event on every drag pixel**
   `onValueChange = { onEvent(SettingsEvent.SetSoundFeedbackVolume(it)) }` dispatches a new event for every pixel of slider movement. If the event handler writes to disk or DataStore, this causes excessive I/O. Use a local `var sliderValue` for the visual and dispatch only in `onValueChangeFinished`.

3. **`CircularProgressIndicator` instances could be wavy (consistency)**
   Two `CircularProgressIndicator` instances (lines 201, 764) remain as the standard variant while the codebase uses `CircularWavyProgressIndicator` elsewhere (RecordingOverlay). Minor visual inconsistency.

4. **Deeply nested conditional provider UI is hard to maintain**
   The model selection section has a 4-way `if/else if/else` branching by `transcriptionProvider`, each with loading/error/content states. This is not a bug but makes the code fragile — adding a new provider requires changes in multiple places.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `CircularProgressIndicator` (indeterminate) | `CircularWavyProgressIndicator` or `LoadingIndicator` | Lines 201, 764 |
| `Button` / `OutlinedButton` (use-case chips) | `ToggleButton` / `TonalToggleButton` or `FilterChip` | `UseCaseChipRow` |
| `DropdownMenuItem` (in all dropdowns) | Expressive `DropdownMenuItem` | Multiple dropdowns |
| Plain `Button` / `FilledTonalButton` / `OutlinedButton` | Expressive overloads with `ButtonShapes` | Throughout |

---

## File 8: `HistoryScreen.kt`

### Part 1 — Issues

1. **`LazyColumn` has no height constraint — may overflow or not scroll**
   The `LazyColumn` at line 119 is inside a `Column(Modifier.fillMaxSize())` but has no `Modifier.weight(1f)`. Without a height constraint, `LazyColumn` measures to its intrinsic height and may overflow the parent without scrolling. Add `Modifier.weight(1f)` to fill remaining space.

2. **Alert dialog text references live `state.entries.size`**
   The "Delete all ${state.entries.size} recordings?" text reads from live state. If entries are deleted or updated while the dialog is open, the count changes dynamically. Minor UX issue — snapshot the count when opening the dialog.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `Button` ("Clear all" confirm) | Expressive `Button` with `ButtonShapes` | Line 59 |
| `FilledTonalButton` / `TextButton` | Expressive overloads | Throughout |
| `AssistChip` (disabled, badges) | Informational `Surface` badges | Lines 161-167 |
| `OutlinedCard` (history entries) | `SegmentedListItem` | `HistoryEntryCard` |

---

## File 9: `DictionaryScreen.kt`

### Part 1 — Issues

1. **`LazyColumn` missing `Modifier.weight(1f)` — same overflow issue as HistoryScreen**

2. **`EmptyState` passed `actionLabel = "Learn more"` but no onClick handler**
   Line 137: `EmptyState(... actionLabel = "Learn more")` provides a label but no action. If the `EmptyState` component renders a button for this label, it will be a non-functional button. Verify the `EmptyState` implementation or remove the label.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `Switch` (entry toggle) | Stay as `Switch` (no toggle button warranted) | Line 162 |
| `FilledTonalButton` / `TextButton` | Expressive overloads | Throughout |
| `OutlinedCard` (dictionary entries) | `SegmentedListItem` | Line 141 |

---

## File 10: `StatsScreen.kt`

### Part 1 — Issues

1. **No scrollable container — content can overflow**
   The outer `Column(Modifier.fillMaxSize().padding(...))` has no `verticalScroll()` modifier. If there are many top apps or the window is small, content overflows without scroll capability. Add `.verticalScroll(rememberScrollState())` or convert to `LazyColumn`.

2. **`Animatable` starts from 0 on initial composition, not from current state**
   `remember { Animatable(0f) }` always starts from 0. On screen re-entry (navigating away and back), the count animates from 0 again, which looks intentional as a "reveal" animation but could be jarring with large numbers. Consider using `Animatable(targetValue.toFloat())` as initial value to skip the animation on re-entry, or accept the current UX.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `FilledTonalButton` ("Refresh") | Expressive overload with `ButtonShapes` | Line 57 |
| `AssistChip` (disabled, badge) | Informational `Surface` badge | Line 108 |
| `ElevatedCard` (stat cards) | Stay as-is or add motion enhancement | `StatCard` |

---

## File 11: `UserInfoScreen.kt`

### Part 1 — Issues

1. **Save button enabled even when no fields have changed**
   `FilledTonalButton(... enabled = !state.isSaving)` is enabled whenever a save is not in progress, even if no fields were modified. This allows redundant saves. Compare current field values to persisted values.

2. **No input validation on email/phone fields**
   `ProfileField` for email and phone accepts arbitrary text. While these are for phrase expansion (not auth), basic format hints would improve UX.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `FilledTonalButton` ("Save profile") | Expressive overload with `ButtonShapes` | Line 93 |
| `TextButton` ("Dismiss") | Expressive overload | Line 118 |

---

## File 12: `OnboardingScreen.kt`

### Part 1 — Issues

1. **`LaunchedEffect(state.micSettingsUnavailable)` won't re-show snackbar**
   If `micSettingsUnavailable` stays `true` after the first trigger, the LaunchedEffect won't re-fire because the key hasn't changed. The user won't see the snackbar again if they re-tap "Open Sound Settings." Use a unique event ID (like `modelsFetchErrorId`) instead of a boolean flag.

2. **Broad exception catch on focus request**
   Line 436: `try { apiFocusRequester.requestFocus() } catch (_: Exception) { }` swallows all exceptions, including `OutOfMemoryError` (caught because Kotlin's `Exception` includes many types). Narrow to `catch (_: IllegalStateException)`.

3. **`rememberInfiniteTransition` allocated for every `StepSection` instance (4 total)**
   Each step creates an infinite transition for the glow animation, even locked/complete steps where `glowAlpha` targets 0. Four perpetually-ticking transitions are wasteful. Gate the transition behind `if (stepState == StepState.ACTIVE)`.

4. **`LinearProgressIndicator` — standard variant, not wavy**
   Uses `LinearProgressIndicator` at line 245. Inconsistent with the rest of the codebase's expressive direction.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `LinearProgressIndicator` (progress bar) | `LinearWavyProgressIndicator` | Line 245 |
| `CircularProgressIndicator` (multiple) | `CircularWavyProgressIndicator` or `LoadingIndicator` | Lines 376, 408, 475 |
| `Button` / `OutlinedButton` (use-case chips) | `ToggleButton` / `TonalToggleButton` | `UseCaseChipRow` |
| `Button` ("Enter OpenYap" CTA) | Expressive `Button` with large `ButtonShapes` | Line 538 |
| `Button` / `OutlinedButton` / `FilledTonalButton` | Expressive overloads with `ButtonShapes` | Throughout |

---

## File 13: `CustomizationScreen.kt`

### Part 1 — Issues

1. **`remember(appTones, appPrompts)` may recompute unnecessarily**
   `Map` equality is by reference. If the ViewModel emits new `Map` instances with identical content on every state emission, the `remember` block re-runs each time. Use `derivedStateOf` or keys that are structurally stable.

2. **`LazyColumn` without height constraint — same overflow issue**
   The `LazyColumn` at line 110 is inside a `Column(fillMaxSize())` without `weight(1f)`.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `FilterChip` (tone selection) | Already correct component — optionally upgrade to expressive `FilterChip` | `AppCustomizationCard` |
| `FilledTonalButton` / `TextButton` | Expressive overloads with `ButtonShapes` | Throughout |
| `OutlinedCard` (app entries) | `SegmentedListItem` | Line 160 |

---

## File 14: `AppTheme.kt`

### Part 1 — Issues

1. **`LightColorScheme` is missing surface container tokens**
   `DarkColorScheme` explicitly sets `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`. The `LightColorScheme` omits all of these, falling back to M3 defaults. Since the app uses `surfaceContainerHigh` in `ShellContentPane` (AppShell line 613), the light-mode surface color may not match the design intent.

2. **Reduced-motion initial value is `true` (safe but causes animation skip on first frame)**
   `produceState(initialValue = true)` means the very first frame after app launch uses reduced motion. After the `platformPrefersReducedMotion()` call completes on `Dispatchers.Default`, it switches to the actual value. Users who prefer full motion will see a brief non-animated first frame. Consider making the initial value `false` if most users have full motion, or accept the current safe default.

3. **`platformPrefersReducedMotion()` runs on `Dispatchers.Default` instead of `Main`**
   If this function accesses platform UI APIs (Windows accessibility settings), it should run on `Dispatchers.Main` or `Dispatchers.IO`. `Dispatchers.Default` is meant for CPU-bound work. On most platforms this is a simple property read and works fine, but it's technically incorrect threading.

### Part 2 — M3 Expressive Candidates

| Current | Recommended Replacement | Location |
|---------|------------------------|----------|
| `MaterialExpressiveTheme` | Already M3 Expressive ✓ | Line 260 |
| `MotionScheme.expressive()` | Already M3 Expressive ✓ | Line 258 |

No migration needed — this file is already fully M3 Expressive.

---

## File 15: `ReducedMotion.kt`

### Part 1 — Issues

1. **`staticCompositionLocalOf { false }` default doesn't match `AppTheme` initial**
   `LocalReducedMotion` defaults to `false` (motion enabled), but `AppTheme`'s `produceState` starts with `true` (motion disabled). If any composable reads `LocalReducedMotion` before `AppTheme` provides a value (which shouldn't happen in normal flow), it gets the opposite assumption. Not a practical bug, but a documentation inconsistency.

### Part 2 — M3 Expressive Candidates

None — utility file with no UI components.

---

## Summary of Critical Issues

| Severity | File | Issue |
|----------|------|-------|
| **High** | `AppShell.kt` | `PrimaryShellAction` fake disabled state breaks accessibility |
| **High** | `RecordingOverlay.kt` | Side effect during composition (`lastFlashMessage`) |
| **High** | `HistoryScreen.kt` | `LazyColumn` missing `weight(1f)` — content overflow |
| **High** | `DictionaryScreen.kt` | `LazyColumn` missing `weight(1f)` — content overflow |
| **High** | `CustomizationScreen.kt` | `LazyColumn` missing `weight(1f)` — content overflow |
| **Medium** | `StatsScreen.kt` | No scroll container — content can overflow |
| **Medium** | `SettingsScreen.kt` | Slider fires event per pixel — excessive I/O |
| **Medium** | `RecordingOverlay.kt` | `ErrorBar`/`FlashMessageRow` use theme colors in theme-independent overlay |
| **Medium** | `OnboardingScreen.kt` | `micSettingsUnavailable` snackbar won't re-show |
| **Medium** | `HomeHeroCard.kt` | `idlePulseAlpha` infinite transition runs when not visible |
| **Low** | `AppTheme.kt` | `LightColorScheme` missing surface container tokens |
| **Low** | `SettingsScreen.kt` | Shadowed `reducedMotion` variable |
| **Low** | `DictionaryScreen.kt` | `EmptyState` has action label but no handler |
| **Low** | `UserInfoScreen.kt` | Save button always enabled even without changes |

## Summary of M3 Expressive Migration Candidates

| Migration | Files Affected | Impact |
|-----------|---------------|--------|
| `CircularProgressIndicator` → `CircularWavyProgressIndicator` / `LoadingIndicator` | AppShell, SettingsScreen, OnboardingScreen | 5+ instances |
| `LinearProgressIndicator` → `LinearWavyProgressIndicator` | OnboardingScreen | 1 instance |
| Record/stop FAB → `ToggleFloatingActionButton` | AppShell (`PrimaryShellAction`), HomeHeroCard | 2 instances |
| `Button`/`OutlinedButton` toggle patterns → `ToggleButton` / `TonalToggleButton` | SettingsScreen, OnboardingScreen (UseCaseChipRow) | 2 chip rows |
| Plain `Button` / `FilledTonalButton` → Expressive overloads with `ButtonShapes` | All screen files | 15+ instances |
| `IconButton` → Expressive `IconButton` with `IconButtonShapes` | AppRail, AppShell | 2 instances |
| `DropdownMenu`/`DropdownMenuItem` → `DropdownMenuGroup` + expressive `DropdownMenuItem` | AppShell, SettingsScreen | 6+ dropdowns |
| `OutlinedCard` list rows → `SegmentedListItem` | HistoryScreen, DictionaryScreen, CustomizationScreen | 3 lists |
| `MaterialExpressiveTheme` | AppTheme.kt | Already migrated ✓ |
| `WideNavigationRail` | AppRail.kt | Already migrated ✓ |
