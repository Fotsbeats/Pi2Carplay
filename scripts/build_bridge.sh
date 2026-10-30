#!/bin/sh
# Uses the protocol runtime's own ARM32 Bionic closure; SDK implementation is binary-only.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
: "${PROTOCOL_LIB:?Set directory containing the ARM32 protocol runtime libraries}"
mkdir -p "$ROOT/build/bridge"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_call_playback.c" -o "$ROOT/build/bridge/test-call-playback"
"$ROOT/build/bridge/test-call-playback"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_call_mic.c" -o "$ROOT/build/bridge/test-call-mic"
"$ROOT/build/bridge/test-call-mic"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_call_audio.c" -o "$ROOT/build/bridge/test-call-audio"
"$ROOT/build/bridge/test-call-audio"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_audio.c" -o "$ROOT/build/bridge/test-audio"
"$ROOT/build/bridge/test-audio"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_nmea.c" -o "$ROOT/build/bridge/test-nmea"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_gps_stats.c" -o "$ROOT/build/bridge/test-gps-stats"
"$ROOT/build/bridge/test-gps-stats"
"$ROOT/build/bridge/test-nmea"
cc -O2 -Wall -Wextra "$ROOT/src/bridge/test_display_geometry.c" -o "$ROOT/build/bridge/test-display-geometry"
"$ROOT/build/bridge/test-display-geometry"
cc -O2 -Wno-unused-parameter "$ROOT/src/bridge/test_touch.c" -o "$ROOT/build/bridge/test-touch"
"$ROOT/build/bridge/test-touch"
clang --target=armv7a-linux-androideabi30 -marm -ffreestanding -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -fPIC -c "$ROOT/src/bridge/handshake_probe.c" -o "$ROOT/build/bridge/handshake-probe.o"
ld.lld -m armelf_linux_eabi -pie -e _start --dynamic-linker /data/local/tmp/pi5-protocol/bin/linker -o "$ROOT/build/handshake-probe" "$ROOT/build/bridge/handshake-probe.o" -L "$PROTOCOL_LIB" --no-as-needed -ldl -lc
clang --target=armv7a-linux-androideabi30 -marm -ffreestanding -fno-stack-protector -fPIC -c "$ROOT/src/bridge/test_call_sdk.c" -o "$ROOT/build/bridge/test-call-sdk.o"
ld.lld -m armelf_linux_eabi -pie -e _start --dynamic-linker /data/local/tmp/pi5-protocol/bin/linker -o "$ROOT/build/bridge/test-call-sdk" "$ROOT/build/bridge/test-call-sdk.o" -L "$PROTOCOL_LIB" --no-as-needed -ldl -lc
