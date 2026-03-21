package com.openyap.platform

import com.openyap.model.AudioDevice
import com.openyap.platform.NativeAudioBridge.readLastError
import com.sun.jna.CallbackThreadInitializer
import com.sun.jna.Native
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

internal data class SpeechTrimBounds(
    val startSample: Int,
    val endSampleExclusive: Int,
)

internal fun calculateSpeechTrimBounds(
    totalSamples: Int,
    frameSamples: Int,
    firstSpeechFrame: Int,
    lastSpeechFrame: Int,
    leadingMarginFrames: Int,
    trailingMarginFrames: Int,
    hangoverFrames: Int,
    maxLeadingTrimFrames: Int,
): SpeechTrimBounds? {
    if (totalSamples <= 0 || frameSamples <= 0) {
        return null
    }
    if (firstSpeechFrame < 0 || lastSpeechFrame < firstSpeechFrame) {
        return null
    }

    val totalFrames = (totalSamples + frameSamples - 1) / frameSamples
    val trimmedStartFrame = (firstSpeechFrame - leadingMarginFrames).coerceAtLeast(0)
    val startFrame = minOf(trimmedStartFrame, maxLeadingTrimFrames)
    val endFrameExclusive = min(
        totalFrames,
        lastSpeechFrame + 1 + trailingMarginFrames + hangoverFrames,
    )
    val startSample = min(totalSamples, startFrame * frameSamples)
    val endSampleExclusive = min(totalSamples, endFrameExclusive * frameSamples)

    return if (endSampleExclusive > startSample) {
        SpeechTrimBounds(startSample = startSample, endSampleExclusive = endSampleExclusive)
    } else {
        null
    }
}

class NativeAudioRecorder(
    private val native: NativeAudioBridge.OpenYapNative = NativeAudioBridge.instance
        ?: error("Native audio pipeline is unavailable."),
) : AudioRecorder, AudioRecorderDiagnostics, Closeable {

    companion object {
        private const val SampleRate = 48000
        private const val Channels = 1
        private const val VadFrameSamples = 960
        private const val NormalLeadingMarginFrames = 10
        private const val NormalTrailingMarginFrames = 10
        private const val NormalHangoverFrames = 15
        private const val MaxLeadingTrimFrames = 30
        private const val WhisperLeadingMarginFrames = 18
        private const val WhisperTrailingMarginFrames = 18
        private const val WhisperHangoverFrames = 28
    }

    private val _amplitudeFlow = MutableStateFlow(0f)
    override val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private val pcmBuffer = ConcurrentLinkedQueue<ShortArray>()
    private val isRecording = AtomicBoolean(false)
    private val pendingWarning = AtomicReference<String?>(null)
    private val sensitivityPreset = AtomicReference(RecordingSensitivityPreset.NORMAL)

    @Volatile
    private var outputPath: String? = null

    // ── JNI callback (hot path) ───────────────────────────────────────────────
    // Used when NativeAudioCallbackJni.isAvailable == true.
    // The ShortArray arrives already allocated by the C++ bridge — no Pointer
    // marshalling, no JNA reflection proxy, no per-chunk thread management.
    private val jniCaptureCallback = NativeAudioCallbackJni.AudioDataCallback { pcmData, sampleCount ->
        pcmBuffer.offer(pcmData)
        _amplitudeFlow.value = native.openyap_amplitude(pcmData, sampleCount).coerceIn(0f, 1f)
    }

    // ── JNA callback (fallback) ───────────────────────────────────────────────
    // Used when the DLL was built without JDK / OPENYAP_JNI_ENABLED, or when
    // System.load failed for any other reason.
    private val jnaCaptureCallback =
        NativeAudioBridge.OpenYapNative.AudioCallback { pcmData, sampleCount, _ ->
            val samples = pcmData.getShortArray(0, sampleCount)
            pcmBuffer.offer(samples)
            _amplitudeFlow.value = native.openyap_amplitude(samples, sampleCount).coerceIn(0f, 1f)
        }

    init {
        // CallbackThreadInitializer tells JNA to keep the WASAPI capture thread
        // attached to the JVM between chunks (daemon=true, detach=false).
        // Not needed for the JNI path — the C++ bridge manages thread attachment
        // via thread-local RAII (JvmThreadGuard in jni_audio_bridge.cpp).
        if (!NativeAudioCallbackJni.isAvailable) {
            Native.setCallbackThreadInitializer(
                jnaCaptureCallback,
                CallbackThreadInitializer(true, false, "WASAPI-Audio-Callback"),
            )
        }
    }

    override suspend fun startRecording(
        outputPath: String,
        deviceId: String?,
        sensitivityPreset: RecordingSensitivityPreset,
    ) {
        check(isRecording.compareAndSet(false, true)) { "Recording already in progress." }

        try {
            this.outputPath = outputPath
            this.sensitivityPreset.set(sensitivityPreset)
            pendingWarning.set(null)
            pcmBuffer.clear()
            _amplitudeFlow.value = 0f

            val result = if (NativeAudioCallbackJni.isAvailable) {
                // JNI path: direct CallVoidMethod from the capture thread.
                if (deviceId != null) {
                    NativeAudioCallbackJni.captureStartWithDeviceJni(
                        SampleRate, Channels, jniCaptureCallback, deviceId,
                    )
                } else {
                    NativeAudioCallbackJni.captureStartJni(
                        SampleRate, Channels, jniCaptureCallback,
                    )
                }
            } else {
                // JNA fallback path: reflection bridge through JNA proxy.
                if (deviceId != null) {
                    native.openyap_capture_start_device(
                        SampleRate, Channels, jnaCaptureCallback, null, deviceId,
                    )
                } else {
                    native.openyap_capture_start(SampleRate, Channels, jnaCaptureCallback, null)
                }
            }
            if (result != 0) {
                throw IllegalStateException(
                    nativeError("Failed to start native audio capture", result),
                )
            }
        } catch (error: Throwable) {
            isRecording.set(false)
            this.outputPath = null
            throw error
        }
    }

    override suspend fun stopRecording(): String {
        val path = outputPath ?: throw IllegalStateException("No recording in progress.")
        check(isRecording.getAndSet(false)) { "No recording in progress." }

        val stopResult = native.openyap_capture_stop()
        // Release the JNI GlobalRef now that the capture thread has exited its
        // loop and will never call CallVoidMethod again.  This lets the Kotlin
        // callback object be garbage-collected between recording sessions.
        if (NativeAudioCallbackJni.isAvailable) {
            runCatching { NativeAudioCallbackJni.releaseCallbackJni() }
        }
        val disconnected = stopResult == -2
        if (disconnected) {
            pendingWarning.set("Microphone disconnected. Processing captured audio.")
        }
        if (stopResult != 0 && stopResult != -2) {
            resetState()
            throw IllegalStateException(
                nativeError("Failed to stop native audio capture", stopResult),
            )
        }

        val capturedPcm = drainSamples()
        resetState()

        if (capturedPcm.isEmpty()) {
            throw IllegalStateException("Native recorder captured no audio.")
        }

        val trimmedPcm = trimSilence(capturedPcm)
        writeWaveFile(trimmedPcm, path)
        return path
    }

    override suspend fun hasPermission(): Boolean = true

    override suspend fun listDevices(): List<AudioDevice> {
        val ptr = native.openyap_list_devices() ?: return emptyList()
        return try {
            val jsonString = ptr.getString(0, "UTF-8")
            parseDeviceJson(jsonString)
        } finally {
            native.openyap_free_string(ptr)
        }
    }

    override fun consumeWarning(): String? = pendingWarning.getAndSet(null)

    override fun close() {
        if (isRecording.getAndSet(false)) {
            runCatching { native.openyap_capture_stop() }
            if (NativeAudioCallbackJni.isAvailable) {
                runCatching { NativeAudioCallbackJni.releaseCallbackJni() }
            }
        }
        pendingWarning.set(null)
        resetState()
    }

    private fun parseDeviceJson(jsonString: String): List<AudioDevice> {
        return try {
            val array = Json.parseToJsonElement(jsonString) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val isDefault = obj["is_default"]?.jsonPrimitive?.boolean ?: false
                AudioDevice(id = id, name = name, isDefault = isDefault)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun drainSamples(): ShortArray {
        val chunks = mutableListOf<ShortArray>()
        var totalSamples = 0

        while (true) {
            val chunk = pcmBuffer.poll() ?: break
            chunks += chunk
            totalSamples += chunk.size
        }

        if (totalSamples == 0) {
            return ShortArray(0)
        }

        val allSamples = ShortArray(totalSamples)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(allSamples, destinationOffset = offset)
            offset += chunk.size
        }
        return allSamples
    }

    private fun trimSilence(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) {
            return samples
        }

        // Reset VAD noise floor so each recording gets a fresh baseline
        native.openyap_vad_reset()

        val totalFrames = (samples.size + VadFrameSamples - 1) / VadFrameSamples
        val preset = sensitivityPreset.get()
        val leadingMarginFrames = when (preset) {
            RecordingSensitivityPreset.NORMAL -> NormalLeadingMarginFrames
            RecordingSensitivityPreset.WHISPER -> WhisperLeadingMarginFrames
        }
        val trailingMarginFrames = when (preset) {
            RecordingSensitivityPreset.NORMAL -> NormalTrailingMarginFrames
            RecordingSensitivityPreset.WHISPER -> WhisperTrailingMarginFrames
        }
        val hangoverFrames = when (preset) {
            RecordingSensitivityPreset.NORMAL -> NormalHangoverFrames
            RecordingSensitivityPreset.WHISPER -> WhisperHangoverFrames
        }
        var firstSpeechFrame = -1
        var lastSpeechFrame = -1

        for (frameIndex in 0 until totalFrames) {
            val start = frameIndex * VadFrameSamples
            val end = min(samples.size, start + VadFrameSamples)
            val frame = samples.copyOfRange(start, end)
            val isSpeech = native.openyap_vad_is_speech(frame, frame.size, SampleRate)
            if (isSpeech < 0) {
                return samples
            }
            if (isSpeech == 1) {
                if (firstSpeechFrame == -1) {
                    firstSpeechFrame = frameIndex
                }
                lastSpeechFrame = frameIndex
            }
        }

        if (firstSpeechFrame == -1 || lastSpeechFrame == -1) {
            return samples
        }

        val trimBounds = calculateSpeechTrimBounds(
            totalSamples = samples.size,
            frameSamples = VadFrameSamples,
            firstSpeechFrame = firstSpeechFrame,
            lastSpeechFrame = lastSpeechFrame,
            leadingMarginFrames = leadingMarginFrames,
            trailingMarginFrames = trailingMarginFrames,
            hangoverFrames = hangoverFrames,
            maxLeadingTrimFrames = MaxLeadingTrimFrames,
        ) ?: return samples
        return samples.copyOfRange(trimBounds.startSample, trimBounds.endSampleExclusive)
    }

    private fun writeWaveFile(samples: ShortArray, outputPath: String) {
        val pcmBytes = ByteArray(samples.size * 2)
        var byteIndex = 0
        for (sample in samples) {
            pcmBytes[byteIndex++] = (sample.toInt() and 0xFF).toByte()
            pcmBytes[byteIndex++] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }

        val audioFormat = AudioFormat(
            SampleRate.toFloat(),
            16,
            Channels,
            true,
            false,
        )
        val frameLength = samples.size.toLong() / Channels
        AudioInputStream(
            ByteArrayInputStream(pcmBytes),
            audioFormat,
            frameLength,
        ).use { audioInputStream ->
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, File(outputPath))
        }
    }

    private fun nativeError(prefix: String, code: Int): String {
        val detail = native.readLastError()?.takeUnless { it.isBlank() }
        return if (detail != null) "$prefix: $detail" else "$prefix (code $code)."
    }

    private fun resetState() {
        pcmBuffer.clear()
        outputPath = null
        _amplitudeFlow.value = 0f
    }
}
