package com.openyap.platform

import java.io.File

class JvmFileOperations : FileOperations {
    override fun tempDir(): String = PlatformInit.tempDir.toString()
    override fun readFile(path: String): ByteArray = File(path).readBytes()
    override fun deleteFile(path: String): Boolean = File(path).delete()
}
