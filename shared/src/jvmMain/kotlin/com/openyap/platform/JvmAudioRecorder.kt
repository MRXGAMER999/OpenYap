package com.openyap.platform

import com.openyap.model.AudioDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class JvmAudioRecorder : AudioRecorder, Closeable {

    private val _amplitudeFlow = MutableStateFlow(0f)
    override val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private var targetDataLine: TargetDataLine? = null
    private var recordingJob: Job? = null
    private var audioData: ByteArrayOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val audioFormat = AudioFormat(
        16000f, // sample rate
        16,     // sample size in bits
        1,      // mono
        true,   // signed
        false,  // little-endian
    )

    @Volatile
    private var currentOutputPath: String? = null

    override suspend fun startRecording(
        outputPath: String,
        deviceId: String?,
        sensitivityPreset: RecordingSensitivityPreset,
    ) =
        withContext(Dispatchers.IO) {
            stopRecordingInternal()

            currentOutputPath = outputPath
            audioData?.close()
            audioData = ByteArrayOutputStream()

            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            if (!AudioSystem.isLineSupported(info)) {
                throw IllegalStateException("Audio line not supported")
            }

            val line = AudioSystem.getLine(info) as TargetDataLine
            line.open(audioFormat)
            line.start()
            targetDataLine = line

            recordingJob = scope.launch {
                val buffer = ByteArray(4096)
                while (isActive && line.isOpen) {
                    val bytesRead = line.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        audioData?.write(buffer, 0, bytesRead)
                        _amplitudeFlow.value = calculateRmsAmplitude(buffer, bytesRead)
                    }
                }
            }
        }

    override suspend fun stopRecording(): String = withContext(Dispatchers.IO) {
        val path = currentOutputPath ?: throw IllegalStateException("No recording in progress")
        stopRecordingInternal()

        val data = audioData?.toByteArray() ?: throw IllegalStateException("No audio data")
        audioData = null

        val outFile = File(path)
        AudioInputStream(
            ByteArrayInputStream(data),
            audioFormat,
            data.size.toLong() / audioFormat.frameSize,
        ).use { audioInputStream ->
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outFile)
        }

        _amplitudeFlow.value = 0f
        currentOutputPath = null
        path
    }

    override suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            AudioSystem.isLineSupported(info)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun listDevices(): List<AudioDevice> = emptyList()

    private fun stopRecordingInternal() {
        recordingJob?.cancel()
        recordingJob = null
        targetDataLine?.let {
            if (it.isOpen) {
                it.stop()
                it.close()
            }
        }
        targetDataLine = null
    }

    override fun close() {
        stopRecordingInternal()
        audioData?.close()
        audioData = null
        currentOutputPath = null
        _amplitudeFlow.value = 0f
        scope.cancel()
    }

    private fun calculateRmsAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        val samples = bytesRead / 2
        if (samples == 0) return 0f

        var peak = 0f

        for (i in 0 until samples) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt() and 0xFF
            val combined = (high shl 8) or low
            val sample = if (combined >= 0x8000) combined - 0x10000 else combined
            val normalized = kotlin.math.abs(sample) / 32768f
            if (normalized > peak) peak = normalized
        }
        return (peak * 4f).coerceIn(0f, 1f)
    }
}
