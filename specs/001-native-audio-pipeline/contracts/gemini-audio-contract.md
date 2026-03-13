# Gemini API Audio Contract

**Feature**: 001-native-audio-pipeline
**Endpoint**: `POST /v1beta/models/{model}:generateContent`
**Base URL**: `https://generativelanguage.googleapis.com`

---

## Contract Change

The `processAudio` method gains a `mimeType` parameter to support both compressed (AAC) and uncompressed (WAV) audio formats.

### Current Request (WAV only)

```json
{
  "contents": [{
    "parts": [{
      "inlineData": {
        "mimeType": "audio/wav",
        "data": "<base64-encoded-wav>"
      }
    }]
  }],
  "system_instruction": {
    "parts": [{ "text": "<system-prompt>" }]
  },
  "generationConfig": {
    "temperature": 0.7
  }
}
```

### Proposed Request (format-aware)

```json
{
  "contents": [{
    "parts": [{
      "inlineData": {
        "mimeType": "audio/aac",
        "data": "<base64-encoded-aac>"
      }
    }]
  }],
  "system_instruction": {
    "parts": [{ "text": "<system-prompt>" }]
  },
  "generationConfig": {
    "temperature": 0.7
  }
}
```

**Only change**: `mimeType` field value is parameterized instead of hardcoded.

---

## Supported MIME Types

| MIME Type | Format | Source | Used When |
|---|---|---|---|
| `audio/aac` | ADTS-wrapped AAC-LC, 48kHz, mono, 96kbps | Native pipeline (`NativeAudioRecorder`) | `NativeAudioBridge.isAvailable == true` |
| `audio/wav` | PCM WAV, 16kHz, 16-bit, mono | Fallback pipeline (`JvmAudioRecorder`) | `NativeAudioBridge.isAvailable == false` |

---

## Gemini Constraints

| Constraint | Value | Source |
|---|---|---|
| Inline data limit | 20 MB | Gemini API docs |
| Max AAC duration at 96kbps | ~20 minutes | `96000 bps / 8 * 1200s * 1.33 (base64) = ~19.2 MB` |
| Max WAV duration at 16kHz/16-bit | ~10 minutes | `32000 B/s * 600s * 1.33 = ~25.5 MB` (exceeds limit at ~10 min) |
| Supported audio formats | WAV, MP3, AIFF, AAC, OGG Vorbis, FLAC | Gemini API docs |
| Target model | `gemini-3.1-flash-lite-preview` | Feature spec |

---

## Kotlin Interface Change

```kotlin
// GeminiClient.kt — processAudio signature change

// Before:
suspend fun processAudio(
    audioBytes: ByteArray,
    systemPrompt: String,
    apiKey: String,
    model: String,
): String

// After:
suspend fun processAudio(
    audioBytes: ByteArray,
    mimeType: String,           // NEW parameter
    systemPrompt: String,
    apiKey: String,
    model: String,
): String
```

The `mimeType` parameter replaces the hardcoded `"audio/wav"` in the `InlineData` construction at `GeminiClient.kt:78`.

---

## Backward Compatibility

- The fallback path (`JvmAudioRecorder`) passes `mimeType = "audio/wav"` — identical to current behavior.
- No change to the Gemini API endpoint, authentication, or response format.
- The response parsing in `GeminiClient` is unchanged.
