cmake_minimum_required(VERSION 3.22.1)

project(archivetune_discord_social)

# Discord Social SDK is now implemented in pure Kotlin.
# No C++ bridge needed.
# Keep an empty target so the build system doesn't complain.
add_library(archivetune_discord_social SHARED empty.cpp)
target_compile_features(archivetune_discord_social PRIVATE cxx_std_17)
target_link_libraries(archivetune_discord_social log)
