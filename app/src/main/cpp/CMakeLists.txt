# SPDX-FileCopyrightText: 2026 quendoris
# SPDX-License-Identifier: AGPL-3.0-only

cmake_minimum_required(VERSION 3.22.1)
project(aeris_android_bridge LANGUAGES CXX)

add_library(aeris_android_bridge SHARED
    native_document_bridge.cpp
)

target_compile_features(aeris_android_bridge PRIVATE cxx_std_20)
target_compile_options(aeris_android_bridge PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Werror
)
