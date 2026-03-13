package com.openyap.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WindowsStartupManager(
    private val appName: String = "OpenYap",
) : StartupManager {

    companion object {
        private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    }

    override val isSupported: Boolean
        get() = resolveLaunchCommand() != null

    override suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "reg",
            "query",
            RUN_KEY,
            "/v",
            appName,
        )
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    }

    override suspend fun setEnabled(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        if (enabled) {
            val launchCommand = resolveLaunchCommand()
                ?: error("Launch on startup is only available in the installed desktop app.")

            executeReg(
                "add",
                RUN_KEY,
                "/v",
                appName,
                "/t",
                "REG_SZ",
                "/d",
                launchCommand,
                "/f",
            )
        } else {
            val result = executeReg(
                "delete",
                RUN_KEY,
                "/v",
                appName,
                "/f",
                allowFailure = true,
            )

            if (result.exitCode != 0 && !result.output.contains("unable to find", ignoreCase = true)) {
                error(result.output.ifBlank { "Failed to remove startup entry." })
            }
        }

        Unit
    }

    private fun resolveLaunchCommand(): String? {
        val jpackageLauncher = System.getProperty("jpackage.app-path")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }

        if (jpackageLauncher != null) {
            return quoted(jpackageLauncher.absolutePath)
        }

        val currentCommand = runCatching {
            ProcessHandle.current().info().command().orElse(null)
        }.getOrNull()
            ?.let(::File)
            ?.takeIf { it.exists() }

        if (currentCommand != null) {
            val name = currentCommand.name.lowercase()
            if (name.endsWith(".exe") && name != "java.exe" && name != "javaw.exe") {
                return quoted(currentCommand.absolutePath)
            }
        }

        return null
    }

    private fun executeReg(vararg args: String, allowFailure: Boolean = false): RegResult {
        val process = ProcessBuilder("reg", *args)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        if (exitCode != 0 && !allowFailure) {
            error(output.ifBlank { "Registry command failed with exit code $exitCode." })
        }
        return RegResult(exitCode = exitCode, output = output)
    }

    private fun quoted(value: String): String = "\"$value\""

    private data class RegResult(
        val exitCode: Int,
        val output: String,
    )
}
