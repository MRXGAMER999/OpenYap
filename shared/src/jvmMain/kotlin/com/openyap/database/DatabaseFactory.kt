package com.openyap.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun createOpenYapDatabase(dbFilePath: String): OpenYapDatabase {
    return Room.databaseBuilder<OpenYapDatabase>(dbFilePath)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .build()
}
