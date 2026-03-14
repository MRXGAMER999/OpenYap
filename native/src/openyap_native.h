#pragma once

#if defined(_WIN32)
#define OPENYAP_CALL __stdcall
#if defined(OPENYAP_EXPORTS)
#define OPENYAP_API extern "C" __declspec(dllexport)
#else
#define OPENYAP_API extern "C" __declspec(dllimport)
#endif
#else
#define OPENYAP_CALL
#define OPENYAP_API extern "C"
#endif

typedef void(OPENYAP_CALL *audio_callback_t)(const short *pcm_data, int sample_count, void *user_data);

typedef void(OPENYAP_CALL *hotkey_event_callback_t)(int event_type, int vk_code, int modifiers_mask, void *user_data);

enum openyap_hotkey_event_type {
    OPENYAP_HOTKEY_EVENT_HOLD_DOWN = 1,
    OPENYAP_HOTKEY_EVENT_HOLD_UP = 2,
    OPENYAP_HOTKEY_EVENT_CANCEL_RECORDING = 3,
    OPENYAP_HOTKEY_EVENT_CAPTURED = 4,
};

OPENYAP_API int OPENYAP_CALL openyap_init(void);
OPENYAP_API void OPENYAP_CALL openyap_shutdown(void);

OPENYAP_API int OPENYAP_CALL openyap_capture_start(
        int sample_rate,
        int channels,
        audio_callback_t callback,
        void *user_data
);

OPENYAP_API int OPENYAP_CALL openyap_capture_start_device(
        int sample_rate,
        int channels,
        audio_callback_t callback,
        void *user_data,
        const char *device_id
);

OPENYAP_API int OPENYAP_CALL openyap_capture_stop(void);

OPENYAP_API int OPENYAP_CALL openyap_hotkey_set_config(
        int key_code,
        unsigned int modifiers_mask,
        int enabled
);

OPENYAP_API int OPENYAP_CALL openyap_hotkey_start_listening(
        hotkey_event_callback_t callback,
        void *user_data
);

OPENYAP_API int OPENYAP_CALL openyap_hotkey_stop_listening(void);

OPENYAP_API int OPENYAP_CALL openyap_hotkey_begin_capture(void);

OPENYAP_API int OPENYAP_CALL openyap_hotkey_cancel_capture(void);

/// Returns a JSON array of devices: [{"id":"...","name":"...","is_default":true}, ...]
/// The caller must free the returned string with openyap_free_string().
/// Returns nullptr on failure (check openyap_last_error()).
OPENYAP_API const char *OPENYAP_CALL openyap_list_devices(void);

/// Frees a string previously returned by openyap_list_devices().
OPENYAP_API void OPENYAP_CALL openyap_free_string(const char *str);

OPENYAP_API int OPENYAP_CALL openyap_encode_aac(
        const short *pcm_data,
        int pcm_sample_count,
        int sample_rate,
        int channels,
        int bitrate,
        const char *output_path
);

OPENYAP_API int OPENYAP_CALL openyap_vad_is_speech(
        const short *pcm_data,
        int sample_count,
        int sample_rate
);

OPENYAP_API void OPENYAP_CALL openyap_vad_reset(void);

OPENYAP_API float OPENYAP_CALL openyap_amplitude(
        const short *pcm_data,
        int sample_count
);

#ifdef _WIN32
/// Pastes `text` into the active window by writing it to the clipboard and
/// simulating Ctrl+V.  If `restore_clipboard` is non-zero the previous
/// clipboard text is saved and restored after a brief delay.
///
/// Windows-only: uses wchar_t because the underlying Win32 clipboard and
/// SendInput APIs operate on wide strings.  Not available on other platforms.
OPENYAP_API int OPENYAP_CALL openyap_paste_text(
        const wchar_t *text,
        int restore_clipboard
);
#endif

OPENYAP_API const char *OPENYAP_CALL openyap_last_error(void);
