# Pi2Carplay contributor and agent instructions

## Scope and authority

Pi2Carplay adds a native USB CarPlay bridge, screen capture/encoding, touch/audio integration and a dashboard launcher to Android on Raspberry Pi 5. The current image uses KonstaKANG AOSP16. Source adaptation to another Android release or Pi Android distribution is possible, but a different board also needs compatible USB hardware, role switching and kernel integration. Do not advertise an untested port as compatible.

Read README.md, LICENSE-SCOPE.md, third_party/, src/kernel/README.md and src/bridge/DISPLAY-NEGOTIATION.md before changing the base OS. Obtain upstream images from their author and preserve their notices. The protocol SDK implementation remains a binary dependency; this is not a fully reproducible OS or complete SDK source release.

## Source map

- src/bridge: SDK adapter, USB lifecycle, video transport, touch, PCM audio and vehicle NMEA input.
- src/capture: Android mirror, GLES I420 conversion, bounded pipeline and JNI/x264 encoder.
- src/audio: Android playback capture through REMOTE_SUBMIX.
- src/launcher: Home, favorites, navigation rail, recents and split-screen entry.
- src/android: first boot, landscape policy, framework integration and guarded media/graphics corrections.
- src/kernel: USB/HID sources, role-switch device tree, configuration and patch provenance.
- scripts and tests: build tools and checks. Keep generated files in ignored build/ and SD images in release assets, never Git history.

## Keep the handshake intact

When porting Android, retain the working bridge runtime and change Android-facing interfaces first. Do not replace the SDK or rewrite authentication to solve a launcher, codec or audio-routing problem.

Preserve USB descriptors/identities, iAP2 HID transport, gated enumeration and control-triggered host/device role changes. Keep the kernel, modules, device tree and vendor startup scripts matched. A generic replacement kernel is not equivalent. Exact final kernel-source reconciliation is pending; retained patches are not a complete reproduction of the shipped kernel.

The protocol uses ARM32 Bionic libraries and its own linker. Android capture/audio processes are ARM64. Clear private LD_PRELOAD and LD_LIBRARY_PATH before starting Android child processes. Retain the USB network interface and IPv6 routing; use negotiated service ports.

Preserve authentication, stream setup, encryption, SDK callbacks and force-keyframe feedback. Change the adapter only where required by the new OS or display integration, with a focused test.

## Android and display adaptation

Negotiate before capture. Resolve and validate the selected canvas and safe rectangle, then apply one geometry to Android logical sizing, GPU composition, encoder and touch. Never infer reserved margins from resolution alone or reuse the last vehicle's rectangle. DISPLAY-NEGOTIATION.md describes the pinned SDK ABI and bounds. Stop safely on invalid or changed geometry.

Hidden DisplayManager APIs, SurfaceTexture behavior, permissions and app_process can change between Android versions. Recompile and test the mirror. Preserve latest-frame capture and the bounded two-buffer pipeline; do not introduce an unbounded frame queue.

Deploy capture JAR and JNI encoder together. Keep I420 orientation, buffer capacities, strides and even canvas dimensions consistent. Output uses a 12-byte big-endian header: NAL length, codec-config flag and monotonic capture milliseconds. Preserve Annex B SPS/PPS, one VCL NAL per frame, Baseline H.264, no B frames/lookahead and requested IDRs. Respect negotiated frame rate and the configured ceiling; metadata alone does not prove sustained delivery. x264/libyuv revisions are pinned in third_party/.

Touch translates the negotiated content origin through Android's physical viewport, including logical-size letterboxing. Test corners, both-axis offsets and multitouch. The Android rail is separate from the head unit's reserved area: launcher, overlay and app-frame constraints must agree on its 104-pixel width.

Rebuild framework changes against the target OS. Never transplant services.jar, old smali offsets, vendor APEX binaries or codec binary patches into another Android version. Reapply the source fix where relevant; guard binary corrections by exact input version/hash. Keep hidden-API exceptions scoped to the launcher and preserve native recents/split-screen ownership.

Preserve capture permissions and REMOTE_SUBMIX routing. Audio is 48kHz stereo PCM16 in 960-byte chunks; larger packets can overrun the legacy sender. Keep a valid Android output clock: broken audio routing can cause fast video playback. Vehicle microphone reception is not established.

## Verification and release hygiene

Run geometry, touch, NMEA and GPS-status tests with scripts/build_bridge.sh. Run tests/test_firstboot.py on a POSIX host, DisplayGeometryTest and launcher checks. Exercise adaptive_encoder_test.c and tests/test_adaptive_encoder.py against real x264 output. Run GpuConversionSelfCheck on the Pi against native libyuv before selecting a new capture pair.

Save exact program/configuration hashes and a small rollback set before a live trial. Confirm the device and any erase destination. Test cold start, reconnection, fit, touch, audio, video and PC recovery on a head unit. Report tested hardware and unresolved cases accurately.

Create release userdata from scratch with a program-only allowlist. Never export accounts, passwords, network/Bluetooth profiles, app/browser data, device identifiers, ADB authorizations, logs, screenshots or private signing material. Use your own signing key outside Git. Keep ADB authentication enabled and bundle no authorized host keys. Audit source and image; verify partition hashes and fully decompress/check the release archive before upload.

Respect component licenses, including the Android base's non-commercial components. Resolve kernel-source provenance and binary redistribution scope before making a private staging repo public. Keep public copy short, generic and free of personal test-vehicle details.
