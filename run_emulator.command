#!/bin/zsh
cd "$(dirname "$0")"
export ANDROID_SDK_ROOT="$(pwd)/_tmp_sdk"
export ANDROID_AVD_HOME="$(pwd)/_tmp_sdk/avd"
"_tmp_sdk/emulator/emulator" -avd Pixel_Indoor_Map -gpu host &
"_tmp_sdk/platform-tools/adb" wait-for-device
sleep 5
"_tmp_sdk/platform-tools/adb" install -r -g IndoorSignalMap-debug.apk
"_tmp_sdk/platform-tools/adb" shell am start -n gr.uni.kinites.indoorsignalmap/.MainActivity
