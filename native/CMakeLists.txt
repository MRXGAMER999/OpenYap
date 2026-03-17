cmake_minimum_required(VERSION 3.21)

project(openyap_native LANGUAGES CXX)

add_library(openyap_native SHARED
    src/openyap_native.cpp
    src/audio_capture.cpp
    src/audio_encoder.cpp
    src/hotkey_manager.cpp
    src/paste_automation.cpp
    src/vad.cpp
    src/noise_suppressor.cpp
)

target_compile_features(openyap_native PRIVATE cxx_std_17)

target_compile_definitions(openyap_native PRIVATE
    OPENYAP_EXPORTS
    WINVER=0x0A00
    _WIN32_WINNT=0x0A00
    WIN32_LEAN_AND_MEAN
    NOMINMAX
    UNICODE
    _UNICODE
)

target_include_directories(openyap_native PRIVATE src)

target_link_libraries(openyap_native PRIVATE
    mfplat
    mfreadwrite
    mf
    mfuuid
    wmcodecdspuuid
    ole32
    propsys
    mmdevapi
    user32
    avrt
)

# ── JNI audio bridge (optional — requires a JDK on the build machine) ────────
# When found, jni_audio_bridge.cpp is compiled into the DLL and exports
# Java_com_openyap_platform_NativeAudioCallbackJni_* symbols that let the
# Kotlin side call the audio callback directly via JNI instead of going through
# JNA's reflection bridge on every audio chunk.
find_package(JNI)
if(JNI_FOUND)
    message(STATUS "openyap_native: JDK found — building JNI audio bridge (${JNI_INCLUDE_DIRS})")
    target_sources(openyap_native PRIVATE src/jni_audio_bridge.cpp)
    target_include_directories(openyap_native PRIVATE ${JNI_INCLUDE_DIRS})
    target_compile_definitions(openyap_native PRIVATE OPENYAP_JNI_ENABLED)
else()
    message(STATUS "openyap_native: JDK not found — JNI audio bridge disabled, falling back to JNA")
endif()

install(TARGETS openyap_native
    RUNTIME DESTINATION "${CMAKE_SOURCE_DIR}/prebuilt/windows-x64"
)
