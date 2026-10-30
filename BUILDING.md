# Building and adapting Pi2Carplay

The repository contains source and build scripts, not a bundled SDK or compiler. Use your own toolchain. Linux x86-64 is the simplest build host; use a Linux VM on macOS or Linux VM/WSL2 on Windows. WSL is optional. Native ARM64 hosts need an appropriate NDK host toolchain; `build_encoder.sh` currently selects `linux-x86_64`.

These steps build the launcher, capture/audio components and bridge adapter. They do not build a complete AOSP distribution in one command. Use the released image as the reference runtime, or integrate the components into your chosen Android build using AGENTS.md. The protocol SDK remains a binary dependency.

## Install tools

Install Git, Python 3, JDK 17, Clang/LLD, CMake, make and a C/C++ build environment. For example, on Debian/Ubuntu:

```sh
sudo apt-get update
sudo apt-get install git python3 openjdk-17-jdk clang lld cmake build-essential pkg-config
```

Download Google's [Android command-line tools](https://developer.android.com/tools), arrange them under `$ANDROID_HOME/cmdline-tools/latest`, and use [sdkmanager](https://developer.android.com/tools/sdkmanager) to install:

```sh
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-30" "build-tools;35.0.0" "ndk;27.2.12479018"
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export ANDROID_JAR="$ANDROID_HOME/platforms/android-30/android.jar"
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.2.12479018"
```

Set `ANDROID_HOME` to your SDK directory first. NDK r27c is the pinned native toolchain. API30 is the compile/minimum API used by these small components, even though the reference OS is Android16. Installing Android Studio is optional.

## Java components and signing

Create your own key outside the repository, then build:

```sh
mkdir -p "$HOME/.local/share/pi2carplay"
keytool -genkeypair -keystore "$HOME/.local/share/pi2carplay/fork.p12" \
  -storetype PKCS12 -alias pi2carplay -keyalg RSA -keysize 3072 -validity 10000
python3 scripts/build_java.py --keystore "$HOME/.local/share/pi2carplay/fork.p12"
python3 scripts/build_overlays.py --keystore "$HOME/.local/share/pi2carplay/fork.p12"
```

Build products appear in ignored `build/`. Keep signing keys private. A different key cannot update the bundled launcher through a normal APK update. Use a consistent identity for your fork and integrate it into a fresh image; do not wipe an existing user's app data to bypass a signature mismatch.

After a launcher update and restart, verify `org.bench.boxhome/.NavigationService` is enabled and bound in `adb shell dumpsys accessibility`, and `adb shell getprop sys.boxhome.rail.ready` returns `1`. This service provides the persistent sidebar, Recents and split-screen actions; Home's local sidebar alone does not verify it. If disabled, enable **Box Home** under Android Settings → Accessibility, retaining any other enabled services. Check the sidebar over another app, hold Home for Recents, and open two supported apps in split screen before handing over a test build.

## Native capture encoder

Clone the upstream dependencies outside the repository and check out the exact commits in `third_party/dependency-revisions.json`:

```sh
git clone https://code.videolan.org/videolan/x264.git ../x264
git -C ../x264 checkout 0480cb05fa188d37ae87e8f4fd8f1aea3711f7ee
git clone https://chromium.googlesource.com/libyuv/libyuv ../libyuv
git -C ../libyuv checkout 2e65a61dffeb6ab9dd5dd363ccede11770957b73
export X264_SOURCE="$(cd ../x264 && pwd)"
export LIBYUV_SOURCE="$(cd ../libyuv && pwd)"
sh scripts/build_encoder.sh
```

This builds ARM64 `build/libcapture_encoder.so` using the NDK. Deploy it with the matching capture JAR. Keep the bounded frame pipeline, dynamic display geometry and framed H.264 output contract unchanged.

Live video controls also require `src/android/boxhome-video.sh` at `/system/bin/boxhome-video.sh` (0755) and `boxhome-video.rc` under `/system/etc/init/` (0644), with restored file contexts. Restart once after installing the init file; subsequent selections apply live. The launcher uses its existing package-scoped hidden-API exception to save `persist.vendor.pi2video.bitrate` (4000/8000) and `.fps` (30/60). Invalid or absent values fall back to 4000/30. The root hook caps Android rendering; capture respects the receiver ceiling, reconfigures bitrate at a frame boundary and sends fresh SPS/PPS plus an IDR when FPS changes. It retains the USB session, canvas, safe area and audio. Run `VideoModeTest`, `tests/test_video_policy.py` and the real-stream checks in `tests/test_adaptive_encoder.py`; verify live switching on the head unit before release.

## ARM32 bridge adapter

Obtain the reference runtime's program-only ARM32 library directory from the release image at `/data/local/tmp/pi5-protocol/lib`. Set `PROTOCOL_LIB` to its local extracted directory. An authorized ADB connection to a reference installation can also copy that directory; do not copy accounts, logs or other userdata. The SDK implementation is not rebuilt by this repository.

```sh
export PROTOCOL_LIB=/absolute/path/to/reference-protocol/lib
sh scripts/build_bridge.sh
```

The script runs the geometry, touch and GPS parser checks, then cross-compiles `build/handshake-probe` with Clang/LLD against the runtime's own ARM32 Bionic libraries. Keep that linker/library closure isolated from ARM64 Android processes. Changing the UI does not require rebuilding this adapter or the USB kernel.

## Kernel, framework and verification

Experimental phone calling has a separate native transport, Android helper and Calls app. See [calling build, deployment and rollback notes](src/audio/CALLING.md). It requires the exact target Android runtime and a vehicle trial; the existing music path alone does not establish call-audio support.

For that native build, extract the pinned BlueZ sbc 2.1 archive from `third_party/dependency-revisions.json` and set `SBC_SOURCE` to its source directory. Preserve its LGPL notice and corresponding source when distributing the statically linked native transport.

The AOSP Phone UI has its own [Car Dialer build, deployment and rollback notes](src/audio/CAR-DIALER.md). `scripts/build_car_dialer.py` adapts the pinned upstream APK for this non-Automotive runtime; it does not replace the OS or rebuild the upstream app from its complete source tree.

The complete patched kernel is on `main` under `src/kernel/linux`. See [kernel build instructions](src/kernel/README.md) for configuration, firmware hashes, modules and toolchain requirements. Linux filenames require a Linux-compatible filesystem; Windows developers should use a Linux VM/WSL checkout for this subtree.

Framework integration needs the target OS's own `services.jar` and compatible smali tools. `scripts/build_sidebar_policy.py` documents `DEX_TOOLS` and `JAVA_HOME`. Never transplant a patched framework or binary codec correction into a different Android version without rebuilding and verifying the relevant change.

After the sidebar policy, run `python3 scripts/build_rail_insets.py /path/to/services-sidebar.jar` with the same tools. This reserves the 104-pixel navigation rail in keyboard window layout and keyboard WindowMetrics on display 0 while the rail service is ready. Metrics queries are scoped to the active keyboard window's owner UID, including themed/display contexts without a window token. It leaves other callers and the raw display insets unchanged. Deploy its `services-rail-insets.jar` and the current bridge wrapper, which refreshes PiP's cached display layout after applying the negotiated size. PiP retains its existing 86x16dp edge clearance. The patcher rejects unexpected target methods. Verify keyboard edge keys, PiP entry/dragging, app frames and split screen on the target device.

`tests/KeyboardRailInsetsTest.java` checks the installed helper on the Pi. Compile it with the same SDK/D8 toolchain, package its `classes.dex` as `keyboard-rail-test.jar`, and push that JAR to `/data/local/tmp/`. Run as root with `env -u LD_PRELOAD -u LD_LIBRARY_PATH CLASSPATH=/system/framework/services.jar:/data/local/tmp/keyboard-rail-test.jar app_process / KeyboardRailInsetsTest`. It restores the rail readiness property after testing.

Run `python3 tests/test_firstboot.py`, the launcher checks in `build_java.py`, the native tests in `build_bridge.sh`, and the capture geometry/frame-pipeline checks. Run `GpuConversionSelfCheck` on the actual Pi before selecting a new capture/encoder pair. Test real head-unit video, audio, touch and reconnection after Android integration changes.

For a distributable image, start from a known baseline and fresh program-only userdata, preserve kernel/USB/audio configuration, and verify image/partition hashes. No account-bearing device clone, signing key or authorized ADB host belongs in a release. See AGENTS.md for the detailed porting contracts and component license boundaries.
