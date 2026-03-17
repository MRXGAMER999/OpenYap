package com.openyap.platform

import java.io.File

/**
 * JNI bridge for the audio capture callback.
 *
 * The JNA-based [NativeAudioBridge] path crosses every PCM chunk through
 * JNA's reflection proxy: native thread → JNA callback dispatcher →
 * Java method lookup → Kotlin lambda.  That overhead fires continuously
 * for the entire duration of every recording.
 *
 * This object registers the same [openyap_native.dll] binary with the JVM's
 * native-library tracker via [System.load] / [System.loadLibrary], which
 * makes the DLL's `Java_com_openyap_platform_NativeAudioCallbackJni_*`
 * symbols available as JNI [external] methods.  The C++ side then calls
 * the Kotlin [AudioDataCallback.onAudioData] method directly via
 * `CallVoidMethod` — zero reflection, zero JNA proxy.
 *
 * The WASAPI capture thread is attached to the JVM on its first audio chunk
 * and stays attached for the whole recording session (cached in thread-local
 * storage on the C++ side).  A RAII guard in `jni_audio_bridge.cpp` calls
 * `DetachCurrentThread` automatically when the `std::thread` exits.
 *
 * All other native calls (hotkeys, VAD, encode, paste) remain on JNA — they
 * are infrequent and the reflection overhead is negligible there.
 *
 * Falls back gracefully: if the JDK was not present at DLL build time
 * (`OPENYAP_JNI_ENABLED` not defined), or if [System.load] fails for any
 * reason, [isAvailable] is `false` and [NativeAudioRecorder] reverts to
 * the JNA path automatically.
 */
object NativeAudioCallbackJni {

    /**
     * Callback interface whose single abstract method is invoked directly from
     * native code via JNI `CallVoidMethod`.  The [pcmData] array is freshly
     * allocated by the C++ bridge for each chunk (same allocation cost as JNA's
     * `Pointer.getShortArray`, but without the reflection dispatch on top).
     */
    fun interface AudioDataCallback {
        fun onAudioData(pcmData: ShortArray, sampleCount: Int)
    }

    /**
     * `true` when the DLL was successfully registered with the JVM for JNI
     * symbol lookup and the `Java_…_captureStartJni` symbols are present.
     * Evaluated once at class-load time.
     */
    val isAvailable: Boolean = tryLoad()

    // ── System.load / loadLibrary ─────────────────────────────────────────────

    private fun tryLoad(): Boolean {
        val path = NativeAudioBridge.loadedPath
        if (path == null) {
            // NativeAudioBridge itself failed to load — nothing to register.
            return false
        }
        return try {
            // If the DLL was resolved to an absolute filesystem path, use
            // System.load(path) so the JVM can locate its JNI exports.
            // On Windows, the underlying LoadLibraryW call simply bumps the
            // ref-count on the already-loaded module — no second copy is loaded.
            //
            // If it was resolved by bare library name ("openyap_native"),
            // use System.loadLibrary so the JVM searches java.library.path.
            if (path.contains(File.separatorChar) || path.contains('/')) {
                System.load(path)
            } else {
                System.loadLibrary(path)
            }
            true
        } catch (e: UnsatisfiedLinkError) {
            // Most likely cause: the DLL was built without OPENYAP_JNI_ENABLED
            // (JDK not found at CMake time), so the Java_… symbols are absent.
            System.err.println(
                "NativeAudioCallbackJni: JNI symbols not found in native library " +
                    "(built without JDK? falling back to JNA): ${e.message}"
            )
            false
        } catch (e: Exception) {
            System.err.println(
                "NativeAudioCallbackJni: unexpected error registering JNI bridge: ${e.message}"
            )
            false
        }
    }

    // ── JNI external declarations ─────────────────────────────────────────────
    // These correspond 1-to-1 with the JNIEXPORT functions in jni_audio_bridge.cpp.
    // @JvmStatic causes the Kotlin compiler to emit a true static native method
    // on the NativeAudioCallbackJni class, which matches the `jclass clazz`
    // second parameter in the C++ JNI signatures.

    /**
     * Starts capture on the default microphone using the JNI callback path.
     *
     * Equivalent to [NativeAudioBridge.OpenYapNative.openyap_capture_start] but
     * routes audio chunks through [jni_audio_adapter] in `jni_audio_bridge.cpp`
     * instead of JNA's reflection proxy.
     *
     * @return 0 on success; non-zero on failure (check `openyap_last_error()`).
     */
    @JvmStatic
    external fun captureStartJni(
        sampleRate: Int,
        channels: Int,
        callback: AudioDataCallback,
    ): Int

    /**
     * Starts capture on a specific device using the JNI callback path.
     *
     * @param deviceId the device ID string from [openyap_list_devices], or
     *   `null` to select the default microphone.
     * @return 0 on success; non-zero on failure.
     */
    @JvmStatic
    external fun captureStartWithDeviceJni(
        sampleRate: Int,
        channels: Int,
        callback: AudioDataCallback,
        deviceId: String?,
    ): Int

    /**
     * Releases the JNI `GlobalRef` held on the [AudioDataCallback] object,
     * allowing it to be garbage-collected between recording sessions.
     *
     * Must be called **after** `openyap_capture_stop()` has returned, so that
     * the capture thread has fully exited its loop and will never call
     * `CallVoidMethod` again.
     */
    @JvmStatic
    external fun releaseCallbackJni()
}
