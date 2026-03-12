package com.openyap.platform

interface SecureStorage {
    suspend fun save(key: String, value: String)
    suspend fun load(key: String): String?
    suspend fun delete(key: String)
}
