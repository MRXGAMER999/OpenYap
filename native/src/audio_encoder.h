#pragma once

#include <string>

namespace openyap::encoder {

    int encode_aac_to_file(
            const short *pcm_data,
            int pcm_sample_count,
            int sample_rate,
            int channels,
            int bitrate,
            const char *output_path,
            std::string *error
    );

}  // namespace openyap::encoder
