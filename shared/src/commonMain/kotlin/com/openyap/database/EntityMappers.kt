package com.openyap.database

import com.openyap.model.AppSettings
import com.openyap.model.DictionaryEntry
import com.openyap.model.EntrySource
import com.openyap.model.HotkeyConfig
import com.openyap.model.PrimaryUseCase
import com.openyap.model.RecordingEntry
import com.openyap.model.TranscriptionProvider
import com.openyap.model.UserProfile
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

// ── AppSettings ─────────────────────────────────────────────────────────────

fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    id = 1,
    geminiModel = geminiModel,
    transcriptionProvider = transcriptionProvider.name,
    groqModel = groqModel,
    groqLLMModel = groqLLMModel,
    hotkeyConfigJson = json.encodeToString(hotkeyConfig),
    genZEnabled = genZEnabled,
    phraseExpansionEnabled = phraseExpansionEnabled,
    dictionaryEnabled = dictionaryEnabled,
    dismissedUpdateVersion = dismissedUpdateVersion,
    onboardingCompleted = onboardingCompleted,
    audioFeedbackEnabled = audioFeedbackEnabled,
    soundFeedbackVolume = soundFeedbackVolume,
    startMinimized = startMinimized,
    launchOnStartup = launchOnStartup,
    audioDeviceId = audioDeviceId,
    primaryUseCase = primaryUseCase.name,
    useCaseContext = useCaseContext,
    whisperLanguage = whisperLanguage,
)

fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
    geminiModel = geminiModel,
    transcriptionProvider = try {
        TranscriptionProvider.valueOf(transcriptionProvider)
    } catch (_: Exception) {
        TranscriptionProvider.GEMINI
    },
    groqModel = groqModel,
    groqLLMModel = groqLLMModel,
    hotkeyConfig = try {
        json.decodeFromString<HotkeyConfig>(hotkeyConfigJson)
    } catch (_: Exception) {
        HotkeyConfig()
    },
    genZEnabled = genZEnabled,
    phraseExpansionEnabled = phraseExpansionEnabled,
    dictionaryEnabled = dictionaryEnabled,
    dismissedUpdateVersion = dismissedUpdateVersion,
    onboardingCompleted = onboardingCompleted,
    audioFeedbackEnabled = audioFeedbackEnabled,
    soundFeedbackVolume = soundFeedbackVolume,
    startMinimized = startMinimized,
    launchOnStartup = launchOnStartup,
    audioDeviceId = audioDeviceId,
    primaryUseCase = try {
        PrimaryUseCase.valueOf(primaryUseCase)
    } catch (_: Exception) {
        PrimaryUseCase.GENERAL
    },
    useCaseContext = useCaseContext,
    whisperLanguage = whisperLanguage,
)

// ── DictionaryEntry ─────────────────────────────────────────────────────────

fun DictionaryEntry.toEntity(): DictionaryEntryEntity = DictionaryEntryEntity(
    id = id,
    original = original,
    replacement = replacement,
    isEnabled = isEnabled,
    frequency = frequency,
    source = source.name,
)

fun DictionaryEntryEntity.toDomain(): DictionaryEntry = DictionaryEntry(
    id = id,
    original = original,
    replacement = replacement,
    isEnabled = isEnabled,
    frequency = frequency,
    source = try {
        EntrySource.valueOf(source)
    } catch (_: Exception) {
        EntrySource.AUTO
    },
)

// ── RecordingEntry ──────────────────────────────────────────────────────────

fun RecordingEntry.toEntity(): RecordingEntryEntity = RecordingEntryEntity(
    id = id,
    recordedAtMillis = recordedAt.toEpochMilliseconds(),
    durationSeconds = durationSeconds,
    response = response,
    targetApp = targetApp,
    model = model,
)

fun RecordingEntryEntity.toDomain(): RecordingEntry = RecordingEntry(
    id = id,
    recordedAt = Instant.fromEpochMilliseconds(recordedAtMillis),
    durationSeconds = durationSeconds,
    response = response,
    targetApp = targetApp,
    model = model,
)

// ── UserProfile ─────────────────────────────────────────────────────────────

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    id = 1,
    name = name,
    email = email,
    phone = phone,
    jobTitle = jobTitle,
    company = company,
    customAliasesJson = json.encodeToString(customAliases),
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    name = name,
    email = email,
    phone = phone,
    jobTitle = jobTitle,
    company = company,
    customAliases = try {
        json.decodeFromString<Map<String, String>>(customAliasesJson)
    } catch (_: Exception) {
        emptyMap()
    },
)
