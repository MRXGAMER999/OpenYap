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
    version = 4,
)
abstract class OpenYapDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun dictionaryEntryDao(): DictionaryEntryDao
    abstract fun recordingEntryDao(): RecordingEntryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appToneDao(): AppToneDao
    abstract fun appPromptDao(): AppPromptDao

    suspend fun deleteAllData() {
        appSettingsDao().deleteAll()
        recordingEntryDao().deleteAll()
        dictionaryEntryDao().deleteAll()
        userProfileDao().deleteAll()
        appToneDao().deleteAll()
        appPromptDao().deleteAll()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE app_settings ADD COLUMN primaryUseCase TEXT NOT NULL DEFAULT 'GENERAL'")
        connection.execSQL("ALTER TABLE app_settings ADD COLUMN useCaseContext TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE app_settings ADD COLUMN soundFeedbackVolume REAL NOT NULL DEFAULT 0.5")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE app_settings ADD COLUMN whisperLanguage TEXT NOT NULL DEFAULT 'en'")
    }
}
