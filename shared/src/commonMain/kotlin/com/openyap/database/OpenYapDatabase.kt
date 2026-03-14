package com.openyap.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        AppSettingsEntity::class,
        DictionaryEntryEntity::class,
        RecordingEntryEntity::class,
        UserProfileEntity::class,
        AppToneEntity::class,
        AppPromptEntity::class,
    ],
    version = 1,
)
abstract class OpenYapDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun dictionaryEntryDao(): DictionaryEntryDao
    abstract fun recordingEntryDao(): RecordingEntryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appToneDao(): AppToneDao
    abstract fun appPromptDao(): AppPromptDao
}
