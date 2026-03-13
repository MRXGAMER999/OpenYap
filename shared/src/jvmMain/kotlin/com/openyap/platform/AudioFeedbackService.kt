package com.openyap.platform

import java.io.ByteArrayInputStream
import java.io.Closeable
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException

class AudioFeedbackService : Closeable {

    enum class Tone { START, STOP, TOO_SHORT, ERROR }

    private val clips = mutableMapOf<Tone, Clip>()

    fun preload(toneBytes: Map<Tone, ByteArray>) {
        toneBytes.forEach { (tone, bytes) ->
            try {
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                    val info = DataLine.Info(Clip::class.java, stream.format)
                    val clip = AudioSystem.getLine(info) as Clip
                    clip.open(stream)
                    clips[tone] = clip
                }
            } catch (_: LineUnavailableException) {
                // No audio device available — visual feedback is the fallback
            } catch (_: Exception) {
                // Corrupt or unsupported WAV — skip silently
            }
        }
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

    override fun close() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }
}
