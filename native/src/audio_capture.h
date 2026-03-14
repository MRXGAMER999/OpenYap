#pragma once

#include <string>
#include <vector>

#include "openyap_native.h"

namespace openyap::capture {

    struct DeviceInfo {
        std::string id;
        std::string name;
        bool is_default;
    };

    int list_devices(std::vector <DeviceInfo> *devices, std::string *error);

    int start(int sample_rate, int channels, audio_callback_t callback, void *user_data,
            const char *device_id, std::string *error);

    int stop(bool *device_disconnected, std::string *error);

    void shutdown();

}  // namespace openyap::capture
