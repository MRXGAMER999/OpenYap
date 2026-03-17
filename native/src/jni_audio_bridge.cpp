// jni_audio_bridge.cpp
//
// Direct JNI audio callback bridge for openyap_native.dll.
//
// Problem: Every audio chunk from the WASAPI capture thread crosses into the
// JVM via JNA's reflection bridge (JNA looks up the Java method by reflection,
// attaches/manages the thread, and dispatches through its callback proxy).
// That overhead fires continuously for the entire duration of every recording.
//
// Solution: Export Java_com_openyap_platform_NativeAudioCallbackJni_* symbols
// that the Kotlin side registers via System.load().  When a recording starts
// through captureStartJni / captureStartWithDeviceJni, the native capture
// worker calls jni_audio_adapter() instead of the JNA proxy.  The adapter:
//
//   1. Attaches the WASAPI capture thread to the JVM on its first invocation
//      (once per recording session — not once per chunk).
//   2. Caches the JNIEnv* in thread-local storage so subsequent chunks pay
//      only the cost of a TLS lookup.
//   3. Creates a jshortArray, copies the PCM data, calls the Kotlin
//      onAudioData(ShortArray, Int) method directly via CallVoidMethod, then
//      deletes the local ref — zero reflection involved.
//   4. A thread_local RAII guard (JvmThreadGuard) calls DetachCurrentThread
//      when the capture std::thread exits, keeping the JVM's thread registry
//      clean without requiring any changes to capture_worker().
//
// All other native calls (hotkeys, VAD, encoding, paste) remain on JNA — they
// are infrequent and the reflection overhead there is negligible.
//
// Compiled only when CMake's find_package(JNI) succeeds (OPENYAP_JNI_ENABLED).

#ifdef OPENYAP_JNI_ENABLED

#include <jni.h>

#include "audio_capture.h"

#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>

namespace {

// ── JNI globals ──────────────────────────────────────────────────────────────
// Written once (under g_jni_mutex) before the capture thread is created.
// Read-only from the capture thread thereafter — no lock needed on the hot path.

static JavaVM*    g_jni_vm          = nullptr;
static jobject    g_jni_callback    = nullptr;  // GlobalRef to AudioDataCallback
static jmethodID  g_jni_invoke_mid  = nullptr;  // onAudioData([SI)V
static std::mutex g_jni_mutex;

// ── Per-thread JVM attachment RAII guard ─────────────────────────────────────
// thread_local destructors run when the owning std::thread exits.
// If this thread was attached by the JNI bridge, the destructor detaches it,
// keeping the JVM's internal thread registry clean without any changes to the
// capture_worker() function in audio_capture.cpp.

struct JvmThreadGuard {
    bool attached = false;
    ~JvmThreadGuard() {
        if (attached && g_jni_vm) {
            g_jni_vm->DetachCurrentThread();
        }
    }
};

static thread_local JvmThreadGuard tl_jvm_guard;

// Cached per-thread JNIEnv* — nullptr until the first callback on this thread.
// After the first attach it is never null for the lifetime of the thread, so
// all subsequent chunks skip GetEnv/AttachCurrentThread entirely.
static thread_local JNIEnv* tl_jni_env = nullptr;

// ── Hot-path adapter ─────────────────────────────────────────────────────────
// Registered as the audio_callback_t with openyap::capture::start().
// Called on the WASAPI capture thread for every PCM chunk (~10–20 ms).

static void OPENYAP_CALL jni_audio_adapter(
        const short* pcm, int count, void* /*user_data*/) {

    if (!g_jni_vm || !g_jni_callback || !g_jni_invoke_mid) return;

    // Fast path: JNIEnv already cached for this thread.
    JNIEnv* env = tl_jni_env;

    if (!env) {
        // First callback on this thread — attach to the JVM.
        jint rc = g_jni_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (rc == JNI_EDETACHED) {
            JavaVMAttachArgs args{JNI_VERSION_1_6, "WASAPI-Audio-Capture", nullptr};
            if (g_jni_vm->AttachCurrentThread(
                        reinterpret_cast<void**>(&env), &args) != JNI_OK) {
                std::fprintf(stderr,
                        "openyap_native: JNI bridge failed to attach capture thread.\n");
                return;
            }
            tl_jvm_guard.attached = true;   // RAII guard will detach on thread exit
        } else if (rc != JNI_OK) {
            return;
        }
        tl_jni_env = env;   // Cache for all subsequent chunks on this thread
    }

    // Allocate a Java short[] and copy the native PCM buffer into it.
    jshortArray arr = env->NewShortArray(static_cast<jsize>(count));
    if (!arr) return;   // OOM — skip this chunk

    env->SetShortArrayRegion(arr, 0, static_cast<jsize>(count), pcm);

    // Direct method call — no reflection, no JNA proxy.
    env->CallVoidMethod(g_jni_callback, g_jni_invoke_mid,
                        arr, static_cast<jint>(count));

    env->DeleteLocalRef(arr);
}

// ── Shared start helper ───────────────────────────────────────────────────────

static jint start_capture_internal(
        JNIEnv* env,
        jobject callback,
        jint    sampleRate,
        jint    channels,
        const char* deviceId) {   // nullable

    {
        std::lock_guard<std::mutex> lock(g_jni_mutex);

        // Retrieve the JavaVM once (it never changes for the process lifetime).
        if (!g_jni_vm) {
            if (env->GetJavaVM(&g_jni_vm) != JNI_OK) {
                std::fprintf(stderr,
                        "openyap_native: JNI bridge could not retrieve JavaVM.\n");
                return -2;
            }
        }

        // Replace any previous global ref.
        if (g_jni_callback) {
            env->DeleteGlobalRef(g_jni_callback);
            g_jni_callback = nullptr;
        }
        g_jni_callback = env->NewGlobalRef(callback);
        if (!g_jni_callback) {
            std::fprintf(stderr,
                    "openyap_native: JNI bridge failed to create global callback ref.\n");
            return -2;
        }

        // Cache the method ID for onAudioData(short[], int) : void
        jclass cb_class = env->GetObjectClass(callback);
        g_jni_invoke_mid = env->GetMethodID(cb_class, "onAudioData", "([SI)V");
        if (!g_jni_invoke_mid) {
            std::fprintf(stderr,
                    "openyap_native: JNI bridge could not find "
                    "AudioDataCallback.onAudioData([SI)V.\n");
            env->DeleteGlobalRef(g_jni_callback);
            g_jni_callback  = nullptr;
            return -2;
        }
    }

    std::string error;
    const int result = openyap::capture::start(
            static_cast<int>(sampleRate),
            static_cast<int>(channels),
            jni_audio_adapter,
            nullptr,
            deviceId,   // nullptr → default device
            &error);

    if (result != 0) {
        std::fprintf(stderr,
                "openyap_native: JNI capture start failed: %s\n", error.c_str());
    }
    return static_cast<jint>(result);
}

}  // namespace

// ── JNI exports ──────────────────────────────────────────────────────────────
// Names follow the JNI mangling convention for:
//   object NativeAudioCallbackJni (package com.openyap.platform)
//   @JvmStatic external fun captureStartJni / captureStartWithDeviceJni / releaseCallbackJni

extern "C" {

// fun captureStartJni(sampleRate: Int, channels: Int, callback: AudioDataCallback): Int
JNIEXPORT jint JNICALL
Java_com_openyap_platform_NativeAudioCallbackJni_captureStartJni(
        JNIEnv* env, jclass /*clazz*/,
        jint sampleRate, jint channels,
        jobject callback) {
    return start_capture_internal(env, callback, sampleRate, channels, nullptr);
}

// fun captureStartWithDeviceJni(sampleRate: Int, channels: Int,
//                               callback: AudioDataCallback, deviceId: String?): Int
JNIEXPORT jint JNICALL
Java_com_openyap_platform_NativeAudioCallbackJni_captureStartWithDeviceJni(
        JNIEnv* env, jclass /*clazz*/,
        jint sampleRate, jint channels,
        jobject callback, jstring deviceId) {
    const char* device_str = nullptr;
    if (deviceId) {
        device_str = env->GetStringUTFChars(deviceId, nullptr);
    }
    const jint result = start_capture_internal(
            env, callback, sampleRate, channels, device_str);
    if (device_str) {
        env->ReleaseStringUTFChars(deviceId, device_str);
    }
    return result;
}

// fun releaseCallbackJni()
// Call after openyap_capture_stop() returns to release the GlobalRef and allow
// the Kotlin callback object to be garbage-collected between recording sessions.
JNIEXPORT void JNICALL
Java_com_openyap_platform_NativeAudioCallbackJni_releaseCallbackJni(
        JNIEnv* env, jclass /*clazz*/) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    if (g_jni_callback) {
        env->DeleteGlobalRef(g_jni_callback);
        g_jni_callback  = nullptr;
    }
    g_jni_invoke_mid = nullptr;
}

}  // extern "C"

#endif  // OPENYAP_JNI_ENABLED