#pragma once

#include <string>

#include "openyap_native.h"

namespace openyap::capture {

    int start(int sample_rate, int channels, audio_callback_t callback, void *user_data, std::string *error);

    int stop(bool *device_disconnected, std::string *error);

    void shutdown();

}  // namespace openyap::capture
