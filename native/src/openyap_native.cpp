#include "openyap_native.h"

#include "audio_capture.h"
#include "audio_encoder.h"
#include "hotkey_manager.h"
#include "paste_automation.h"
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
        std::lock_guard<std::mutex> lock(g_error_mutex);
        g_last_error.clear();
    }

    void set_last_error(std::string message) {
        std::lock_guard<std::mutex> lock(g_error_mutex);
        g_last_error = std::move(message);
    }

    void log_message(const char *message) {
        if (message == nullptr || *message == '\0') {
            return;
        }
        std::fprintf(stderr, "%s\n", message);
    }

    // Simple JSON escaping for device strings
    std::string json_escape(const std::string &input) {
        std::string result;
        result.reserve(input.size() + 8);
        for (char ch : input) {
            switch (ch) {
                case '"':  result += "\\\""; break;
                case '\\': result += "\\\\"; break;
                case '\b': result += "\\b";  break;
                case '\f': result += "\\f";  break;
                case '\n': result += "\\n";  break;
                case '\r': result += "\\r";  break;
                case '\t': result += "\\t";  break;
                default:
                    if (static_cast<unsigned char>(ch) < 0x20) {
                        char buf[8];
                        std::snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned>(ch));
                        result += buf;
                    } else {
                        result += ch;
                    }
                    break;
            }
        }
        return result;
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
    openyap::hotkey::manager().shutdown();
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
    const int result = openyap::capture::start(sample_rate, channels, callback, user_data, nullptr, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_capture_start_device(
        int sample_rate,
        int channels,
        audio_callback_t callback,
        void *user_data,
        const char *device_id
) {
    clear_last_error();

    std::string error;
    const int result = openyap::capture::start(sample_rate, channels, callback, user_data, device_id, &error);
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

const char *OPENYAP_CALL

openyap_list_devices(void) {
    clear_last_error();

    std::vector<openyap::capture::DeviceInfo> devices;
    std::string error;
    const int result = openyap::capture::list_devices(&devices, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
        return nullptr;
    }

    // Build JSON array
    std::string json = "[";
    for (size_t i = 0; i < devices.size(); ++i) {
        if (i > 0) json += ",";
        json += "{\"id\":\"";
        json += json_escape(devices[i].id);
        json += "\",\"name\":\"";
        json += json_escape(devices[i].name);
        json += "\",\"is_default\":";
        json += devices[i].is_default ? "true" : "false";
        json += "}";
    }
    json += "]";

    // Allocate a copy the caller must free with openyap_free_string
    char *copy = static_cast<char *>(std::malloc(json.size() + 1));
    if (copy == nullptr) {
        set_last_error("Failed to allocate memory for device list.");
        return nullptr;
    }
    std::memcpy(copy, json.c_str(), json.size() + 1);
    return copy;
}

void OPENYAP_CALL

openyap_free_string(const char *str) {
    std::free(const_cast<char *>(str));
}

int OPENYAP_CALL

openyap_hotkey_set_config(
        int key_code,
        unsigned int modifiers_mask,
        int enabled
) {
    clear_last_error();

    std::string error;
    openyap::hotkey::HotkeyBinding binding;
    binding.key_code = key_code;
    binding.modifiers = modifiers_mask;
    binding.enabled = enabled != 0 && key_code != 0;
    const int result = openyap::hotkey::manager().set_config(binding, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_hotkey_start_listening(
        hotkey_event_callback_t callback,
        void *user_data
) {
    clear_last_error();

    std::string error;
    const int result = openyap::hotkey::manager().start_listening(callback, user_data, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_hotkey_stop_listening(void) {
    clear_last_error();

    std::string error;
    const int result = openyap::hotkey::manager().stop_listening(&error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_hotkey_begin_capture(void) {
    clear_last_error();

    std::string error;
    const int result = openyap::hotkey::manager().begin_capture(&error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

int OPENYAP_CALL

openyap_hotkey_cancel_capture(void) {
    clear_last_error();

    std::string error;
    const int result = openyap::hotkey::manager().cancel_capture(&error);
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

void OPENYAP_CALL

openyap_vad_reset(void) {
    openyap::vad::reset();
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

int OPENYAP_CALL

openyap_paste_text(const wchar_t *text, int restore_clipboard) {
    clear_last_error();

    std::string error;
    const int result = openyap::paste::paste_text(text, restore_clipboard != 0, &error);
    if (result != 0) {
        set_last_error(error);
        log_message(error.c_str());
    }
    return result;
}

const char *OPENYAP_CALL

openyap_last_error(void) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    return g_last_error.empty() ? nullptr : g_last_error.c_str();
}
