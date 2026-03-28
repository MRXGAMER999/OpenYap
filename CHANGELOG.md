# Changelog

## [1.3.1] - 2026-03-28

fix: switch AI transcription output to plain text

- Updated Groq Whisper transcription requests to return plain text instead of JSON.
- Removed JSON deserialization from the transcription response path to avoid failures from malformed model output.

## [1.3.0] - 2026-03-24

feat: introduce minimalist Liquid Glass recording overlay

- Replaced the dark capsule with a premium "Liquid Glass" design featuring high-refraction borders and depth simulation.
- Removed the numerical timer to minimize visual noise and focus on pure recording status.
- Enhanced waveform sensitivity to provide dynamic visual feedback at normal speaking volumes.
- Refined the overlay dimensions for a more compact and integrated desktop experience.

## [1.2.1] - 2026-03-23

fix: stabilize dictation correction behavior

- Reworked the second-pass correction prompt to use a clearer two-path flow: minimal edits for understandable transcripts and conservative repair for broken transcripts.
- Improved handling of accented, noisy, ill, and non-native speech by treating imperfect transcripts as normal input.
- Reduced correction temperatures for non-Gen Z dictation flows to make rewrites more literal and less prone to over-correction.

## [1.2.0] - 2026-03-22

feat: enhance prompt rules and adjust correction temperature settings

- Added explicit instruction to never censor or mask words, preserving all language including profanity.
- Updated the correction prompt to focus on producing coherent sentences while maintaining the speaker's intent.
- Adjusted correction temperature settings for transcription providers to improve accuracy and responsiveness.
