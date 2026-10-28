#!/bin/sh
# Linux host, JDK/SDK separate. NDK27.2 plus pinned source checkouts are required.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
: "${ANDROID_NDK_ROOT:?Set Android NDK27.2.12479018 directory}"
: "${X264_SOURCE:?Set the x264 checkout directory at the pinned commit}"
: "${LIBYUV_SOURCE:?Set the libyuv checkout directory at the pinned commit}"
TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$TOOLCHAIN:$PATH"
export CC="$TOOLCHAIN/aarch64-linux-android30-clang"
export CXX="$TOOLCHAIN/aarch64-linux-android30-clang++"
export AR="$TOOLCHAIN/llvm-ar"
export RANLIB="$TOOLCHAIN/llvm-ranlib"
[ "$(git -C "$X264_SOURCE" rev-parse HEAD)" = 0480cb05fa188d37ae87e8f4fd8f1aea3711f7ee ]
[ "$(git -C "$LIBYUV_SOURCE" rev-parse HEAD)" = 2e65a61dffeb6ab9dd5dd363ccede11770957b73 ]
mkdir -p "$ROOT/build/x264" "$ROOT/build/libyuv"
cd "$ROOT/build/x264"
"$X264_SOURCE/configure" --host=aarch64-linux-android --enable-static --enable-pic --disable-cli --disable-opencl --disable-lavf --disable-swscale --bit-depth=8 --chroma-format=420 --extra-cflags='-O3 -mcpu=cortex-a76 -ffunction-sections -fdata-sections'
make -j4 libx264.a
cmake -S "$LIBYUV_SOURCE" -B "$ROOT/build/libyuv" -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DLIBYUV_DISABLE_JPEG=ON -DCAN_COMPILE_SME=OFF -DCMAKE_C_FLAGS='-O3 -mcpu=cortex-a76' -DCMAKE_CXX_FLAGS='-O3 -mcpu=cortex-a76 -fno-exceptions -fno-rtti'
cmake --build "$ROOT/build/libyuv" --target yuv -j4
"$CC" -O3 -mcpu=cortex-a76 -fPIC -shared -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--no-undefined -I"$ROOT/build/x264" -I"$X264_SOURCE" -I"$LIBYUV_SOURCE/include" "$ROOT/src/capture/encoder_core.c" "$ROOT/src/capture/encoder_jni.c" "$ROOT/build/x264/libx264.a" "$ROOT/build/libyuv/libyuv.a" -o "$ROOT/build/libcapture_encoder.so"
