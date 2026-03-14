#pragma once

#include "openyap_native.h"

#include <windows.h>

#include <condition_variable>
#include <mutex>
#include <string>
#include <thread>

namespace openyap::hotkey {

struct HotkeyBinding {
    int key_code = 0;
    unsigned int modifiers = 0;
    bool enabled = false;
};

class HotkeyManager {
public:
    int set_config(const HotkeyBinding &binding, std::string *error);
    int start_listening(hotkey_event_callback_t callback, void *user_data, std::string *error);
    int stop_listening(std::string *error);
    int begin_capture(std::string *error);
    int cancel_capture(std::string *error);
    void shutdown();

    LRESULT handle_keyboard(int nCode, WPARAM wParam, const KBDLLHOOKSTRUCT *info);

private:
    void run_loop();
    bool install_hook(std::string *error);
    void uninstall_hook();
    void ensure_message_queue();
    void update_modifier_state(int vk_code, UINT msg_type);
    unsigned int current_modifier_state() const;
    unsigned int modifiers_for_key(int vk_code) const;
    unsigned int combo_mask_for_binding() const;
    bool binding_uses_only_modifiers() const;
    bool is_modifier_part_of_binding(int vk_code) const;
    unsigned int modifier_mask_for_vk(int vk_code) const;
    bool is_modifier_for_binding(int vk_code, unsigned int required_modifiers) const;

    std::mutex mutex_;
    std::condition_variable startup_cv_;
    std::thread thread_;
    DWORD thread_id_ = 0;
    HHOOK hook_ = nullptr;
    HotkeyBinding binding_;
    hotkey_event_callback_t callback_ = nullptr;
    void *callback_user_data_ = nullptr;
    unsigned int active_modifiers_ = 0;
    bool hold_active_ = false;
    bool capture_active_ = false;
    bool running_ = false;
    bool self_stop_requested_ = false;
    bool startup_ready_ = false;
    int startup_result_ = 0;
    std::string startup_error_;
};

HotkeyManager &manager();

}  // namespace openyap::hotkey
