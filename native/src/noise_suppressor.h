#pragma once

#include <windows.h>

#include <string>

struct IAudioClient2;
struct IMMDevice;

namespace openyap::noise {

HRESULT configure_communications_mode(IAudioClient2* audio_client, IMMDevice* device, std::string* diagnostics);

}  // namespace openyap::noise
