package com.openyap.platform

import com.openyap.model.InstalledApp

interface AppEnumerator {
    fun getInstalledApps(): List<InstalledApp>
}
