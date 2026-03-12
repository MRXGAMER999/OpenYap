package com.openyap.platform

import com.openyap.model.InstalledApp

/**
 * Deferred for MVP. Returns empty list.
 */
class NoOpAppEnumerator : AppEnumerator {
    override fun getInstalledApps(): List<InstalledApp> = emptyList()
}
