package com.openyap.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val customAliasesJson: String = "{}",
)
