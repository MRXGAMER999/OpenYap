package com.openyap.model

data class InstalledApp(
    val name: String,
    val executablePath: String = "",
    val iconBase64: String? = null,
)
