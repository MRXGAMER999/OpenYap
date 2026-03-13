#include "noise_suppressor.h"

#include <Audioclient.h>

namespace openyap::noise {

HRESULT configure_communications_mode(IAudioClient2* audio_client, IMMDevice*, std::string* diagnostics) {
    if (audio_client == nullptr) {
        return E_POINTER;
    }

    AudioClientProperties properties{};
    properties.cbSize = sizeof(properties);
    properties.bIsOffload = FALSE;
    properties.eCategory = AudioCategory_Communications;

    const HRESULT hr = audio_client->SetClientProperties(&properties);
    if (diagnostics != nullptr) {
        if (SUCCEEDED(hr)) {
            *diagnostics = "AudioCategory_Communications enabled; Windows APO noise suppression will apply when the endpoint supports it.";
        } else {
            *diagnostics = "AudioCategory_Communications could not be applied; native capture will continue without explicit APO tuning.";
        }
    }

    return hr;
}

}  // namespace openyap::noise
