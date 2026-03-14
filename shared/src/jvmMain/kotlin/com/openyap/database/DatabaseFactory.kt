package com.openyap.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun createOpenYapDatabase(dbFilePath: String): OpenYapDatabase {
    return Room.databaseBuilder<OpenYapDatabase>(dbFilePath)
        .setDriver(BundledSQLiteDriver())
        .build()
}
