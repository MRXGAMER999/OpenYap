#include "audio_capture.h"

#include "noise_suppressor.h"

#include <Audioclient.h>
#include <Audiopolicy.h>
#include <avrt.h>
#include <Mmdeviceapi.h>
#include <wrl/client.h>

// PKEY_Device_FriendlyName — avoids pulling in Functiondiscoverykeys_devpkey.h
// which can cause LSP/build issues depending on include order.
// {a45c254e-df1c-4efd-8020-67d146a850e0}, pid 14
static const PROPERTYKEY OPENYAP_PKEY_FriendlyName = {
        {0xa45c254e, 0xdf1c, 0x4efd, {0x80, 0x20, 0x67, 0xd1, 0x46, 0xa8, 0x50, 0xe0}},
        14
};

#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

    using Microsoft::WRL::ComPtr;

    // ── Helpers ──────────────────────────────────────────────────────────

    std::string narrow(const wchar_t *wide) {
        if (wide == nullptr) {
            return {};
        }
        const int len = WideCharToMultiByte(CP_UTF8, 0, wide, -1, nullptr, 0, nullptr, nullptr);
        if (len <= 0) {
            return {};
        }
        std::string result(static_cast<size_t>(len) - 1, '\0');
        WideCharToMultiByte(CP_UTF8, 0, wide, -1, &result[0], len, nullptr, nullptr);
        return result;
    }

    std::wstring widen(const char *utf8) {
        if (utf8 == nullptr || *utf8 == '\0') {
            return {};
        }
        const int len = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, nullptr, 0);
        if (len <= 0) {
            return {};
        }
        std::wstring result(static_cast<size_t>(len) - 1, L'\0');
        MultiByteToWideChar(CP_UTF8, 0, utf8, -1, &result[0], len);
        return result;
    }

    std::string hresult_message(const char *message, HRESULT hr) {
        char buffer[128];
        std::snprintf(buffer, sizeof(buffer), "%s (HRESULT=0x%08lX)", message, static_cast<unsigned long>(hr));
        return std::string(buffer);
    }

    const char *hresult_name(HRESULT hr) {
        switch (hr) {
            case AUDCLNT_E_INVALID_STREAM_FLAG:
                return "AUDCLNT_E_INVALID_STREAM_FLAG";
            case AUDCLNT_E_UNSUPPORTED_FORMAT:
                return "AUDCLNT_E_UNSUPPORTED_FORMAT";
            case AUDCLNT_E_INVALID_DEVICE_PERIOD:
                return "AUDCLNT_E_INVALID_DEVICE_PERIOD";
            case AUDCLNT_E_DEVICE_IN_USE:
                return "AUDCLNT_E_DEVICE_IN_USE";
            default:
                return nullptr;
        }
    }

    void close_handle(HANDLE &handle) {
        if (handle != nullptr) {
            CloseHandle(handle);
            handle = nullptr;
        }
    }

    // ── Disconnect reason ────────────────────────────────────────────────

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

    // ── Session disconnect listener ──────────────────────────────────────

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

    // ── Capture context (global singleton) ───────────────────────────────

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
        std::string device_id;  // empty = system default
        std::atomic<bool> active{false};
        std::atomic<bool> stop_requested{false};
        std::atomic<bool> device_disconnected{false};
        bool disconnect_result_pending = false;
    };

    CaptureContext g_capture;

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

    // ── Device resolution ────────────────────────────────────────────────

    HRESULT resolve_device(IMMDeviceEnumerator *enumerator, const std::string &device_id, IMMDevice **device) {
        if (device_id.empty()) {
            return enumerator->GetDefaultAudioEndpoint(eCapture, eConsole, device);
        }
        const std::wstring wide_id = widen(device_id.c_str());
        return enumerator->GetDevice(wide_id.c_str(), device);
    }

    // ── IAudioClient3 low-latency init (Phase 1) ────────────────────────
    //
    // Tries IAudioClient3::InitializeSharedAudioStream with the minimum
    // engine period.  Returns S_OK on success or an error HRESULT if the
    // caller should fall back to IAudioClient2::Initialize.

    HRESULT try_init_low_latency(
            IMMDevice *device,
            IAudioClient3 *client3,
            const WAVEFORMATEX *format,
            HANDLE audio_event
    ) {
        UINT32 default_period_frames = 0;
        UINT32 fundamental_period_frames = 0;
        UINT32 min_period_frames = 0;
        UINT32 max_period_frames = 0;

        HRESULT hr = client3->GetSharedModeEnginePeriod(
                format,
                &default_period_frames,
                &fundamental_period_frames,
                &min_period_frames,
                &max_period_frames
        );
        if (FAILED(hr)) {
            return hr;
        }

        // Use minimum period for lowest latency
        const UINT32 period = min_period_frames;

        hr = client3->InitializeSharedAudioStream(
                AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
                period,
                format,
                nullptr  // session GUID
        );
        if (FAILED(hr)) {
            return hr;
        }

        hr = client3->SetEventHandle(audio_event);
        if (FAILED(hr)) {
            return hr;
        }

        std::fprintf(stderr,
                "openyap_native: IAudioClient3 low-latency init succeeded (period=%u frames, min=%u, default=%u).\n",
                period, min_period_frames, default_period_frames);
        return S_OK;
    }

    // ── Deep noise suppression (Phase 2) ─────────────────────────────────
    //
    // After stream initialization, query IAudioEffectsManager and enable
    // AUDIO_EFFECT_TYPE_DEEP_NOISE_SUPPRESSION if available.

    void try_enable_deep_noise_suppression(IAudioClient *client) {
        ComPtr <IAudioEffectsManager> effects_manager;
        HRESULT hr = client->GetService(IID_PPV_ARGS(&effects_manager));
        if (FAILED(hr)) {
            std::fprintf(stderr, "openyap_native: IAudioEffectsManager not available (HRESULT=0x%08lX); "
                                 "deep noise suppression cannot be managed.\n", static_cast<unsigned long>(hr));
            return;
        }

        UINT32 num_effects = 0;
        AUDIO_EFFECT *effects = nullptr;
        hr = effects_manager->GetAudioEffects(&effects, &num_effects);
        if (FAILED(hr)) {
            std::fprintf(stderr, "openyap_native: GetAudioEffects failed (HRESULT=0x%08lX).\n",
                    static_cast<unsigned long>(hr));
            return;
        }

        bool found = false;
        for (UINT32 i = 0; i < num_effects; ++i) {
            if (effects[i].id == AUDIO_EFFECT_TYPE_DEEP_NOISE_SUPPRESSION) {
                found = true;
                if (effects[i].state == AUDIO_EFFECT_STATE_ON) {
                    std::fprintf(stderr, "openyap_native: deep noise suppression is already ON.\n");
                } else if (effects[i].canSetState) {
                    const HRESULT set_hr = effects_manager->SetAudioEffectState(
                            effects[i].id, AUDIO_EFFECT_STATE_ON);
                    if (SUCCEEDED(set_hr)) {
                        std::fprintf(stderr, "openyap_native: deep noise suppression enabled.\n");
                    } else {
                        std::fprintf(stderr, "openyap_native: failed to enable deep noise suppression "
                                             "(HRESULT=0x%08lX).\n", static_cast<unsigned long>(set_hr));
                    }
                } else {
                    std::fprintf(stderr, "openyap_native: deep noise suppression present but state is read-only.\n");
                }
                break;
            }
        }

        if (effects != nullptr) {
            CoTaskMemFree(effects);
        }

        if (!found) {
            std::fprintf(stderr, "openyap_native: deep noise suppression effect not available on this endpoint.\n");
        }
    }

    // ── Echo cancellation (Phase 4) ──────────────────────────────────────
    //
    // After stream initialization, query IAcousticEchoCancellationControl
    // and let Windows auto-select the render endpoint as the AEC reference.
    // Requires Windows Build 22621+.  E_NOINTERFACE is normal on older
    // builds or endpoints that don't expose controllable AEC.

    void try_configure_echo_cancellation(IAudioClient *client) {
        ComPtr <IAcousticEchoCancellationControl> aec_control;
        HRESULT hr = client->GetService(IID_PPV_ARGS(&aec_control));
        if (hr == E_NOINTERFACE) {
            std::fprintf(stderr, "openyap_native: IAcousticEchoCancellationControl not available; "
                                 "endpoint does not expose controllable AEC.\n");
            return;
        }
        if (FAILED(hr)) {
            std::fprintf(stderr, "openyap_native: IAcousticEchoCancellationControl query failed "
                                 "(HRESULT=0x%08lX).\n", static_cast<unsigned long>(hr));
            return;
        }

        // Pass NULL to let Windows pick the best render endpoint automatically
        hr = aec_control->SetEchoCancellationRenderEndpoint(nullptr);
        if (SUCCEEDED(hr)) {
            std::fprintf(stderr, "openyap_native: AEC reference set to Windows auto-selected render endpoint.\n");
        } else {
            std::fprintf(stderr, "openyap_native: SetEchoCancellationRenderEndpoint failed "
                                 "(HRESULT=0x%08lX).\n", static_cast<unsigned long>(hr));
        }
    }

    // ── Audio effects change monitor (Phase 5) ───────────────────────────
    //
    // Implements IAudioEffectsChangedNotificationClient.  When the system
    // notifies us that the effects list changed (e.g. a user toggled an
    // effect in Windows Sound settings), we re-query and re-enable deep
    // noise suppression if it was turned off.

    class EffectsChangedHandler final : public IAudioEffectsChangedNotificationClient {
    public:
        explicit EffectsChangedHandler(IAudioClient *client)
                : ref_count_(1), client_(client) {
        }

        STDMETHODIMP QueryInterface(REFIID riid, void **object) override {
            if (object == nullptr) return E_POINTER;
            if (riid == __uuidof(IUnknown) || riid == __uuidof(IAudioEffectsChangedNotificationClient)) {
                *object = static_cast<IAudioEffectsChangedNotificationClient *>(this);
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
            if (count == 0) delete this;
            return count;
        }

        STDMETHODIMP OnAudioEffectsChanged() override {
            // Re-query effects and re-enable deep noise suppression if it got turned off
            if (client_ == nullptr) return S_OK;

            ComPtr <IAudioEffectsManager> mgr;
            HRESULT hr = client_->GetService(IID_PPV_ARGS(&mgr));
            if (FAILED(hr)) return S_OK;

            UINT32 num = 0;
            AUDIO_EFFECT *fx = nullptr;
            hr = mgr->GetAudioEffects(&fx, &num);
            if (FAILED(hr)) return S_OK;

            for (UINT32 i = 0; i < num; ++i) {
                if (fx[i].id == AUDIO_EFFECT_TYPE_DEEP_NOISE_SUPPRESSION) {
                    if (fx[i].state == AUDIO_EFFECT_STATE_OFF && fx[i].canSetState) {
                        const HRESULT set_hr = mgr->SetAudioEffectState(
                                fx[i].id, AUDIO_EFFECT_STATE_ON);
                        if (SUCCEEDED(set_hr)) {
                            std::fprintf(stderr, "openyap_native: effects changed — re-enabled deep noise suppression.\n");
                        } else if (set_hr != AUDCLNT_E_EFFECT_NOT_AVAILABLE &&
                                set_hr != AUDCLNT_E_EFFECT_STATE_READ_ONLY) {
                            std::fprintf(stderr, "openyap_native: effects changed — failed to re-enable "
                                                 "deep noise suppression (HRESULT=0x%08lX).\n",
                                    static_cast<unsigned long>(set_hr));
                        }
                    }
                    break;
                }
            }

            if (fx != nullptr) CoTaskMemFree(fx);
            return S_OK;
        }

    private:
        volatile LONG ref_count_;
        IAudioClient *client_;  // raw non-owning pointer; outlived by the capture worker
    };

    // ── Capture worker thread ────────────────────────────────────────────

    void capture_worker() {
        const HRESULT com_result = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        const bool should_uninitialize = SUCCEEDED(com_result);
        if (FAILED(com_result) && com_result != RPC_E_CHANGED_MODE) {
            set_start_failure(-2, "WASAPI thread COM initialization failed.");
            return;
        }

        // ── MMCSS: boost capture thread to "Pro Audio" scheduling class ──────────
        // Tells the Windows Multimedia Class Scheduler to give this thread real-time
        // priority, eliminating OS scheduler jitter in the WASAPI capture loop.
        // This directly improves VAD accuracy and reduces audio dropouts on every
        // recording. The RAII guard calls AvRevertMmThreadCharacteristics on all
        // exit paths — including early returns below — without touching each one.
        struct MmcssGuard {
            HANDLE handle = nullptr;
            ~MmcssGuard() {
                if (handle) AvRevertMmThreadCharacteristics(handle);
            }
        };
        DWORD mmcss_task_index = 0;
        MmcssGuard mmcss;
        mmcss.handle = AvSetMmThreadCharacteristicsW(L"Pro Audio", &mmcss_task_index);
        if (!mmcss.handle) {
            std::fprintf(stderr,
                    "openyap_native: AvSetMmThreadCharacteristics failed (error=%lu); "
                    "capture thread running at normal priority.\n",
                    GetLastError());
        }

        // Read device_id from context (set by start() before spawning the thread)
        std::string device_id;
        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            device_id = g_capture.device_id;
        }

        // ── Create enumerator ────────────────────────────────────────────
        ComPtr <IMMDeviceEnumerator> enumerator;
        HRESULT hr = CoCreateInstance(
                __uuidof(MMDeviceEnumerator),
                nullptr,
                CLSCTX_ALL,
                IID_PPV_ARGS(&enumerator)
        );
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to create MMDeviceEnumerator.", hr));
            if (should_uninitialize) CoUninitialize();
            return;
        }

        // ── Resolve device (Phase 3a: device selection) ──────────────────
        ComPtr <IMMDevice> device;
        hr = resolve_device(enumerator.Get(), device_id, &device);
        if (hr == E_NOTFOUND) {
            const std::string msg = device_id.empty()
                    ? "No audio input device is available."
                    : "The selected microphone is no longer available.";
            set_start_failure(-4, msg);
            if (should_uninitialize) CoUninitialize();
            return;
        }
        if (FAILED(hr)) {
            set_start_failure(-2, hresult_message("Failed to resolve audio capture device.", hr));
            if (should_uninitialize) CoUninitialize();
            return;
        }

        // ── Target format: 48 kHz 16-bit mono PCM ───────────────────────
        WAVEFORMATEX target_format{};
        target_format.wFormatTag = WAVE_FORMAT_PCM;
        target_format.nChannels = 1;
        target_format.nSamplesPerSec = 48000;
        target_format.wBitsPerSample = 16;
        target_format.nBlockAlign = static_cast<WORD>(target_format.nChannels * (target_format.wBitsPerSample / 8));
        target_format.nAvgBytesPerSec = target_format.nSamplesPerSec * target_format.nBlockAlign;
        target_format.cbSize = 0;

        // ── Phase 1: Try IAudioClient3 low-latency first ─────────────────
        ComPtr <IAudioClient3> audio_client3;
        ComPtr <IAudioClient2> audio_client2;
        ComPtr <IAudioClient> audio_client_base;
        bool used_client3 = false;

        hr = device->Activate(__uuidof(IAudioClient3), CLSCTX_ALL, nullptr,
                reinterpret_cast<void **>(audio_client3.GetAddressOf()));
        if (SUCCEEDED(hr)) {
            // Apply communications category hint BEFORE initialization
            std::string diagnostics;
            // IAudioClient3 inherits from IAudioClient2; we can QI for it
            ComPtr <IAudioClient2> client2_for_props;
            if (SUCCEEDED(audio_client3.As(&client2_for_props))) {
                openyap::noise::configure_communications_mode(client2_for_props.Get(), device.Get(), &diagnostics);
                if (!diagnostics.empty()) {
                    std::fprintf(stderr, "openyap_native: %s\n", diagnostics.c_str());
                }
            }

            const HRESULT init_hr = try_init_low_latency(
                    device.Get(), audio_client3.Get(), &target_format, g_capture.audio_event);
            if (SUCCEEDED(init_hr)) {
                used_client3 = true;
                audio_client_base = audio_client3;  // QI to IAudioClient via ComPtr assignment
            } else {
                const char *init_name = hresult_name(init_hr);
                if (init_name != nullptr) {
                    std::fprintf(stderr, "openyap_native: IAudioClient3 init failed (%s, HRESULT=0x%08lX); "
                                         "falling back to IAudioClient2.\n",
                            init_name,
                            static_cast<unsigned long>(init_hr));
                } else {
                    std::fprintf(stderr, "openyap_native: IAudioClient3 init failed (HRESULT=0x%08lX); "
                                         "falling back to IAudioClient2.\n", static_cast<unsigned long>(init_hr));
                }
                // Release client3 — we need a fresh activation for client2
                audio_client3.Reset();
            }
        }

        if (!used_client3) {
            // ── Fallback: IAudioClient2 standard init ────────────────────
            hr = device->Activate(__uuidof(IAudioClient2), CLSCTX_ALL, nullptr,
                    reinterpret_cast<void **>(audio_client2.GetAddressOf()));
            if (FAILED(hr)) {
                set_start_failure(-2, hresult_message("Failed to activate IAudioClient2.", hr));
                if (should_uninitialize) CoUninitialize();
                return;
            }

            std::string diagnostics;
            openyap::noise::configure_communications_mode(audio_client2.Get(), device.Get(), &diagnostics);
            if (!diagnostics.empty()) {
                std::fprintf(stderr, "openyap_native: %s\n", diagnostics.c_str());
            }

            constexpr
            REFERENCE_TIME buffer_duration = 200000;  // 20 ms
            hr = audio_client2->Initialize(
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
                if (should_uninitialize) CoUninitialize();
                return;
            }

            hr = audio_client2->SetEventHandle(g_capture.audio_event);
            if (FAILED(hr)) {
                set_start_failure(-2, hresult_message("Failed to assign the WASAPI capture event handle.", hr));
                if (should_uninitialize) CoUninitialize();
                return;
            }

            audio_client_base = audio_client2;
            std::fprintf(stderr, "openyap_native: using IAudioClient2 standard-latency capture.\n");
        }

        // ── Phase 2: Try enabling deep noise suppression ─────────────────
        try_enable_deep_noise_suppression(audio_client_base.Get());

        // ── Phase 4: Try configuring echo cancellation ───────────────────
        try_configure_echo_cancellation(audio_client_base.Get());

        // ── Phase 5: Register effects change monitoring ──────────────────
        ComPtr <IAudioEffectsManager> effects_manager_for_monitor;
        EffectsChangedHandler *effects_handler = nullptr;
        {
            HRESULT em_hr = audio_client_base->GetService(IID_PPV_ARGS(&effects_manager_for_monitor));
            if (SUCCEEDED(em_hr)) {
                effects_handler = new EffectsChangedHandler(audio_client_base.Get());
                const HRESULT reg_hr = effects_manager_for_monitor->RegisterAudioEffectsChangedNotificationCallback(
                        effects_handler);
                if (SUCCEEDED(reg_hr)) {
                    std::fprintf(stderr, "openyap_native: audio effects change monitoring registered.\n");
                } else {
                    std::fprintf(stderr, "openyap_native: failed to register effects change callback "
                                         "(HRESULT=0x%08lX).\n", static_cast<unsigned long>(reg_hr));
                    effects_handler->Release();
                    effects_handler = nullptr;
                }
            }
        }

        // ── Get capture client ───────────────────────────────────────────
        ComPtr <IAudioCaptureClient> capture_client;
        hr = audio_client_base->GetService(__uuidof(IAudioCaptureClient),
                reinterpret_cast<void **>(capture_client.GetAddressOf()));
        if (FAILED(hr)) {
            if (effects_handler != nullptr && effects_manager_for_monitor) {
                effects_manager_for_monitor->UnregisterAudioEffectsChangedNotificationCallback(effects_handler);
                effects_handler->Release();
            }
            set_start_failure(-2, hresult_message("Failed to acquire IAudioCaptureClient.", hr));
            if (should_uninitialize) CoUninitialize();
            return;
        }

        // ── Register session disconnect notifications ────────────────────
        ComPtr <IAudioSessionControl> session_control;
        hr = audio_client_base->GetService(__uuidof(IAudioSessionControl),
                reinterpret_cast<void **>(session_control.GetAddressOf()));
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
                std::fprintf(stderr, "openyap_native: failed to register audio session callbacks (HRESULT=0x%08lX).\n",
                        static_cast<unsigned long>(register_result));
                session_events->Release();
                session_events = nullptr;
            }
        }

        // ── Start capture ────────────────────────────────────────────────
        hr = audio_client_base->Start();
        if (FAILED(hr)) {
            if (session_events != nullptr) {
                session_control->UnregisterAudioSessionNotification(session_events);
                session_events->Release();
            }
            if (effects_handler != nullptr && effects_manager_for_monitor) {
                effects_manager_for_monitor->UnregisterAudioEffectsChangedNotificationCallback(effects_handler);
                effects_handler->Release();
            }
            set_start_failure(-2, hresult_message("Failed to start WASAPI capture.", hr));
            if (should_uninitialize) CoUninitialize();
            return;
        }

        // ── Signal success ───────────────────────────────────────────────
        {
            std::lock_guard <std::mutex> lock(g_capture.mutex);
            g_capture.start_result = 0;
            g_capture.start_error.clear();
        }
        g_capture.active.store(true);
        SetEvent(g_capture.startup_event);

        // ── Capture loop ─────────────────────────────────────────────────
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

        // ── Cleanup ──────────────────────────────────────────────────────
        audio_client_base->Stop();

        // Unregister effects change handler (Phase 5)
        if (effects_handler != nullptr && effects_manager_for_monitor) {
            effects_manager_for_monitor->UnregisterAudioEffectsChangedNotificationCallback(effects_handler);
            effects_handler->Release();
            effects_handler = nullptr;
        }

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

// ── Public API ───────────────────────────────────────────────────────────

namespace openyap::capture {

    // ── Phase 3a: Device enumeration ─────────────────────────────────────

    int list_devices(std::vector <DeviceInfo> *devices, std::string *error) {
        if (devices == nullptr) {
            if (error != nullptr) *error = "devices pointer is null.";
            return -3;
        }
        devices->clear();

        const HRESULT com_result = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        const bool should_uninitialize = SUCCEEDED(com_result);
        if (FAILED(com_result) && com_result != RPC_E_CHANGED_MODE) {
            if (error != nullptr) *error = "COM initialization failed.";
            return -2;
        }

        ComPtr <IMMDeviceEnumerator> enumerator;
        HRESULT hr = CoCreateInstance(
                __uuidof(MMDeviceEnumerator),
                nullptr,
                CLSCTX_ALL,
                IID_PPV_ARGS(&enumerator)
        );
        if (FAILED(hr)) {
            if (error != nullptr) *error = hresult_message("Failed to create MMDeviceEnumerator.", hr);
            if (should_uninitialize) CoUninitialize();
            return -2;
        }

        // Get default device ID for comparison
        std::wstring default_id;
        {
            ComPtr <IMMDevice> default_device;
            if (SUCCEEDED(enumerator->GetDefaultAudioEndpoint(eCapture, eConsole, &default_device))) {
                LPWSTR id_str = nullptr;
                if (SUCCEEDED(default_device->GetId(&id_str)) && id_str != nullptr) {
                    default_id = id_str;
                    CoTaskMemFree(id_str);
                }
            }
        }

        // Enumerate all active capture endpoints
        ComPtr <IMMDeviceCollection> collection;
        hr = enumerator->EnumAudioEndpoints(eCapture, DEVICE_STATE_ACTIVE, &collection);
        if (FAILED(hr)) {
            if (error != nullptr) *error = hresult_message("Failed to enumerate audio endpoints.", hr);
            if (should_uninitialize) CoUninitialize();
            return -2;
        }

        UINT count = 0;
        hr = collection->GetCount(&count);
        if (FAILED(hr)) {
            if (error != nullptr) *error = hresult_message("Failed to get device count.", hr);
            if (should_uninitialize) CoUninitialize();
            return -2;
        }

        for (UINT i = 0; i < count; ++i) {
            ComPtr <IMMDevice> dev;
            if (FAILED(collection->Item(i, &dev))) continue;

            LPWSTR id_str = nullptr;
            if (FAILED(dev->GetId(&id_str)) || id_str == nullptr) continue;
            const std::wstring wide_id(id_str);
            CoTaskMemFree(id_str);

            ComPtr <IPropertyStore> props;
            std::string friendly_name = "(Unknown)";
            if (SUCCEEDED(dev->OpenPropertyStore(STGM_READ, &props))) {
                PROPVARIANT var;
                PropVariantInit(&var);
                if (SUCCEEDED(props->GetValue(OPENYAP_PKEY_FriendlyName, &var)) && var.vt == VT_LPWSTR) {
                    friendly_name = narrow(var.pwszVal);
                }
                PropVariantClear(&var);
            }

            DeviceInfo info;
            info.id = narrow(wide_id.c_str());
            info.name = friendly_name;
            info.is_default = (wide_id == default_id);
            devices->push_back(std::move(info));
        }

        if (should_uninitialize) CoUninitialize();
        return 0;
    }

    // ── Start capture (with optional device ID) ──────────────────────────

    int start(int sample_rate, int channels, audio_callback_t callback, void *user_data,
            const char *device_id, std::string *error) {
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
            g_capture.device_id = (device_id != nullptr) ? std::string(device_id) : std::string();
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

    // ── Stop capture ─────────────────────────────────────────────────────

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
