#include "audio_capture.h"

#include "noise_suppressor.h"

#include <Audioclient.h>
#include <Audiopolicy.h>
#include <Mmdeviceapi.h>
#include <wrl/client.h>

#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

    using Microsoft::WRL::ComPtr;

    std::string disconnect_reason_message(AudioSessionDisconnectReason reason) {
        switch (reason) {
            case DisconnectReasonDeviceRemoval:
                return "Microphone disconnected during capture.";
            case DisconnectReasonServerShutdown:
                return "Windows audio service stopped during capture.";
            case DisconnectReasonFormatChanged:
                return "Microphone format changed during capture.";
            case DisconnectReasonSessionLogoff:
                return "User session changed during capture.";
            case DisconnectReasonSessionDisconnected:
                return "Audio session disconnected during capture.";
            case DisconnectReasonExclusiveModeOverride:
                return "Exclusive-mode client interrupted microphone capture.";
            default:
                return "Audio session disconnected during capture.";
        }
    }

    class SessionDisconnectEvents final : public IAudioSessionEvents {
    public:
        SessionDisconnectEvents(
                std::atomic<bool> *disconnected,
                HANDLE wake_event,
                std::string *disconnect_message,
                std::mutex *disconnect_mutex
        )
                : ref_count_(1),
                  disconnected_(disconnected),
                  wake_event_(wake_event),
                  disconnect_message_(disconnect_message),
                  disconnect_mutex_(disconnect_mutex) {
        }

        STDMETHODIMP QueryInterface(REFIID riid, void **object) override {
            if (object == nullptr) {
                return E_POINTER;
            }
            if (riid == __uuidof(IUnknown) || riid == __uuidof(IAudioSessionEvents)) {
                *object = static_cast<IAudioSessionEvents *>(this);
                AddRef();
                return S_OK;
            }
            *object = nullptr;
            return E_NOINTERFACE;
        }

        STDMETHODIMP_(ULONG)

        AddRef() override {
            return static_cast<ULONG>(InterlockedIncrement(&ref_count_));
        }

        STDMETHODIMP_(ULONG)

        Release() override {
            const ULONG count = static_cast<ULONG>(InterlockedDecrement(&ref_count_));
            if (count == 0) {
                delete this;
            }
            return count;
        }

        STDMETHODIMP OnDisplayNameChanged(LPCWSTR, LPCGUID) override {
            return S_OK;
        }

        STDMETHODIMP OnIconPathChanged(LPCWSTR, LPCGUID) override {
            return S_OK;
        }

        STDMETHODIMP OnSimpleVolumeChanged(float, BOOL, LPCGUID) override {
            return S_OK;
        }

        STDMETHODIMP OnChannelVolumeChanged(DWORD, float[], DWORD, LPCGUID) override {
            return S_OK;
        }

        STDMETHODIMP OnGroupingParamChanged(LPCGUID, LPCGUID) override {
            return S_OK;
        }

        STDMETHODIMP OnStateChanged(AudioSessionState) override {
            return S_OK;
        }

        STDMETHODIMP OnSessionDisconnected(AudioSessionDisconnectReason reason) override {
            if (disconnected_ != nullptr) {
                disconnected_->store(true);
            }
            if (disconnect_message_ != nullptr && disconnect_mutex_ != nullptr) {
                std::lock_guard <std::mutex> lock(*disconnect_mutex_);
                *disconnect_message_ = disconnect_reason_message(reason);
            }
            if (wake_event_ != nullptr) {
                SetEvent(wake_event_);
            }
            return S_OK;
        }

    private:
        volatile LONG ref_count_;
        std::atomic<bool> *disconnected_;
        HANDLE wake_event_;
        std::string *disconnect_message_;
        std::mutex *disconnect_mutex_;
    };

    struct CaptureContext {
        std::mutex mutex;
        std::thread worker;
        HANDLE stop_event = nullptr;
        HANDLE audio_event = nullptr;
        HANDLE startup_event = nullptr;
        audio_callback_t callback = nullptr;
        void *user_data = nullptr;
        int start_result = 0;
        std::string start_error;
        std::string disconnect_message;
        std::atomic<bool> active{false};
        std::atomic<bool> stop_requested{false};
        std::atomic<bool> device_disconnected{false};
        bool disconnect_result_pending = false;
    };

    CaptureContext g_capture;

    std::string hresult_message(const char *message, HRESULT hr) {
        char buffer[128];
        std::snprintf(buffer, sizeof(buffer), "%s (HRESULT=0x%08lX)", message, static_cast<unsigned long>(hr));
        return std::string(buffer);
    }

    void close_handle(HANDLE &handle) {
        if (handle != nullptr) {
            CloseHandle(handle);
            handle = nullptr;
        }
    }

    void store_disconnect_message(const std::string &message) {
        std::lock_guard <std::mutex> lock(g_capture.mutex);
        g_capture.disconnect_message = message;
    }

    void set_start_failure(int code, std::string message) {
        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            g_capture.start_result = code;
            g_capture.start_error = std::move(message);
        }
        if (g_capture.startup_event != nullptr) {
            SetEvent(g_capture.startup_event);
        }
    }

    void capture_worker() {
        const HRESULT com_result = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        const bool should_uninitialize = SUCCEEDED(com_result);
        if (FAILED(com_result) && com_result != RPC_E_CHANGED_MODE) {
            set_start_failure(-2, "WASAPI thread COM initialization failed.");
            return;
        }

        ComPtr <IMMDeviceEnumerator> enumerator;
        HRESULT hr = CoCreateInstance(
                __uuidof(MMDeviceEnumerator),
                nullptr,
                CLSCTX_ALL,
                IID_PPV_ARGS(&enumerator)
        );
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to create MMDeviceEnumerator.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        ComPtr <IMMDevice> device;
        hr = enumerator->GetDefaultAudioEndpoint(eCapture, eConsole, &device);
        if (hr == E_NOTFOUND) {
            set_start_failure(-4, "No audio input device is available.");
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to resolve the default microphone.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        ComPtr <IAudioClient2> audio_client;
        hr = device->Activate(__uuidof(IAudioClient2), CLSCTX_ALL, nullptr, reinterpret_cast<void **>(audio_client.GetAddressOf()));
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to activate IAudioClient2.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        std::string diagnostics;
        const HRESULT communications_result = openyap::noise::configure_communications_mode(
                audio_client.Get(),
                device.Get(),
                &diagnostics
        );
        if (!diagnostics.empty()) {
            std::fprintf(stderr, "openyap_native: %s\n", diagnostics.c_str());
        }
        if (FAILED(communications_result)) {
            std::fprintf(stderr, "openyap_native: communications category unavailable; continuing without APO hint.\n");
        }

        WAVEFORMATEX target_format{};
        target_format.wFormatTag = WAVE_FORMAT_PCM;
        target_format.nChannels = 1;
        target_format.nSamplesPerSec = 48000;
        target_format.wBitsPerSample = 16;
        target_format.nBlockAlign = static_cast<WORD>(target_format.nChannels * (target_format.wBitsPerSample / 8));
        target_format.nAvgBytesPerSec = target_format.nSamplesPerSec * target_format.nBlockAlign;
        target_format.cbSize = 0;

        constexpr
        REFERENCE_TIME buffer_duration = 200000;
        hr = audio_client->Initialize(
                AUDCLNT_SHAREMODE_SHARED,
                AUDCLNT_STREAMFLAGS_EVENTCALLBACK |
                        AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM |
                        AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
                buffer_duration,
                0,
                &target_format,
                nullptr
        );
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to initialize the WASAPI capture client.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        hr = audio_client->SetEventHandle(g_capture.audio_event);
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to assign the WASAPI capture event handle.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        ComPtr <IAudioCaptureClient> capture_client;
        hr = audio_client->GetService(__uuidof(IAudioCaptureClient), reinterpret_cast<void **>(capture_client.GetAddressOf()));
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to acquire IAudioCaptureClient.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        ComPtr <IAudioSessionControl> session_control;
        hr = audio_client->GetService(__uuidof(IAudioSessionControl), reinterpret_cast<void **>(session_control.GetAddressOf()));
        SessionDisconnectEvents *session_events = nullptr;
        if (SUCCEEDED(hr)) {
            session_events = new SessionDisconnectEvents(
                    &g_capture.device_disconnected,
                    g_capture.audio_event,
                    &g_capture.disconnect_message,
                    &g_capture.mutex
            );
            const HRESULT register_result = session_control->RegisterAudioSessionNotification(session_events);
            if (FAILED(register_result)) {
                std::fprintf(stderr, "openyap_native: failed to register audio session callbacks (HRESULT=0x%08lX).\n", static_cast<unsigned long>(register_result));
                session_events->Release();
                session_events = nullptr;
            }
        }

        hr = audio_client->Start();
        if (FAILED(hr)) {
            if (session_events != nullptr) {
                session_control->UnregisterAudioSessionNotification(session_events);
                session_events->Release();
            }
            set_start_failure(-2, hresult_message("Failed to start WASAPI capture.", hr));
            if (should_uninitialize) {
                CoUninitialize();
            }
            return;
        }

        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            g_capture.start_result = 0;
            g_capture.start_error.clear();
        }
        g_capture.active.store(true);
        SetEvent(g_capture.startup_event);

        HANDLE wait_handles[] = {g_capture.stop_event, g_capture.audio_event};
        bool disconnect_detected = false;
        bool capture_failed = false;

        while (!g_capture.stop_requested.load()) {
            const DWORD wait_result = WaitForMultipleObjects(2, wait_handles, FALSE, 250);
            if (wait_result == WAIT_OBJECT_0) {
                break;
            }
            if (g_capture.device_disconnected.load()) {
                disconnect_detected = true;
                break;
            }
            if (wait_result != WAIT_OBJECT_0 + 1) {
                continue;
            }

            while (!g_capture.stop_requested.load()) {
                UINT32 next_packet_frames = 0;
                hr = capture_client->GetNextPacketSize(&next_packet_frames);
                if (hr == AUDCLNT_E_DEVICE_INVALIDATED || hr == AUDCLNT_E_SERVICE_NOT_RUNNING) {
                    disconnect_detected = true;
                    store_disconnect_message(
                            hr == AUDCLNT_E_SERVICE_NOT_RUNNING
                                    ? "Windows audio service stopped during capture."
                                    : "Microphone disconnected during capture."
                    );
                    g_capture.device_disconnected.store(true);
                    break;
                }
                if (FAILED(hr)) {
                    capture_failed = true;
                    store_disconnect_message(hresult_message("GetNextPacketSize failed.", hr));
                    break;
                }
                if (next_packet_frames == 0) {
                    break;
                }

                BYTE *data = nullptr;
                UINT32 frames_available = 0;
                DWORD flags = 0;
                hr = capture_client->GetBuffer(&data, &frames_available, &flags, nullptr, nullptr);
                if (hr == AUDCLNT_E_DEVICE_INVALIDATED || hr == AUDCLNT_E_SERVICE_NOT_RUNNING) {
                    disconnect_detected = true;
                    store_disconnect_message(
                            hr == AUDCLNT_E_SERVICE_NOT_RUNNING
                                    ? "Windows audio service stopped during capture."
                                    : "Microphone disconnected during capture."
                    );
                    g_capture.device_disconnected.store(true);
                    break;
                }
                if (FAILED(hr)) {
                    capture_failed = true;
                    store_disconnect_message(hresult_message("GetBuffer failed.", hr));
                    break;
                }

                const int sample_count = static_cast<int>(frames_available * target_format.nChannels);
                if ((flags & AUDCLNT_BUFFERFLAGS_SILENT) != 0 || data == nullptr) {
                    std::vector<short> silent_chunk(static_cast<size_t>(sample_count), 0);
                    g_capture.callback(silent_chunk.data(), sample_count, g_capture.user_data);
                } else {
                    g_capture.callback(reinterpret_cast<const short *>(data), sample_count, g_capture.user_data);
                }

                capture_client->ReleaseBuffer(frames_available);
            }

            if (disconnect_detected || capture_failed) {
                break;
            }
        }

        audio_client->Stop();

        if (session_events != nullptr) {
            session_control->UnregisterAudioSessionNotification(session_events);
            session_events->Release();
        }

        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            g_capture.active.store(false);
            g_capture.disconnect_result_pending = disconnect_detected || g_capture.device_disconnected.load();
            if (capture_failed && g_capture.disconnect_message.empty()) {
                g_capture.disconnect_message = "Audio capture stopped unexpectedly.";
                g_capture.disconnect_result_pending = true;
            }
        }

        if (should_uninitialize) {
            CoUninitialize();
        }
    }

}  // namespace

namespace openyap::capture {

    int start(int sample_rate, int channels, audio_callback_t callback, void *user_data, std::string *error) {
        if (sample_rate != 48000 || channels != 1 || callback == nullptr) {
            if (error != nullptr) {
                *error = "Native capture requires 48000 Hz mono audio and a non-null callback.";
            }
            return -3;
        }

        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            if (g_capture.active.load() || g_capture.worker.joinable()) {
                if (error != nullptr) {
                    *error = "A capture session is already active.";
                }
                return -1;
            }

            g_capture.callback = callback;
            g_capture.user_data = user_data;
            g_capture.start_result = 0;
            g_capture.start_error.clear();
            g_capture.disconnect_message.clear();
            g_capture.stop_requested.store(false);
            g_capture.device_disconnected.store(false);
            g_capture.disconnect_result_pending = false;
            g_capture.stop_event = CreateEventW(nullptr, TRUE, FALSE, nullptr);
            g_capture.audio_event = CreateEventW(nullptr, FALSE, FALSE, nullptr);
            g_capture.startup_event = CreateEventW(nullptr, TRUE, FALSE, nullptr);

            if (g_capture.stop_event == nullptr || g_capture.audio_event == nullptr || g_capture.startup_event == nullptr) {
                close_handle(g_capture.stop_event);
                close_handle(g_capture.audio_event);
                close_handle(g_capture.startup_event);
                if (error != nullptr) {
                    *error = "Failed to create native capture synchronization handles.";
                }
                return -2;
            }

            g_capture.worker = std::thread(capture_worker);
        }

        const DWORD wait_result = WaitForSingleObject(g_capture.startup_event, 10000);
        if (wait_result != WAIT_OBJECT_0) {
            bool ignored = false;
            std::string ignored_error;
            stop(&ignored, &ignored_error);
            if (error != nullptr) {
                *error = "Timed out while starting the native audio capture thread.";
            }
            return -2;
        }

        std::thread worker_to_join;
        std::string start_error;
        int start_result = 0;

        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            start_result = g_capture.start_result;
            start_error = g_capture.start_error;
            if (start_result != 0 && g_capture.worker.joinable()) {
                worker_to_join = std::move(g_capture.worker);
            }
            if (start_result != 0) {
                close_handle(g_capture.stop_event);
                close_handle(g_capture.audio_event);
                close_handle(g_capture.startup_event);
                g_capture.callback = nullptr;
                g_capture.user_data = nullptr;
            }
        }

        if (worker_to_join.joinable()) {
            worker_to_join.join();
        }

        if (start_result != 0) {
            if (error != nullptr) {
                *error = start_error;
            }
            return start_result;
        }

        return 0;
    }

    int stop(bool *device_disconnected, std::string *error) {
        std::thread worker_to_join;
        HANDLE stop_event = nullptr;
        HANDLE audio_event = nullptr;
        HANDLE startup_event = nullptr;

        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            if (!g_capture.active.load() && !g_capture.worker.joinable()) {
                if (g_capture.disconnect_result_pending) {
                    if (device_disconnected != nullptr) {
                        *device_disconnected = true;
                    }
                    if (error != nullptr) {
                        *error = g_capture.disconnect_message;
                    }
                    g_capture.disconnect_result_pending = false;
                    return -2;
                }
                if (error != nullptr) {
                    *error = "No capture session is active.";
                }
                return -1;
            }

            g_capture.stop_requested.store(true);
            if (g_capture.stop_event != nullptr) {
                SetEvent(g_capture.stop_event);
            }

            if (g_capture.worker.joinable()) {
                worker_to_join = std::move(g_capture.worker);
            }

            stop_event = g_capture.stop_event;
            audio_event = g_capture.audio_event;
            startup_event = g_capture.startup_event;
            g_capture.stop_event = nullptr;
            g_capture.audio_event = nullptr;
            g_capture.startup_event = nullptr;
        }

        if (worker_to_join.joinable()) {
            worker_to_join.join();
        }

        close_handle(stop_event);
        close_handle(audio_event);
        close_handle(startup_event);

        bool disconnected = false;
        std::string disconnect_message;

        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            disconnected = g_capture.disconnect_result_pending;
            disconnect_message = g_capture.disconnect_message;
            g_capture.disconnect_result_pending = false;
            g_capture.callback = nullptr;
            g_capture.user_data = nullptr;
            g_capture.active.store(false);
            g_capture.stop_requested.store(false);
            g_capture.device_disconnected.store(false);
        }

        if (device_disconnected != nullptr) {
            *device_disconnected = disconnected;
        }
        if (disconnected && error != nullptr) {
            *error = disconnect_message;
        }

        return disconnected ? -2 : 0;
    }

    void shutdown() {
        bool ignored_disconnected = false;
        std::string ignored_error;
        stop(&ignored_disconnected, &ignored_error);
    }

}  // namespace openyap::capture
