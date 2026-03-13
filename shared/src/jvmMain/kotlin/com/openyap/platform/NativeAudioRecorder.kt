package com.openyap.platform

import com.sun.jna.CallbackThreadInitializer
import com.sun.jna.Native
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class NativeAudioRecorder(
    private val native: NativeAudioBridge.OpenYapNative = NativeAudioBridge.instance
        ?: error("Native audio pipeline is unavailable."),
) : AudioRecorder, AudioRecorderDiagnostics, Closeable {

    companion object {
        private const val SampleRate = 48000
        private const val Channels = 1
        private const val Bitrate = 96000
        private const val VadFrameSamples = 960
        private const val LeadingMarginFrames = 10
        private const val TrailingMarginFrames = 10
        private const val HangoverFrames = 15
    }

    private val _amplitudeFlow = MutableStateFlow(0f)
    override val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private val pcmBuffer = ConcurrentLinkedQueue<ShortArray>()
    private val isRecording = AtomicBoolean(false)
    private val pendingWarning = AtomicReference<String?>(null)

    @Volatile
    private var outputPath: String? = null

    private val captureCallback =
        NativeAudioBridge.OpenYapNative.AudioCallback { pcmData, sampleCount, _ ->
            val samples = pcmData.getShortArray(0, sampleCount)
            pcmBuffer.offer(samples)
            _amplitudeFlow.value = native.openyap_amplitude(samples, sampleCount).coerceIn(0f, 1f)
        }

    init {
        Native.setCallbackThreadInitializer(
            captureCallback,
            CallbackThreadInitializer(true, false, "WASAPI-Audio-Callback"),
        )
    }

    override suspend fun startRecording(outputPath: String) {
        check(isRecording.compareAndSet(false, true)) { "Recording already in progress." }

        try {
            this.outputPath = outputPath
            pendingWarning.set(null)
            pcmBuffer.clear()
            _amplitudeFlow.value = 0f

            val result = native.openyap_capture_start(SampleRate, Channels, captureCallback, null)
            if (result != 0) {
                throw IllegalStateException(
                    nativeError(
                        "Failed to start native audio capture",
                        result
                    )
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
        val disconnected = stopResult == -2
        if (disconnected) {
            pendingWarning.set("Microphone disconnected. Processing captured audio.")
        }
        if (stopResult != 0 && stopResult != -2) {
            resetState()
            throw IllegalStateException(
                nativeError(
                    "Failed to stop native audio capture",
                    stopResult
                )
            )
        }

        val capturedPcm = drainSamples()
        resetState()

        if (capturedPcm.isEmpty()) {
            throw IllegalStateException("Native recorder captured no audio.")
        }

        val trimmedPcm = trimSilence(capturedPcm)
        val encodeResult = native.openyap_encode_aac(
            trimmedPcm,
            trimmedPcm.size,
            SampleRate,
            Channels,
            Bitrate,
            path,
        )
        if (encodeResult != 0) {
            val error = nativeError("Failed to encode AAC audio", encodeResult)
            System.err.println("Native audio encoding failed: $error")
            throw IllegalStateException(error)
        }

        return path
    }

    override suspend fun hasPermission(): Boolean = true

    override fun consumeWarning(): String? = pendingWarning.getAndSet(null)

    override fun close() {
        if (isRecording.getAndSet(false)) {
            runCatching { native.openyap_capture_stop() }
        }
        pendingWarning.set(null)
        resetState()
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

        val totalFrames = (samples.size + VadFrameSamples - 1) / VadFrameSamples
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

        val startFrame = (firstSpeechFrame - LeadingMarginFrames).coerceAtLeast(0)
        val endFrameExclusive = min(
            totalFrames,
            lastSpeechFrame + 1 + TrailingMarginFrames + HangoverFrames,
        )
        val startSample = startFrame * VadFrameSamples
        val endSample = min(samples.size, endFrameExclusive * VadFrameSamples)
        return samples.copyOfRange(startSample, endSample)
    }

    private fun nativeError(prefix: String, code: Int): String {
        val detail = native.openyap_last_error()?.takeUnless { it.isBlank() }
        return if (detail != null) "$prefix: $detail" else "$prefix (code $code)."
    }

    private fun resetState() {
        pcmBuffer.clear()
        outputPath = null
        _amplitudeFlow.value = 0f
    }
}
