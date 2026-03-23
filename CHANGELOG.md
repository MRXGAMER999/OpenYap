# Changelog

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
