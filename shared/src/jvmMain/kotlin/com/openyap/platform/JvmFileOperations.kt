package com.openyap.platform

import org.koin.core.annotation.Single
import java.io.File

@Single(binds = [FileOperations::class])
class JvmFileOperations : FileOperations {
    override fun tempDir(): String = PlatformInit.tempDir.toString()
    override fun readFile(path: String): ByteArray = File(path).readBytes()
    override fun deleteFile(path: String): Boolean = File(path).delete()
}
