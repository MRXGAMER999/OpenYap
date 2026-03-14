#include "hotkey_manager.h"

#include <windows.h>

#include <string>

namespace {

    constexpr int kWhKeyboardLl = 13;
    constexpr UINT
    kWmKeyDown = 0x0100;
    constexpr UINT
    kWmKeyUp = 0x0101;
    constexpr UINT
    kWmSysKeyDown = 0x0104;
    constexpr UINT
    kWmSysKeyUp = 0x0105;
    constexpr int kVkEscape = 0x1B;
    constexpr unsigned int kModifierCtrl = 1u << 0;
    constexpr unsigned int kModifierAlt = 1u << 1;
    constexpr unsigned int kModifierShift = 1u << 2;
    constexpr unsigned int kModifierMeta = 1u << 3;
    constexpr DWORD
    kInjectedFlag = 0x10;

    bool is_key_down(UINT
    msg_type) {
    return msg_type == kWmKeyDown || msg_type ==
    kWmSysKeyDown;
}

bool is_key_up(UINT
msg_type) {
return msg_type == kWmKeyUp || msg_type ==
kWmSysKeyUp;
}

std::string format_win32_error(const char *prefix, DWORD error_code) {
    std::string message = prefix;
    message += " (Win32 error ";
    message += std::to_string(error_code);
    message += ")";
    return message;
}

HMODULE current_module_handle() {
    HMODULE module = nullptr;
    GetModuleHandleExW(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCWSTR>(&current_module_handle),
            &module
    );
    return module;
}

LRESULT CALLBACK

keyboard_proc(int nCode, WPARAM wParam, LPARAM lParam) {
    const auto *info = reinterpret_cast<const KBDLLHOOKSTRUCT *>(lParam);
    return openyap::hotkey::manager().handle_keyboard(nCode, wParam, info);
}

}  // namespace

namespace openyap::hotkey {

    HotkeyManager &manager() {
        static HotkeyManager instance;
        return instance;
    }

    int HotkeyManager::set_config(const HotkeyBinding &binding, std::string *error) {
        std::lock_guard <std::mutex> lock(mutex_);
        binding_ = binding;
        if (!binding_.enabled) {
            hold_active_ = false;
        }
        if (error) {
            error->clear();
        }
        return 0;
    }

    int HotkeyManager::start_listening(hotkey_event_callback_t callback, void *user_data, std::string *error) {
        std::unique_lock <std::mutex> lock(mutex_);
        callback_ = callback;
        callback_user_data_ = user_data;

        if (running_) {
            if (error) {
                error->clear();
            }
            return 0;
        }

        if (thread_.joinable()) {
            lock.unlock();
            thread_.join();
            lock.lock();
        }

        startup_ready_ = false;
        startup_result_ = 0;
        startup_error_.clear();
        active_modifiers_ = 0;
        hold_active_ = false;
        capture_active_ = false;

        try {
            thread_ = std::thread([this]() {
                run_loop();
            });
        } catch (const std::exception &exception) {
            if (error) {
                *error = std::string("Failed to create hotkey thread: ") + exception.what();
            }
            return -1;
        } catch (...) {
            if (error) {
                *error = "Failed to create hotkey thread.";
            }
            return -1;
        }

        startup_cv_.wait(lock, [this]() {
            return startup_ready_;
        });
        if (startup_result_ != 0) {
            if (error) {
                *error = startup_error_;
            }
            lock.unlock();
            if (thread_.joinable()) {
                thread_.join();
            }
            return startup_result_;
        }

        if (error) {
            error->clear();
        }
        return 0;
    }

    int HotkeyManager::stop_listening(std::string *error) {
        DWORD thread_id = 0;
        bool called_from_hook_thread = false;
        {
            std::lock_guard <std::mutex> lock(mutex_);
            if (!running_ && !thread_.joinable()) {
                if (error) {
                    error->clear();
                }
                return 0;
            }
            thread_id = thread_id_;
            called_from_hook_thread = thread_.joinable() && std::this_thread::get_id() == thread_.get_id();
            if (called_from_hook_thread) {
                self_stop_requested_ = true;
            }
        }

        if (thread_id != 0) {
            PostThreadMessageW(thread_id, WM_QUIT, 0, 0);
        }
        if (called_from_hook_thread) {
            if (error) {
                error->clear();
            }
            return 0;
        }
        if (thread_.joinable()) {
            thread_.join();
        }

        if (error) {
            error->clear();
        }
        return 0;
    }

    int HotkeyManager::begin_capture(std::string *error) {
        std::lock_guard <std::mutex> lock(mutex_);
        if (!running_) {
            if (error) {
                *error = "Hotkey listener is not running.";
            }
            return -1;
        }
        capture_active_ = true;
        hold_active_ = false;
        active_modifiers_ = current_modifier_state();
        if (error) {
            error->clear();
        }
        return 0;
    }

    int HotkeyManager::cancel_capture(std::string *error) {
        std::lock_guard <std::mutex> lock(mutex_);
        capture_active_ = false;
        if (error) {
            error->clear();
        }
        return 0;
    }

    void HotkeyManager::shutdown() {
        std::string ignored;
        stop_listening(&ignored);
        std::lock_guard <std::mutex> lock(mutex_);
        callback_ = nullptr;
        callback_user_data_ = nullptr;
        active_modifiers_ = 0;
        hold_active_ = false;
        capture_active_ = false;
        binding_ = {};
    }

    void HotkeyManager::run_loop() {
        {
            std::lock_guard <std::mutex> lock(mutex_);
            thread_id_ = GetCurrentThreadId();
        }

        ensure_message_queue();

        std::string install_error;
        if (!install_hook(&install_error)) {
            std::lock_guard <std::mutex> lock(mutex_);
            startup_result_ = -2;
            startup_error_ = install_error;
            startup_ready_ = true;
            thread_id_ = 0;
            startup_cv_.notify_one();
            return;
        }

        {
            std::lock_guard <std::mutex> lock(mutex_);
            running_ = true;
            startup_result_ = 0;
            startup_error_.clear();
            startup_ready_ = true;
            startup_cv_.notify_one();
        }

        MSG msg;
        while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        }

        uninstall_hook();

        std::lock_guard <std::mutex> lock(mutex_);
        running_ = false;
        hold_active_ = false;
        capture_active_ = false;
        active_modifiers_ = 0;
        thread_id_ = 0;
        if (self_stop_requested_ && thread_.joinable() && std::this_thread::get_id() == thread_.get_id()) {
            thread_.detach();
            self_stop_requested_ = false;
        }
    }

    bool HotkeyManager::install_hook(std::string *error) {
        const HMODULE module = current_module_handle();
        hook_ = SetWindowsHookExW(kWhKeyboardLl, keyboard_proc, module, 0);
        if (hook_ == nullptr) {
            if (error) {
                *error = format_win32_error("SetWindowsHookExW(WH_KEYBOARD_LL) failed", GetLastError());
            }
            return false;
        }
        if (error) {
            error->clear();
        }
        return true;
    }

    void HotkeyManager::uninstall_hook() {
        if (hook_ != nullptr) {
            UnhookWindowsHookEx(hook_);
            hook_ = nullptr;
        }
    }

    void HotkeyManager::ensure_message_queue() {
        MSG msg;
        PeekMessageW(&msg, nullptr, 0, 0, PM_NOREMOVE);
    }

    unsigned int HotkeyManager::modifier_mask_for_vk(int vk_code) const {
        switch (vk_code) {
            case VK_CONTROL:
            case VK_LCONTROL:
            case VK_RCONTROL:
                return kModifierCtrl;
            case VK_MENU:
            case VK_LMENU:
            case VK_RMENU:
                return kModifierAlt;
            case VK_SHIFT:
            case VK_LSHIFT:
            case VK_RSHIFT:
                return kModifierShift;
            case VK_LWIN:
            case VK_RWIN:
                return kModifierMeta;
            default:
                return 0;
        }
    }

    void HotkeyManager::update_modifier_state(int vk_code, UINT msg_type) {
        const unsigned int mask = modifier_mask_for_vk(vk_code);
        if (mask == 0) {
            return;
        }
        if (is_key_down(msg_type)) {
            active_modifiers_ |= mask;
        } else if (is_key_up(msg_type)) {
            active_modifiers_ &= ~mask;
        }
    }

    unsigned int HotkeyManager::current_modifier_state() const {
        unsigned int mask = 0;
        if ((GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0) {
            mask |= kModifierCtrl;
        }
        if ((GetAsyncKeyState(VK_MENU) & 0x8000) != 0) {
            mask |= kModifierAlt;
        }
        if ((GetAsyncKeyState(VK_SHIFT) & 0x8000) != 0) {
            mask |= kModifierShift;
        }
        if ((GetAsyncKeyState(VK_LWIN) & 0x8000) != 0 || (GetAsyncKeyState(VK_RWIN) & 0x8000) != 0) {
            mask |= kModifierMeta;
        }
        return mask;
    }

    unsigned int HotkeyManager::modifiers_for_key(int vk_code) const {
        return active_modifiers_ & ~modifier_mask_for_vk(vk_code);
    }

    unsigned int HotkeyManager::combo_mask_for_binding() const {
        return binding_.modifiers | modifier_mask_for_vk(binding_.key_code);
    }

    bool HotkeyManager::binding_uses_only_modifiers() const {
        return binding_.enabled && binding_.key_code != 0 && modifier_mask_for_vk(binding_.key_code) != 0;
    }

    bool HotkeyManager::is_modifier_part_of_binding(int vk_code) const {
        const unsigned int key_mask = modifier_mask_for_vk(vk_code);
        return key_mask != 0 && (combo_mask_for_binding() & key_mask) != 0;
    }

    bool HotkeyManager::is_modifier_for_binding(int vk_code, unsigned int required_modifiers) const {
        const unsigned int mask = modifier_mask_for_vk(vk_code);
        return mask != 0 && (required_modifiers & mask) != 0;
    }

    LRESULT HotkeyManager::handle_keyboard(int nCode, WPARAM wParam, const KBDLLHOOKSTRUCT *info) {
        if (nCode < 0 || info == nullptr) {
            return CallNextHookEx(hook_, nCode, wParam, reinterpret_cast<LPARAM>(info));
        }

        if ((info->flags & kInjectedFlag) != 0) {
            return CallNextHookEx(hook_, nCode, wParam, reinterpret_cast<LPARAM>(info));
        }

        hotkey_event_callback_t callback = nullptr;
        void *user_data = nullptr;
        int event_type = 0;
        int event_vk_code = 0;
        unsigned int event_modifiers = 0;
        bool consume = false;

        {
            std::lock_guard <std::mutex> lock(mutex_);
            const int vk_code = static_cast<int>(info->vkCode);
            const UINT msg_type = static_cast<UINT>(wParam);

            update_modifier_state(vk_code, msg_type);
            const unsigned int key_modifiers = modifiers_for_key(vk_code);

            if (capture_active_) {
                const bool is_modifier_key = modifier_mask_for_vk(vk_code) != 0;
                const bool should_capture =
                        (msg_type == kWmKeyDown || msg_type == kWmSysKeyDown) &&
                                (!is_modifier_key || key_modifiers != 0);
                if (should_capture) {
                    capture_active_ = false;
                    event_type = OPENYAP_HOTKEY_EVENT_CAPTURED;
                    event_vk_code = vk_code;
                    event_modifiers = key_modifiers;
                }
            } else if (binding_.enabled && binding_.key_code != 0) {
                const bool modifier_only_binding = binding_uses_only_modifiers();
                const bool matches_binding = modifier_only_binding
                        ? (is_modifier_part_of_binding(vk_code) && active_modifiers_ == combo_mask_for_binding())
                        : (vk_code == binding_.key_code && key_modifiers == binding_.modifiers);

                if (matches_binding) {
                    if (msg_type == kWmKeyDown || msg_type == kWmSysKeyDown) {
                        if (!hold_active_) {
                            hold_active_ = true;
                            event_type = OPENYAP_HOTKEY_EVENT_HOLD_DOWN;
                        }
                        consume = !modifier_only_binding;
                    } else if (msg_type == kWmKeyUp || msg_type == kWmSysKeyUp) {
                        if (hold_active_) {
                            hold_active_ = false;
                            event_type = OPENYAP_HOTKEY_EVENT_HOLD_UP;
                        }
                        consume = !modifier_only_binding;
                    }
                } else if (hold_active_ && is_key_up(msg_type)) {
                    const bool is_main_key = modifier_only_binding ? is_modifier_part_of_binding(vk_code) : vk_code == binding_.key_code;
                    const bool is_required_modifier = is_modifier_for_binding(vk_code, binding_.modifiers);
                    if (is_main_key || is_required_modifier) {
                        hold_active_ = false;
                        event_type = OPENYAP_HOTKEY_EVENT_HOLD_UP;
                        consume = is_main_key && !modifier_only_binding && modifier_mask_for_vk(vk_code) == 0;
                    }
                }

                if (vk_code == kVkEscape && hold_active_ && is_key_down(msg_type)) {
                    hold_active_ = false;
                    event_type = OPENYAP_HOTKEY_EVENT_CANCEL_RECORDING;
                    consume = true;
                }
            }

            callback = callback_;
            user_data = callback_user_data_;
        }

        if (event_type != 0 && callback != nullptr) {
            callback(event_type, event_vk_code, static_cast<int>(event_modifiers), user_data);
        }

        if (consume) {
            return 1;
        }

        return CallNextHookEx(hook_, nCode, wParam, reinterpret_cast<LPARAM>(info));
    }

}  // namespace openyap::hotkey
