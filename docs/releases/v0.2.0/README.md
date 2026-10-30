# v0.2.0

Bluetooth calling, vehicle microphone support and live video controls for the Raspberry Pi 5 Android 16 image.

**Boot-image correction:** the original v0.2.0 download omitted the extended-partition tables, preventing Android from locating system/vendor. Download the replacement ending in **`-bootfix.img.gz`**. It restores the complete partition chain; all filesystem and program bytes are unchanged. Structural checks and archive verification pass; a fresh hardware boot of the replacement still needs testing.

## Changes since v0.1.0

- Added Bluetooth hands-free calls through the paired phone, with two-way audio over CarPlay. Supports mSBC wideband speech with CVSD fallback, and routes the vehicle microphone into Android recording apps.
- Added an adapted AOSP Phone app with contacts, call history, dialpad and call controls. Hands-free Bluetooth identification enables iPhone Sync Contacts; the phone owner must approve sharing. Phonebook reconnects when permitted.
- Added a floating ongoing-call bar with contact name or number, timer, mute and hang-up controls. Tap it to return to Phone.
- Updated Phone with dark surfaces, rounded controls, a compact landscape dialpad and a rounded-square icon sized consistently in Home and Apps.
- Improved call-audio pacing and buffering, removed a duplicate playback route that caused discontinuities, and corrected microphone conversion and stream lifecycle handling.
- Fixed picture-in-picture clearance and keyboard layout around the persistent 104-pixel sidebar. Retained native Android Recents and split screen; deployment checks now verify the navigation service as well as Home's local sidebar.
- Added live 4/8 Mbps and 30/60 FPS controls to Home settings. FPS controls both Android rendering and capture/encoding, within the receiver's negotiated limit. Changes require no reboot. Clean images default to **4 Mbps / 30 FPS**.
- Cached video-conversion shader locations. FPS changes send fresh H.264 configuration and a keyframe while retaining the USB session, negotiated geometry and audio.
- Polished Home settings with rounded glass surfaces and clearer controls, including scrolling on smaller displays.
- Added concise Google custom-ROM registration and stale compliance-cache recovery instructions in the main README. Each owner registers their own device; registration does not confer official hardware certification.

## Clean-image setup

The image uses fresh userdata built from a program-only allowlist. It includes no personal accounts, saved Wi-Fi networks, Bluetooth pairings, contacts, call history, browser/app data, device identifiers, authorized ADB hosts, test logs or private signing keys. Configure your own Wi-Fi, Google account and phone pairing after flashing. ADB requires host authorization.

First setup enables dashboard navigation and Phone's floating-call permission. Subsequent boots retain the owner's choices. Existing test-device data and the previous release are retained separately.

## Validation and remaining tests

On-device checks verified all four video combinations within one capture process, Android's actual 30/60 FPS scheduling, GPU conversion, H.264 configuration/keyframe transitions and synthetic audio clocks. The persistent sidebar, Recents and two-app split screen were checked; navigation remained enabled after restart. Earlier vehicle trials confirmed normal microphone quality and Android app microphone access.

The user reported that the latest combined build passed the car test. A fresh-image first boot on hardware remains untested. No performance claim is made for sustained 60 FPS at every resolution. Broader vehicle compatibility and occasional restart/power-on behavior remain unqualified.

Image assembly, the exact userdata allowlist, the privacy audit and full archive decompression checks passed. See the accompanying image manifest, privacy audit, source checks and SHA256SUMS.

Download `Pi2Carplay-v0.2.0-bootfix.img.gz` and flash it with Raspberry Pi Imager using the main README. The release also includes the pinned x264, libyuv and BlueZ sbc sources in `Pi2Carplay-v0.2.0-codec-sources.tar.gz`. Project adapters, build scripts and the complete patched kernel source are in this release's Git source archive. The packaging correction and partition-table regression test are on main. Preserve all component notices. The Android base includes non-commercial components; see LICENSE-SCOPE.md and third_party/.
