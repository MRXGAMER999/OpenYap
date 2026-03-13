package com.openyap.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.Crypt32
import com.sun.jna.platform.win32.WinCrypt.DATA_BLOB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences
import java.util.Base64

/**
 * Stores secrets using Windows DPAPI (CryptProtectData / CryptUnprotectData).
 * The encrypted blob is Base64-encoded and stored in Java Preferences (registry).
 * Decryption only works for the same Windows user account that encrypted the data.
 */
class WindowsCredentialStorage : SecureStorage {

    private val prefs = Preferences.userNodeForPackage(WindowsCredentialStorage::class.java)

    override suspend fun save(key: String, value: String) = withContext(Dispatchers.IO) {
        val encrypted = dpApiEncrypt(value)
        prefs.put(key, Base64.getEncoder().encodeToString(encrypted))
        prefs.flush()
    }

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.get(key, null) ?: return@withContext null
        try {
            val encrypted = Base64.getDecoder().decode(stored)
            dpApiDecrypt(encrypted)
        } catch (_: Exception) {
            // Handle migration from old plaintext storage — return raw value if
            // it's not a valid DPAPI blob, then re-encrypt it on next save.
            // This covers users upgrading from the previous plaintext version.
            try {
                val plaintext = stored
                // Attempt to re-encrypt transparently on next save
                val reEncrypted = dpApiEncrypt(plaintext)
                prefs.put(key, Base64.getEncoder().encodeToString(reEncrypted))
                prefs.flush()
                plaintext
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        prefs.remove(key)
        prefs.flush()
    }

    private fun dpApiEncrypt(plaintext: String): ByteArray {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        val input = DATA_BLOB().apply {
            cbData = bytes.size
            pbData = Memory(bytes.size.toLong()).also { it.write(0, bytes, 0, bytes.size) }
        }
        val output = DATA_BLOB()
        if (!Crypt32.INSTANCE.CryptProtectData(input, "OpenYap", null, null, null, 0, output)) {
            val error = Native.getLastError()
            throw IllegalStateException("CryptProtectData failed with error $error")
        }
        val encrypted = output.pbData.getByteArray(0, output.cbData)
        // Free the output memory allocated by Windows
        com.sun.jna.platform.win32.Kernel32.INSTANCE.LocalFree(output.pbData)
        return encrypted
    }

    private fun dpApiDecrypt(encrypted: ByteArray): String {
        val input = DATA_BLOB().apply {
            cbData = encrypted.size
            pbData = Memory(encrypted.size.toLong()).also { it.write(0, encrypted, 0, encrypted.size) }
        }
        val output = DATA_BLOB()
        if (!Crypt32.INSTANCE.CryptUnprotectData(input, null, null, null, null, 0, output)) {
            val error = Native.getLastError()
            throw IllegalStateException("CryptUnprotectData failed with error $error")
        }
        val decrypted = output.pbData.getByteArray(0, output.cbData)
        com.sun.jna.platform.win32.Kernel32.INSTANCE.LocalFree(output.pbData)
        return decrypted.toString(Charsets.UTF_8)
    }
}
