package com.openyap.di

import com.openyap.database.OpenYapDatabase
import com.openyap.database.createOpenYapDatabase
import com.openyap.platform.AppDataResetter
import com.openyap.platform.AudioFeedbackPlayerImpl
import com.openyap.platform.AudioFeedbackService
import com.openyap.platform.AudioPipelineConfig
import com.openyap.platform.AudioRecorder
import com.openyap.platform.ForegroundAppDetector
import com.openyap.platform.HotkeyDisplayFormatter
import com.openyap.platform.HotkeyManager
import com.openyap.platform.FileOperations
import com.openyap.platform.HttpClientFactory
import com.openyap.platform.JvmAppDataResetter
import com.openyap.platform.JvmAudioRecorder
import com.openyap.platform.JvmFileOperations
import com.openyap.platform.NativeAudioBridge
import com.openyap.platform.NativeAudioRecorder
import com.openyap.platform.PasteAutomation
import com.openyap.platform.PermissionManager
import com.openyap.platform.PlatformInit
import com.openyap.platform.SecureStorage
import com.openyap.platform.StartupManager
import com.openyap.platform.WindowsCredentialStorage
import com.openyap.platform.WindowsForegroundAppDetector
import com.openyap.platform.WindowsHotkeyDisplayFormatter
import com.openyap.platform.WindowsHotkeyManager
import com.openyap.platform.WindowsPasteAutomation
import com.openyap.platform.WindowsPermissionManager
import com.openyap.platform.WindowsStartupManager
import com.openyap.repository.DictionaryRepository
import com.openyap.repository.HistoryRepository
import com.openyap.repository.RoomDictionaryRepository
import com.openyap.repository.RoomHistoryRepository
import com.openyap.repository.RoomSettingsRepository
import com.openyap.repository.RoomUserProfileRepository
import com.openyap.repository.SettingsRepository
import com.openyap.repository.UserProfileRepository
import com.openyap.service.GeminiClient
import com.openyap.service.GroqLLMClient
import com.openyap.service.GroqWhisperClient
import com.openyap.viewmodel.AudioFeedbackPlayer
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.nio.file.Path
import java.util.logging.Logger

@Module
class PlatformModule {

    companion object {
        private val logger = Logger.getLogger(PlatformModule::class.java.name)
    }

    @Single
    fun provideSecureStorage(): SecureStorage = WindowsCredentialStorage()

    @Single
    fun provideDatabase(): OpenYapDatabase {
        val dbPath = PlatformInit.dataDir.resolve("openyap.db").toString()
        return createOpenYapDatabase(dbPath)
    }

    @Single
    fun provideSettingsRepository(db: OpenYapDatabase, ss: SecureStorage): SettingsRepository =
        RoomSettingsRepository(db, ss)

    @Single
    fun provideHistoryRepository(db: OpenYapDatabase): HistoryRepository =
        RoomHistoryRepository(db)

    @Single
    fun provideDictionaryRepository(db: OpenYapDatabase): DictionaryRepository =
        RoomDictionaryRepository(db)

    @Single
    fun provideUserProfileRepository(db: OpenYapDatabase): UserProfileRepository =
        RoomUserProfileRepository(db)

    @Single
    fun provideHotkeyManager(): HotkeyManager = WindowsHotkeyManager()

    @Single
    fun provideAudioPipelineConfig(): AudioPipelineConfig {
        val nativeAudio = NativeAudioBridge.instance
        return if (nativeAudio != null) {
            logger.info("Native audio pipeline available; NativeAudioBridge.instance present, using NativeAudioRecorder")
            AudioPipelineConfig(
                audioRecorder = NativeAudioRecorder(nativeAudio),
                audioMimeType = "audio/wav",
                audioFileExtension = ".wav",
            )
        } else {
            logger.info("Native audio pipeline unavailable; NativeAudioBridge.instance absent, using JvmAudioRecorder fallback")
            AudioPipelineConfig(
                audioRecorder = JvmAudioRecorder(),
                audioMimeType = "audio/wav",
                audioFileExtension = ".wav",
            )
        }
    }

    @Single
    fun provideAudioRecorder(config: AudioPipelineConfig): AudioRecorder = config.audioRecorder

    @Single
    @Named("audioMimeType")
    fun provideAudioMimeType(config: AudioPipelineConfig): String = config.audioMimeType

    @Single
    @Named("audioFileExtension")
    fun provideAudioFileExtension(config: AudioPipelineConfig): String = config.audioFileExtension

    @Single
    fun providePasteAutomation(): PasteAutomation = WindowsPasteAutomation()

    @Single
    fun provideForegroundAppDetector(): ForegroundAppDetector = WindowsForegroundAppDetector()

    @Single
    fun providePermissionManager(): PermissionManager = WindowsPermissionManager()

    @Single
    fun provideStartupManager(): StartupManager = WindowsStartupManager()

    @Single
    fun provideGeminiClient(): GeminiClient = HttpClientFactory.createGeminiClient()

    @Single
    fun provideGroqWhisperClient(): GroqWhisperClient = HttpClientFactory.createGroqWhisperClient()

    @Single
    fun provideGroqLLMClient(): GroqLLMClient = HttpClientFactory.createGroqLLMClient()

    @Single
    fun provideHotkeyDisplayFormatter(): HotkeyDisplayFormatter = WindowsHotkeyDisplayFormatter()

    @Single
    fun provideAudioFeedbackService(): AudioFeedbackService = AudioFeedbackService()

    @Single
    @Named("dataDir")
    fun provideDataDir(): Path = PlatformInit.dataDir

    @Single
    @Named("tempDir")
    fun provideTempDir(): Path = PlatformInit.tempDir

    @Single
    fun provideFileOperations(): FileOperations = JvmFileOperations()

    @Single
    fun provideAudioFeedbackPlayer(service: AudioFeedbackService): AudioFeedbackPlayer =
        AudioFeedbackPlayerImpl(service)

    @Single
    fun provideAppDataResetter(
        ss: SecureStorage,
        db: OpenYapDatabase,
        @Named("dataDir") dataDir: Path,
        @Named("tempDir") tempDir: Path,
    ): AppDataResetter = JvmAppDataResetter(ss, db, dataDir, tempDir)
}
