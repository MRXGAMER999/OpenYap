package com.openyap.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

object NativeAudioBridge {

    private data class LoadResult(
        val library: OpenYapNative?,
        val error: String?,
    )

    private val loadResult: LoadResult by lazy { loadLibrary() }

    val instance: OpenYapNative?
        get() = loadResult.library

    val isAvailable: Boolean
        get() = instance != null

    val failureReason: String?
        get() = loadResult.error

    private fun loadLibrary(): LoadResult {
        var lastError: String? = null
        val candidates = candidateNames()

        if (candidates.none { it == "openyap_native" }) {
            lastError = "No native audio DLL found in known locations. Expected `openyap_native.dll` in `native/prebuilt/windows-x64/`, `composeApp/resources/windows-x64/`, the packaged resources directory, or the working directory."
        }

        for (candidate in candidates) {
            try {
                val library = Native.load(candidate, OpenYapNative::class.java)
                val initResult = library.openyap_init()
                if (initResult == 0) {
                    Runtime.getRuntime().addShutdownHook(Thread {
                        runCatching { library.openyap_capture_stop() }
                        runCatching { library.openyap_shutdown() }
                    })
                    return LoadResult(library = library, error = null)
                }

                val reason = library.openyap_last_error()
                    ?.takeUnless { it.isBlank() }
                    ?: "openyap_init failed with code $initResult"
                lastError = "Failed to initialize native audio pipeline from ${describeCandidate(candidate)}: $reason"
                runCatching { library.openyap_shutdown() }
            } catch (error: Throwable) {
                lastError = "Failed to load native audio pipeline from ${describeCandidate(candidate)}: ${error.message ?: error.javaClass.simpleName}"
            }
        }

        lastError?.let(System.err::println)

        return LoadResult(library = null, error = lastError)
    }

    private fun candidateNames(): List<String> {
        val candidates = LinkedHashSet<String>()
        val resourcesDir = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let(Paths::get)

        fun addPath(path: Path?) {
            if (path != null && path.exists()) {
                candidates += path.absolutePathString()
            }
        }

        addPath(resourcesDir?.resolve("windows-x64")?.resolve("openyap_native.dll"))
        addPath(resourcesDir?.resolve("openyap_native.dll"))
        addPath(Paths.get("resources", "windows-x64", "openyap_native.dll"))
        addPath(Paths.get("build", "processedResources", "jvm", "main", "windows-x64", "openyap_native.dll"))
        addPath(Paths.get("native", "prebuilt", "windows-x64", "openyap_native.dll"))
        addPath(Paths.get("native", "build", "openyap_native.dll"))
        addPath(Paths.get("native", "build", "Release", "openyap_native.dll"))
        addPath(Paths.get("..", "native", "prebuilt", "windows-x64", "openyap_native.dll"))
        addPath(Paths.get("..", "native", "build", "openyap_native.dll"))
        addPath(Paths.get("..", "native", "build", "Release", "openyap_native.dll"))
        addPath(Paths.get("..", "native", "out", "build", "x64-Debug", "openyap_native.dll"))
        addPath(Paths.get("composeApp", "resources", "windows-x64", "openyap_native.dll"))
        addPath(Paths.get("composeApp", "build", "processedResources", "jvm", "main", "windows-x64", "openyap_native.dll"))
        addPath(Paths.get("openyap_native.dll"))
        candidates += "openyap_native"
        return candidates.toList()
    }

    private fun describeCandidate(candidate: String): String {
        return if (candidate == "openyap_native") "system library path" else candidate
    }

    interface OpenYapNative : StdCallLibrary {
        fun openyap_init(): Int
        fun openyap_shutdown()
        fun openyap_capture_start(
            sampleRate: Int,
            channels: Int,
            callback: AudioCallback,
            userData: Pointer?,
        ): Int

        fun openyap_capture_stop(): Int

        fun openyap_encode_aac(
            pcmData: ShortArray,
            pcmSampleCount: Int,
            sampleRate: Int,
            channels: Int,
            bitrate: Int,
            outputPath: String,
        ): Int

        fun openyap_vad_is_speech(
            pcmData: ShortArray,
            sampleCount: Int,
            sampleRate: Int,
        ): Int

        fun openyap_amplitude(
            pcmData: ShortArray,
            sampleCount: Int,
        ): Float

        fun openyap_last_error(): String?

        fun interface AudioCallback : StdCallLibrary.StdCallCallback {
            fun invoke(pcmData: Pointer, sampleCount: Int, userData: Pointer?)
        }
    }
}
