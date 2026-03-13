#include "audio_encoder.h"

#include <windows.h>

#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <wrl/client.h>
#include <wmcodecdsp.h>

#include <algorithm>
#include <cstring>
#include <cstdio>
#include <filesystem>
#include <mutex>
#include <string>

namespace {

    using Microsoft::WRL::ComPtr;

    std::mutex g_encode_mutex;

    std::wstring utf8_to_wide(const char *text) {
        if (text == nullptr || *text == '\0') {
            return {};
        }

        const int required = MultiByteToWideChar(CP_UTF8, 0, text, -1, nullptr, 0);
        if (required <= 0) {
            return {};
        }

        std::wstring wide(static_cast<size_t>(required), L'\0');
        MultiByteToWideChar(CP_UTF8, 0, text, -1, wide.data(), required);
        wide.pop_back();
        return wide;
    }

    std::string hresult_message(const char *message, HRESULT hr) {
        char buffer[128];
        std::snprintf(buffer, sizeof(buffer), "%s (HRESULT=0x%08lX)", message, static_cast<unsigned long>(hr));
        return std::string(buffer);
    }

}  // namespace

namespace openyap::encoder {

    int encode_aac_to_file(
            const short *pcm_data,
            int pcm_sample_count,
            int sample_rate,
            int channels,
            int bitrate,
            const char *output_path,
            std::string *error
    ) {
        if (pcm_data == nullptr || pcm_sample_count <= 0 || output_path == nullptr || channels != 1 ||
                (sample_rate != 44100 && sample_rate != 48000)) {
            if (error != nullptr) {
                *error = "Invalid AAC encoding parameters.";
            }
            return -2;
        }

        const std::wstring output_path_wide = utf8_to_wide(output_path);
        if (output_path_wide.empty()) {
            if (error != nullptr) {
                *error = "Output path must be valid UTF-8.";
            }
            return -2;
        }

        const std::filesystem::path path(output_path_wide);
        if (!path.parent_path().empty() && !std::filesystem::exists(path.parent_path())) {
            if (error != nullptr) {
                *error = "AAC output directory does not exist.";
            }
            return -4;
        }

        std::lock_guard <std::mutex> lock(g_encode_mutex);

        const HRESULT com_result = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        const bool should_uninitialize = SUCCEEDED(com_result);
        if (FAILED(com_result) && com_result != RPC_E_CHANGED_MODE) {
            if (error != nullptr) {
                *error = "Encoder thread COM initialization failed.";
            }
            return -1;
        }

        const UINT32 clamped_bitrate = static_cast<UINT32>(std::max(bitrate, 96000));
        const UINT32 avg_bytes_per_second = clamped_bitrate / 8;
        const UINT32 block_alignment = static_cast<UINT32>(channels * sizeof(short));
        const UINT32 pcm_bytes = static_cast<UINT32>(pcm_sample_count * sizeof(short));
        const LONGLONG sample_duration = (static_cast<LONGLONG>(pcm_sample_count) * 10000000LL) /
                static_cast<LONGLONG>(sample_rate * channels);

        ComPtr <IMFSinkWriter> sink_writer;
        HRESULT hr = MFCreateSinkWriterFromURL(output_path_wide.c_str(), nullptr, nullptr, &sink_writer);
        if (FAILED(hr)) {
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to create the AAC sink writer.", hr);
            }
            return -1;
        }

        ComPtr <IMFMediaType> output_type;
        hr = MFCreateMediaType(&output_type);
        if (FAILED(hr)) {
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to allocate AAC output media type.", hr);
            }
            return -1;
        }

        output_type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
        output_type->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_AAC);
        output_type->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
        output_type->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, static_cast<UINT32>(sample_rate));
        output_type->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, static_cast<UINT32>(channels));
        output_type->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, avg_bytes_per_second);
        output_type->SetUINT32(MF_MT_AAC_PAYLOAD_TYPE, 0);
        output_type->SetUINT32(MF_MT_AAC_AUDIO_PROFILE_LEVEL_INDICATION, 0x29);

        DWORD stream_index = 0;
        hr = sink_writer->AddStream(output_type.Get(), &stream_index);
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to add the AAC output stream.", hr);
            }
            return -1;
        }

        ComPtr <IMFMediaType> input_type;
        hr = MFCreateMediaType(&input_type);
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to allocate PCM input media type.", hr);
            }
            return -1;
        }

        input_type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
        input_type->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM);
        input_type->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
        input_type->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, static_cast<UINT32>(sample_rate));
        input_type->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, static_cast<UINT32>(channels));
        input_type->SetUINT32(MF_MT_AUDIO_BLOCK_ALIGNMENT, block_alignment);
        input_type->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, static_cast<UINT32>(sample_rate) * block_alignment);
        input_type->SetUINT32(MF_MT_ALL_SAMPLES_INDEPENDENT, TRUE);

        hr = sink_writer->SetInputMediaType(stream_index, input_type.Get(), nullptr);
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to bind the PCM input format to the AAC writer.", hr);
            }
            return -1;
        }

        hr = sink_writer->BeginWriting();
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to start the AAC sink writer.", hr);
            }
            return -3;
        }

        ComPtr <IMFMediaBuffer> media_buffer;
        hr = MFCreateMemoryBuffer(pcm_bytes, &media_buffer);
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to allocate the PCM media buffer.", hr);
            }
            return -3;
        }

        BYTE *destination = nullptr;
        hr = media_buffer->Lock(&destination, nullptr, nullptr);
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to lock the PCM media buffer.", hr);
            }
            return -3;
        }

        std::memcpy(destination, pcm_data, pcm_bytes);
        media_buffer->Unlock();
        media_buffer->SetCurrentLength(pcm_bytes);

        ComPtr <IMFSample> sample;
        hr = MFCreateSample(&sample);
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to allocate the PCM sample wrapper.", hr);
            }
            return -3;
        }

        sample->AddBuffer(media_buffer.Get());
        sample->SetSampleTime(0);
        sample->SetSampleDuration(sample_duration);

        hr = sink_writer->WriteSample(stream_index, sample.Get());
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Media Foundation rejected the AAC sample.", hr);
            }
            return -3;
        }

        hr = sink_writer->Finalize();
        if (FAILED(hr)) {
            DeleteFileW(output_path_wide.c_str());
            if (should_uninitialize) {
                CoUninitialize();
            }
            if (error != nullptr) {
                *error = hresult_message("Failed to finalize the AAC output file.", hr);
            }
            return -4;
        }

        if (should_uninitialize) {
            CoUninitialize();
        }

        return 0;
    }

}  // namespace openyap::encoder
