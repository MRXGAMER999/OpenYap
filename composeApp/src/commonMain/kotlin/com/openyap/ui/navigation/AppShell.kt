package com.openyap.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.openyap.model.RecordingState
import com.openyap.ui.component.RecordingIndicator
import com.openyap.ui.screen.CustomizationScreen
import com.openyap.ui.screen.DictionaryScreen
import com.openyap.ui.screen.HistoryScreen
import com.openyap.ui.screen.OnboardingScreen
import com.openyap.ui.screen.SettingsScreen
import com.openyap.ui.screen.StatsScreen
import com.openyap.ui.screen.UserInfoScreen
import com.openyap.ui.theme.Spacing
import com.openyap.ui.theme.reducedMotionEnabled
import com.openyap.viewmodel.AppCustomizationEvent
import com.openyap.viewmodel.AppCustomizationViewModel
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingUiState
import com.openyap.viewmodel.OnboardingViewModel
import com.openyap.viewmodel.RecordingEvent
import com.openyap.viewmodel.RecordingUiState
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsUiState
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppShell(
    backStack: SnapshotStateList<Route>,
    recordingViewModel: RecordingViewModel,
    settingsViewModel: SettingsViewModel,
    onRecordingEvent: (RecordingEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
    onOnboardingEvent: (OnboardingEvent) -> Unit,
    onCopyToClipboard: (String) -> Unit,
) {
    val recordingState by recordingViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val onboardingViewModel = koinInject<OnboardingViewModel>()
    val onboardingState by onboardingViewModel.state.collectAsState()
    val reducedMotion = reducedMotionEnabled()
    val snackbarHostState = remember { SnackbarHostState() }

    if (!onboardingState.isLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "OpenYap",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Spacing.sm))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }

    if (!onboardingState.isComplete) {
        OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)
        return
    }

    val currentRoute = backStack.lastOrNull() ?: Route.Home
    val isPrimaryActionEnabled = remember(recordingState.recordingState) {
        recordingState.recordingState is RecordingState.Idle ||
            recordingState.recordingState is RecordingState.Success ||
            recordingState.recordingState is RecordingState.Error ||
            recordingState.recordingState is RecordingState.Recording
    }
    val primaryActionStopsRecording = recordingState.recordingState is RecordingState.Recording
    val isRecordingActive = recordingState.recordingState !is RecordingState.Idle
    val contentVisibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val menuFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val destinationsFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }
    val timeLabel = rememberSystemTimeLabel()

    fun navigateTo(route: Route) {
        backStack.clear()
        backStack.add(route)
        menuExpanded = false
    }

    val shellLayout = rememberAdaptiveShellLayout()
    val motionScheme = MaterialTheme.motionScheme
    val railState = rememberWideNavigationRailState(
        if (shellLayout.isWideRail) WideNavigationRailValue.Expanded
        else WideNavigationRailValue.Collapsed,
    )
    LaunchedEffect(shellLayout.isWideRail) {
        if (shellLayout.isWideRail) railState.expand() else railState.collapse()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md, top = Spacing.sm),
    ) {

        AnimatedContent(
            targetState = shellLayout,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                } else {
                    (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                        scaleIn(
                            initialScale = 0.99f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                        )) togetherWith fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                }
            },
            label = "shellLayout",
        ) { activeLayout ->
            if (activeLayout.showsRail) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ShellRailPane(
                        currentRoute = currentRoute,
                        layout = activeLayout,
                        railState = railState,
                        onRouteSelected = ::navigateTo,
                        onPrimaryAction = { onRecordingEvent(RecordingEvent.ToggleRecording) },
                        primaryActionEnabled = isPrimaryActionEnabled,
                        primaryActionStopsRecording = primaryActionStopsRecording,
                        menuFocusRequester = menuFocusRequester,
                        primaryActionFocusRequester = primaryActionFocusRequester,
                        destinationsFocusRequester = destinationsFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )

                    ShellContentPane(
                        modifier = Modifier.weight(1f),
                        backStack = backStack,
                        recordingState = recordingState,
                        settingsState = settingsState,
                        snackbarHostState = snackbarHostState,
                        onboardingState = onboardingState,
                        onRecordingEvent = onRecordingEvent,
                        onSettingsEvent = onSettingsEvent,
                        onOnboardingEvent = onOnboardingEvent,
                        onCopyToClipboard = onCopyToClipboard,
                        onNavigateToSettings = { navigateTo(Route.Settings) },
                        reducedMotion = reducedMotion,
                        contentVisibility = contentVisibility,
                        contentFocusRequester = contentFocusRequester,
                        previousFocusRequester = destinationsFocusRequester,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ShellTopBar(
                        currentRoute = currentRoute,
                        timeLabel = timeLabel,
                        menuExpanded = menuExpanded,
                        onMenuExpandedChange = { menuExpanded = it },
                        onRouteSelected = ::navigateTo,
                        onPrimaryAction = { onRecordingEvent(RecordingEvent.ToggleRecording) },
                        primaryActionEnabled = isPrimaryActionEnabled,
                        primaryActionStopsRecording = primaryActionStopsRecording,
                        menuFocusRequester = menuFocusRequester,
                        primaryActionFocusRequester = primaryActionFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )

                    ShellContentPane(
                        modifier = Modifier.weight(1f),
                        backStack = backStack,
                        recordingState = recordingState,
                        settingsState = settingsState,
                        snackbarHostState = snackbarHostState,
                        onboardingState = onboardingState,
                        onRecordingEvent = onRecordingEvent,
                        onSettingsEvent = onSettingsEvent,
                        onOnboardingEvent = onOnboardingEvent,
                        onCopyToClipboard = onCopyToClipboard,
                        onNavigateToSettings = { navigateTo(Route.Settings) },
                        reducedMotion = reducedMotion,
                        contentVisibility = contentVisibility,
                        contentFocusRequester = contentFocusRequester,
                        previousFocusRequester = primaryActionFocusRequester,
                    )
                }
            }
        }

        if (isRecordingActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.md)
                    .clearAndSetSemantics {
                        contentDescription = recordingIndicatorDescription(recordingState.recordingState)
                        liveRegion = LiveRegionMode.Polite
                        traversalIndex = 3f
                    },
            ) {
                RecordingIndicator(
                    recordingState = recordingState.recordingState,
                    amplitude = recordingState.amplitude,
                    onCancel = { onRecordingEvent(RecordingEvent.CancelRecording) },
                    onErrorDismissed = { onRecordingEvent(RecordingEvent.DismissError) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellRailPane(
    currentRoute: Route,
    layout: AppShellLayout,
    railState: androidx.compose.material3.WideNavigationRailState,
    onRouteSelected: (Route) -> Unit,
    onPrimaryAction: () -> Unit,
    primaryActionEnabled: Boolean,
    primaryActionStopsRecording: Boolean,
    menuFocusRequester: FocusRequester,
    primaryActionFocusRequester: FocusRequester,
    destinationsFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
) {
    if (layout.railTreatment == null) return
    val scope = rememberCoroutineScope()
    val isExpanded = railState.targetValue == WideNavigationRailValue.Expanded

    AppRail(
        destinations = primaryRailRoutes,
        currentRoute = currentRoute,
        onRouteSelected = onRouteSelected,
        railState = railState,
        modifier = Modifier
            .fillMaxHeight()
            .focusRequester(destinationsFocusRequester)
            .focusProperties {
                previous = primaryActionFocusRequester
                next = contentFocusRequester
            }
            .semantics { traversalIndex = 2f },
        header = {
            Column(
                horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(
                    start = if (isExpanded) Spacing.md else 16.dp,
                    end = if (isExpanded) 0.dp else 16.dp,
                )
            ) {
                RailToggleButton(
                    isExpanded = isExpanded,
                    onToggle = {
                        scope.launch {
                            if (isExpanded) railState.collapse() else railState.expand()
                        }
                    },
                    modifier = Modifier
                        .focusRequester(menuFocusRequester)
                        .focusProperties { next = primaryActionFocusRequester }
                        .semantics {
                            role = Role.Button
                            contentDescription = if (isExpanded) "Collapse navigation" else "Expand navigation"
                            traversalIndex = 0f
                        },
                )

                PrimaryShellAction(
                    expanded = isExpanded,
                    enabled = primaryActionEnabled,
                    stopsRecording = primaryActionStopsRecording,
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .focusRequester(primaryActionFocusRequester)
                        .focusProperties {
                            previous = menuFocusRequester
                            next = destinationsFocusRequester
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = primaryActionContentDescription(primaryActionStopsRecording)
                            traversalIndex = 1f
                        },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellTopBar(
    currentRoute: Route,
    timeLabel: String,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onRouteSelected: (Route) -> Unit,
    onPrimaryAction: () -> Unit,
    primaryActionEnabled: Boolean,
    primaryActionStopsRecording: Boolean,
    menuFocusRequester: FocusRequester,
    primaryActionFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OpenYap",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryShellAction(
                expanded = true,
                enabled = primaryActionEnabled,
                stopsRecording = primaryActionStopsRecording,
                onClick = onPrimaryAction,
                modifier = Modifier
                    .focusRequester(primaryActionFocusRequester)
                    .focusProperties {
                        previous = menuFocusRequester
                        next = contentFocusRequester
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = primaryActionContentDescription(primaryActionStopsRecording)
                        traversalIndex = 1f
                    },
            )

            Box {
                IconButton(
                    onClick = { onMenuExpandedChange(!menuExpanded) },
                    modifier = Modifier
                        .focusRequester(menuFocusRequester)
                        .focusProperties { next = primaryActionFocusRequester }
                        .semantics {
                            role = Role.Button
                            contentDescription = if (menuExpanded) {
                                "Close navigation menu"
                            } else {
                                "Open navigation menu"
                            }
                            traversalIndex = 0f
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                    )
                }

                ShellRouteMenu(
                    expanded = menuExpanded,
                    currentRoute = currentRoute,
                    onDismiss = { onMenuExpandedChange(false) },
                    onRouteSelected = onRouteSelected,
                )
            }
        }
    }
}

@Composable
private fun ShellRouteMenu(
    expanded: Boolean,
    currentRoute: Route,
    onDismiss: () -> Unit,
    onRouteSelected: (Route) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        primaryRailRoutes.forEachIndexed { index, destination ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (currentRoute == destination.route) {
                            "${destination.label} - current"
                        } else {
                            destination.label
                        },
                    )
                },
                onClick = {
                    onRouteSelected(destination.route)
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (currentRoute == destination.route) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = null,
                    )
                },
                modifier = Modifier.semantics { traversalIndex = 2f + index.toFloat() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrimaryShellAction(
    expanded: Boolean,
    enabled: Boolean,
    stopsRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = if (stopsRecording) "Stop recording" else "Record thought"
    val actionIcon = if (stopsRecording) Icons.Default.Stop else Icons.Default.Mic

    SmallExtendedFloatingActionButton(
        text = { Text(actionLabel, style = MaterialTheme.typography.labelLargeEmphasized) },
        icon = { Icon(actionIcon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        shape = FloatingActionButtonDefaults.mediumExtendedFabShape,
        onClick = { if (enabled) onClick() },
        expanded = expanded,
        modifier = modifier.then(if (!enabled) Modifier.alpha(0.38f) else Modifier),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

private fun primaryActionContentDescription(stopsRecording: Boolean): String =
    if (stopsRecording) "Stop recording" else "Record thought"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellContentPane(
    modifier: Modifier,
    backStack: MutableList<Route>,
    recordingState: RecordingUiState,
    settingsState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onboardingState: OnboardingUiState,
    onRecordingEvent: (RecordingEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
    onOnboardingEvent: (OnboardingEvent) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    reducedMotion: Boolean,
    contentVisibility: MutableTransitionState<Boolean>,
    contentFocusRequester: FocusRequester,
    previousFocusRequester: FocusRequester,
) {
    val motionScheme = MaterialTheme.motionScheme
    val enterTransition = if (reducedMotion) {
        fadeIn(animationSpec = motionScheme.fastEffectsSpec())
    } else {
        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
            scaleIn(
                initialScale = 0.98f,
                animationSpec = motionScheme.defaultSpatialSpec(),
            )
    }

    AnimatedVisibility(
        visibleState = contentVisibility,
        modifier = modifier,
        enter = enterTransition,
        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester)
                .focusProperties { previous = previousFocusRequester },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Transparent,
            ) { innerPadding ->
                NavDisplay(
                    modifier = Modifier.padding(innerPadding),
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    },
                    entryProvider = entryProvider {
                        entry<Route.Home> {
                            HomeContent(
                                state = recordingState,
                                settingsState = settingsState,
                                onNavigateToSettings = onNavigateToSettings,
                                onEvent = onRecordingEvent,
                                snackbarHostState = snackbarHostState,
                                reducedMotion = reducedMotion,
                            )
                        }
                        entry<Route.History> {
                            val vm = koinInject<HistoryViewModel>()
                            val state by vm.state.collectAsState()
                            HistoryScreen(state, vm::onEvent, onCopyToClipboard)
                        }
                        entry<Route.Dictionary> {
                            val vm = koinInject<DictionaryViewModel>()
                            val state by vm.state.collectAsState()
                            DictionaryScreen(
                                state = state,
                                isDictionaryEnabled = settingsState.dictionaryEnabled,
                                onEvent = vm::onEvent,
                            )
                        }
                        entry<Route.UserInfo> {
                            val vm = koinInject<UserProfileViewModel>()
                            val state by vm.state.collectAsState()
                            UserInfoScreen(state, vm::onEvent)
                        }
                        entry<Route.Stats> {
                            val vm = koinInject<StatsViewModel>()
                            val state by vm.state.collectAsState()
                            StatsScreen(state, onRefresh = vm::refresh)
                        }
                        entry<Route.Customization> {
                            val vm = koinInject<AppCustomizationViewModel>()
                            val state by vm.state.collectAsState()
                            CustomizationScreen(
                                state.appTones,
                                state.appPrompts,
                                onSaveTone = { app, tone ->
                                    vm.onEvent(AppCustomizationEvent.SaveTone(app, tone))
                                },
                                onSavePrompt = { app, prompt ->
                                    vm.onEvent(AppCustomizationEvent.SavePrompt(app, prompt))
                                },
                                onRemoveApp = { app ->
                                    vm.onEvent(AppCustomizationEvent.RemoveApp(app))
                                },
                            )
                        }
                        entry<Route.Settings> {
                            SettingsScreen(settingsState, onSettingsEvent)
                        }
                        entry<Route.Onboarding> {
                            OnboardingScreen(
                                state = onboardingState,
                                onEvent = onOnboardingEvent,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: RecordingUiState,
    settingsState: SettingsUiState,
    onNavigateToSettings: () -> Unit,
    onEvent: (RecordingEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    reducedMotion: Boolean,
) {
    val errorMessage = state.error
    val statusTitle = when (state.recordingState) {
        is RecordingState.Idle -> "Ready to speak"
        is RecordingState.Recording -> "Capturing your thought"
        is RecordingState.Processing -> "Polishing your words"
        is RecordingState.Success -> "Pasted with style"
        is RecordingState.Error -> "Need one more try"
    }
    val statusBody = when (state.recordingState) {
        is RecordingState.Idle -> {
            "Press ${settingsState.hotkeyLabel} or use the button below to record and paste polished text instantly."
        }
        is RecordingState.Recording -> "Listening live. Release when you're done speaking."
        is RecordingState.Processing -> "Gemini is transcribing, refining, and preparing the final paste."
        is RecordingState.Success -> "Your latest response has already been pasted into the active app."
        is RecordingState.Error -> errorMessage ?: "Something interrupted the flow."
    }
    var latestResultText by remember(state.lastResultText) { mutableStateOf(state.lastResultText) }

    LaunchedEffect(state.recordingState) {
        when (val recordingValue = state.recordingState) {
            is RecordingState.Recording -> {
                if (recordingValue.durationSeconds == 0) {
                    latestResultText = null
                }
            }

            is RecordingState.Success -> latestResultText = recordingValue.text
            else -> Unit
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, actionLabel = "Dismiss")
            onEvent(RecordingEvent.DismissError)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HomeHeroCard(
                state = state,
                settingsState = settingsState,
                statusTitle = statusTitle,
                statusBody = statusBody,
                latestResultText = latestResultText,
                onNavigateToSettings = onNavigateToSettings,
                onEvent = onEvent,
                reducedMotion = reducedMotion,
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun rememberSystemTimeLabel(): String {
    return produceState(initialValue = formatSystemTime(Clock.System.now())) {
        while (true) {
            val now = Clock.System.now()
            value = formatSystemTime(now)
            val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val delayMillis = ((60 - localNow.second) * 1000L) - (localNow.nanosecond / 1_000_000L)
            delay(delayMillis.coerceAtLeast(1L))
        }
    }.value
}

@OptIn(ExperimentalTime::class)
private fun formatSystemTime(now: kotlin.time.Instant): String {
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour24 = local.hour
    val minute = local.minute.toString().padStart(2, '0')
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val meridiem = if (hour24 >= 12) "PM" else "AM"
    return "$hour12:$minute $meridiem"
}

private fun recordingIndicatorDescription(recordingState: RecordingState): String {
    return when (recordingState) {
        is RecordingState.Recording -> "Recording in progress"
        is RecordingState.Processing -> "Processing recording"
        is RecordingState.Success -> "Recording pasted successfully"
        is RecordingState.Error -> recordingState.message
        is RecordingState.Idle -> ""
    }
}
