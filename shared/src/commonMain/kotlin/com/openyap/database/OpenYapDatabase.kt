package com.openyap.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        AppSettingsEntity::class,
        DictionaryEntryEntity::class,
        RecordingEntryEntity::class,
        UserProfileEntity::class,
        AppToneEntity::class,
        AppPromptEntity::class,
    ],
    version = 2,
)
abstract class OpenYapDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun dictionaryEntryDao(): DictionaryEntryDao
    abstract fun recordingEntryDao(): RecordingEntryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appToneDao(): AppToneDao
    abstract fun appPromptDao(): AppPromptDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE app_settings ADD COLUMN primaryUseCase TEXT NOT NULL DEFAULT 'GENERAL'")
        connection.execSQL("ALTER TABLE app_settings ADD COLUMN useCaseContext TEXT NOT NULL DEFAULT ''")
    }
}
