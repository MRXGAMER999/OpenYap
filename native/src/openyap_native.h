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

OPENYAP_API int OPENYAP_CALL openyap_init(void);
OPENYAP_API void OPENYAP_CALL openyap_shutdown(void);

OPENYAP_API int OPENYAP_CALL openyap_capture_start(
        int sample_rate,
        int channels,
        audio_callback_t callback,
        void *user_data
);

OPENYAP_API int OPENYAP_CALL openyap_capture_stop(void);

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

OPENYAP_API float OPENYAP_CALL openyap_amplitude(
        const short *pcm_data,
        int sample_count
);

OPENYAP_API const char *OPENYAP_CALL openyap_last_error(void);
