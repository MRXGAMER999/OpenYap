cmake_minimum_required(VERSION 3.21)

project(openyap_native LANGUAGES CXX)

add_library(openyap_native SHARED
    src/openyap_native.cpp
    src/audio_capture.cpp
    src/audio_encoder.cpp
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
)

install(TARGETS openyap_native
    RUNTIME DESTINATION "${CMAKE_SOURCE_DIR}/prebuilt/windows-x64"
)
