#pragma once

#include <string>

namespace openyap::vad {

int is_speech(const short* pcm_data, int sample_count, int sample_rate, std::string* error);

}  // namespace openyap::vad
