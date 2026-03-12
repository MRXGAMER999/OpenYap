package com.openyap.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.WinCrypt.DATA_BLOB
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

/**
 * Stores secrets using Java Preferences as a pragmatic first implementation.
 * Full Windows Credential Manager integration via JNA CredWrite/CredRead
 * can replace this when needed for production-grade security.
 */
class WindowsCredentialStorage : SecureStorage {

    private val prefs = Preferences.userNodeForPackage(WindowsCredentialStorage::class.java)

    override suspend fun save(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.put(key, value)
        prefs.flush()
    }

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        prefs.get(key, null)
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        prefs.remove(key)
        prefs.flush()
    }
}
