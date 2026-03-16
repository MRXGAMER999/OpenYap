package com.openyap.platform

import com.openyap.viewmodel.AudioFeedbackPlayer

class AudioFeedbackPlayerImpl(
    private val audioFeedbackService: AudioFeedbackService,
) : AudioFeedbackPlayer {
    override fun playStart() = audioFeedbackService.play(AudioFeedbackService.Tone.START)
    override fun playStop() = audioFeedbackService.play(AudioFeedbackService.Tone.STOP)
    override fun playTooShort() = audioFeedbackService.play(AudioFeedbackService.Tone.TOO_SHORT)
    override fun playError() = audioFeedbackService.play(AudioFeedbackService.Tone.ERROR)
}
