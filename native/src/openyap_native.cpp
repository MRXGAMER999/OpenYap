#include "openyap_native.h"

#include "audio_capture.h"
#include "audio_encoder.h"
#include "vad.h"

#include <mfapi.h>
#include <objbase.h>

#include <climits>
#include <cmath>
#include <cstdio>
#include <mutex>
#include <string>

namespace {

    std::mutex g_error_mutex;
    std::string g_last_error;
    bool g_com_initialized = false;
    bool g_media_foundation_started = false;

    void clear_last_error() {
        std::lock_guard <std::mutex> lock(g_error_mutex);
        g_last_error.clear();
    }

    void set_last_error(std::string message) {
        std::lock_guard <std::mutex> lock(g_error_mutex);
        g_last_error = std::move(message);
    }

    void log_message(const char *message) {
        if (message == nullptr || *message == '\0') {
            return;
        }
        std::fprintf(stderr, "%s\n", message);
    }

}  // namespace

int OPENYAP_CALL

openyap_init(void) {
    clear_last_error();

    if (g_media_foundation_started) {
        return 0;
    }

    const HRESULT com_result = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    if (FAILED(com_result) && com_result != RPC_E_CHANGED_MODE) {
        set_last_error("COM initialization failed.");
        return -1;
    }
    g_com_initialized = SUCCEEDED(com_result);

    const HRESULT media_result = MFStartup(MF_VERSION);
    if (FAILED(media_result)) {
        if (g_com_initialized) {
            CoUninitialize();
            g_com_initialized = false;
        }
        set_last_error("Media Foundation initialization failed.");
        return -2;
    }

    g_media_foundation_started = true;
    return 0;
}

void OPENYAP_CALL

openyap_shutdown(void) {
    openyap::capture::shutdown();

    if (g_media_foundation_started) {
        MFShutdown();
        g_media_foundation_started = false;
    }

    if (g_com_initialized) {
        CoUninitialize();
        g_com_initialized = false;
    }

    clear_last_error();
}

int OPENYAP_CALL

openyap_capture_start(
        int sample_rate,
        int channels,
        audio_callback_t callback,
        void *user_data
) {
    clear_last_error();

    std::string error;
    const int result = openyap::capture::start(sample_rate, channels, callback, user_data, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_capture_stop(void) {
    clear_last_error();

    bool device_disconnected = false;
    std::string error;
    const int result = openyap::capture::stop(&device_disconnected, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_encode_aac(
        const short *pcm_data,
        int pcm_sample_count,
        int sample_rate,
        int channels,
        int bitrate,
        const char *output_path
) {
    clear_last_error();

    std::string error;
    const int result = openyap::encoder::encode_aac_to_file(
            pcm_data,
            pcm_sample_count,
            sample_rate,
            channels,
            bitrate,
            output_path,
            &error
    );
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_vad_is_speech(
        const short *pcm_data,
        int sample_count,
        int sample_rate
) {
    clear_last_error();

    std::string error;
    const int result = openyap::vad::is_speech(pcm_data, sample_count, sample_rate, &error);
    if (result < 0) {
        set_last_error(error);
    }
    return result;
}

float OPENYAP_CALL

openyap_amplitude(const short *pcm_data, int sample_count) {
    if (pcm_data == nullptr || sample_count <= 0) {
        return 0.0f;
    }

    int peak = 0;
    for (int index = 0; index < sample_count; ++index) {
        const int sample = static_cast<int>(pcm_data[index]);
        const int magnitude = sample == SHRT_MIN ? 32768 : std::abs(sample);
        if (magnitude > peak) {
            peak = magnitude;
        }
    }

    return static_cast<float>(peak) / 32768.0f;
}

const char *OPENYAP_CALL

openyap_last_error(void) {
    std::lock_guard <std::mutex> lock(g_error_mutex);
    return g_last_error.empty() ? nullptr : g_last_error.c_str();
}
