package com.openyap.platform

interface AppDataResetter {
    suspend fun reset()
}

class NoOpAppDataResetter : AppDataResetter {
    override suspend fun reset() {}
}
