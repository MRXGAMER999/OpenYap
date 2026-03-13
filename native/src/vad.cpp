#include "vad.h"

#include <cmath>
#include <mutex>

namespace {

std::mutex g_vad_mutex;
double g_noise_floor = 0.0;
bool g_noise_floor_initialized = false;

}  // namespace

namespace openyap::vad {

int is_speech(const short* pcm_data, int sample_count, int sample_rate, std::string* error) {
    if (pcm_data == nullptr || sample_count <= 0 || sample_rate <= 0) {
        if (error != nullptr) {
            *error = "Invalid VAD frame parameters.";
        }
        return -1;
    }

    double sum_squares = 0.0;
    for (int index = 0; index < sample_count; ++index) {
        const double normalized = static_cast<double>(pcm_data[index]) / 32768.0;
        sum_squares += normalized * normalized;
    }

    const double rms = std::sqrt(sum_squares / static_cast<double>(sample_count));

    std::lock_guard<std::mutex> lock(g_vad_mutex);
    if (!g_noise_floor_initialized) {
        g_noise_floor = rms;
        g_noise_floor_initialized = true;
    } else if (rms < g_noise_floor) {
        g_noise_floor = (g_noise_floor * 0.8) + (rms * 0.2);
    } else {
        g_noise_floor = (g_noise_floor * 0.995) + (rms * 0.005);
    }

    const double threshold = std::max(0.015, g_noise_floor * 3.0);
    return rms >= threshold ? 1 : 0;
}

}  // namespace openyap::vad
