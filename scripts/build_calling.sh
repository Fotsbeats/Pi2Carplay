#!/bin/sh
# ANDROID_CC may select a standalone NDK Clang wrapper on nonstandard build hosts.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
mkdir -p "$ROOT/build/calling"
: "${SBC_SOURCE:?Set SBC_SOURCE to the pinned BlueZ sbc 2.1 source directory (third_party/dependency-revisions.json)}"
build_hci() {
    compiler=$1; shift
    "$compiler" -O2 -Wall -Wextra -I"$SBC_SOURCE/sbc" "$@" \
        "$SBC_SOURCE/sbc/sbc.c" "$SBC_SOURCE/sbc/sbc_primitives.c" \
        "$SBC_SOURCE/sbc/sbc_primitives_sse.c" "$SBC_SOURCE/sbc/sbc_primitives_mmx.c" \
        "$SBC_SOURCE/sbc/sbc_primitives_iwmmxt.c" "$SBC_SOURCE/sbc/sbc_primitives_neon.c" \
        "$SBC_SOURCE/sbc/sbc_primitives_armv6.c"
}
build_hci cc -pthread "$ROOT/src/audio/test_call_hci.c" -lm -o "$ROOT/build/calling/test-call-hci"
"$ROOT/build/calling/test-call-hci"
if [ -z "${ANDROID_CC:-}" ]; then
    : "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT or ANDROID_CC}"
    ANDROID_CC=$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang
fi
build_hci "$ANDROID_CC" -fPIC -shared "$ROOT/src/audio/call_hci.c" -llog -o "$ROOT/build/calling/libpi2call_hci.so"
build_hci "$ANDROID_CC" "$ROOT/src/audio/test_call_hci.c" -llog -lm -o "$ROOT/build/calling/test-call-hci-android"
"$ANDROID_CC" -O2 -Wall -Wextra -fPIC -shared "$ROOT/src/audio/call_resample.c" -ldl -o "$ROOT/build/calling/libpi2call_resample.so"
