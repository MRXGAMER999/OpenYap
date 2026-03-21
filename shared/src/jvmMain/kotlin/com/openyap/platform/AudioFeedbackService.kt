package com.openyap.platform

import java.io.ByteArrayInputStream
import java.io.Closeable
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineUnavailableException
import kotlin.math.log10

class AudioFeedbackService : Closeable {

    enum class Tone { START, STOP, TOO_SHORT, ERROR }

    private val clips = mutableMapOf<Tone, Clip>()
    private var volume: Float = 0.5f

    fun preload(toneBytes: Map<Tone, ByteArray>) {
        toneBytes.forEach { (tone, bytes) ->
            try {
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                    val info = DataLine.Info(Clip::class.java, stream.format)
                    val clip = AudioSystem.getLine(info) as Clip
                    clip.open(stream)
                    applyVolumeToClip(clip, volume)
                    clips.put(tone, clip)?.let { previousClip ->
                        runCatching { previousClip.close() }
                    }
                }
            } catch (_: LineUnavailableException) {
                // No audio device available — visual feedback is the fallback
            } catch (_: Exception) {
                // Corrupt or unsupported WAV — skip silently
            }
        }
    }

    fun setVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        clips.values.forEach { applyVolumeToClip(it, volume) }
    }

    fun play(tone: Tone) {
        clips[tone]?.let { clip ->
            try {
                clip.framePosition = 0
                clip.start()
            } catch (_: Exception) {
                // Audio playback failure — non-fatal
            }
        }
    }

    private fun applyVolumeToClip(clip: Clip, volume: Float) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val control = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val dB = if (volume <= 0f) {
                    control.minimum
                } else {
                    (20f * log10(volume.toDouble()).toFloat())
                        .coerceIn(control.minimum, control.maximum)
                }
                control.value = dB
            }
        } catch (_: Exception) {
            // Volume control not supported on this platform — ignore
        }
    }

    override fun close() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }
}
