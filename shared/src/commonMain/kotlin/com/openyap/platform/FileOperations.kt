package com.openyap.platform

interface FileOperations {
    fun tempDir(): String
    fun readFile(path: String): ByteArray
    fun deleteFile(path: String): Boolean
}
