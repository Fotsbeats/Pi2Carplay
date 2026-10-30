# Experimental Bluetooth calling

This candidate targets the reference Raspberry Pi 5 / KonstaKANG Android 16 installation. Following the SDK microphone correction and wideband revision, a vehicle trial reported normal microphone quality and working Discord input. Logs confirm mSBC negotiation, return from handset audio, and independent app microphone/playback demand. Some concealed Bluetooth frame losses and an HFP service reconnection were recorded; broader cold-start, incoming-call and reconnection testing remains. It is not a qualified release feature.

The phone connects to Android's existing HFP hands-free client. Its connection policy is saved by Android; pairing data is retained. The helper reconnects the single phone with an allowed HFP policy, with retries 30 seconds apart. If several phones have allowed policies, choose one in Calls. `Calls` provides phone selection, dialing, answer, end/reject and mute. It does not place a call automatically or copy contacts.

The optional [AOSP Car Dialer integration](CAR-DIALER.md) adds a full Phone UI through the existing HFP Telecom account and Bluetooth phonebook support. Calls remains a diagnostic fallback. Roll back the dialer separately before applying the full calling rollback below.

## Audio path

- `call_hci.c` loads alongside the installation's original Bluetooth HAL. It leaves controller initialization, ACL/media traffic and HFP signalling in Android. Only SCO setup for the selected phone changes the Broadcom controller's PCM routing to HCI. The vendor command's completion is consumed before the original setup command is forwarded. HFP advertises mSBC wideband negotiation, retaining CVSD fallback. Transparent mSBC HCI packets pass through bounded H2 reassembly and the pinned BlueZ codec; PCM remains 16 kHz. CVSD uses 8 kHz PCM and the system Speex resampler. The Calls status and helper log identify the negotiated codec.
- The root `CallAudio` helper exchanges bounded PCM messages with this transport. Android's audio policy API injects vehicle PCM into microphone recording streams, and captures voice communication playback for the duplex CarPlay stream. Voice playback uses loopback alone: duplicating it to the primary output introduced discontinuities between independent output clocks in synthetic on-Pi playback checks. Recording and voice-playback callbacks request this stream independently of Bluetooth calls, so app microphone access also works after a call. REMOTE_SUBMIX remains outside the microphone rule. No framework, audio-policy XML, kernel, USB identity or SDK binary is replaced.
- The bridge changes its SDK audio stream from 48 kHz stereo media to a duplex 16 kHz mono telephony stream while Bluetooth or app voice audio needs it. Switching an ongoing phone call to its handset keeps the negotiated CarPlay stream available until the call ends, allowing a return to Pi audio without tearing down and reopening the SDK stream. Media capture is drained during voice audio. Hang-up with no app demand, or helper disconnect, stops telephony and restores media. SDK payloads remain at most 960 bytes.
- Voice playback primes 20 ms of PCM in a bounded 60 ms buffer and submits 10 ms packets on a monotonic clock. Producer or scheduler bursts cannot trigger accelerated catch-up playback. End-of-call counters report paced packets, underruns and discarded PCM bytes. Real vehicle validation of this revision remains pending.
- The microphone opens for call or app demand, rather than a boot-time diagnostic. PCM is streamed in memory and is never saved. Logs contain demand transitions, negotiated codec and packet/loss counters. The Calls control socket accepts only root and the installed Calls package UID; transport sockets check peer credentials.

The pinned SDK's `GetRTPJitterBufferPreparedLen` reports sample frames, while `RTPJitterBufferRead` accepts bytes. Its convenience microphone wrapper mixes those units and can report odd byte lengths for valid PCM16. The adapter uses the two primitives directly, validates the two-byte mono frame stride and converts frames to bounded, even byte lengths. The pinned stream's jitter object is at offset `0x110`; the jitter output frame stride is at `0x7c`. Both `libSdBridge.so` and its `libAirPlay.so` provider must match the recorded target hashes. No SDK binary is patched.

The first vehicle trial accepted telephony setup but the old adapter rejected a microphone read and returned audio to the phone. The frame/byte correction passed the real-SDK synthetic check and enabled two-way speech in the next trial. That trial still used narrowband Bluetooth. Switching to the handset then closed telephony and failed to reopen the SDK media port, leaving the CarPlay audio endpoint unavailable. Holding telephony through an ongoing call avoids that transition; recovery and speech quality remain car-test items.

CarPlay must be connected before dialing or answering through Calls. With no vehicle connection, the helper disallows HFP audio routing and returns an unexpected SCO connection to the phone. This candidate supports one phone and no simultaneous Bluetooth headset. It does not implement contacts, call history, DTMF controls or automatic incoming-call popups.

## Build and installation

Use the toolchain in [BUILDING.md](../../BUILDING.md). Set `SBC_SOURCE` to the extracted, pinned BlueZ sbc 2.1 source directory, then build the native components with `sh scripts/build_calling.sh` and the bridge with `sh scripts/build_bridge.sh`. Preserve `third_party/licenses/sbc-COPYING.LIB` with installed calling programs and keep the corresponding source available for redistribution. `ANDROID_CC` can select an existing ARM64 NDK compiler wrapper; otherwise the native build uses `ANDROID_NDK_ROOT`. Build/sign the helper and Calls APK with `python3 scripts/build_calling.py --keystore /path/to/your/key.p12`. Set `PI2CALL_KEY_PASS` to supply the signing password without putting it in command arguments.

Installation is a board-specific development operation, not a generic APK update. First save and hash the installed bridge executable and wrapper, and hash the working framework, landscape policy, capture pair, SDK, Bluetooth HAL, system Speex library and audio policies. Keep the two bridge originals under `/data/local/tmp/pi2call/rollback/`, with relative-name checksums in `rollback.sha256`. Save the original `bluetooth.profile.hfp.hf.enabled` and `bluetooth.hfp.hf_client_features.config` property values as `hf-property` and `features-property` there. An empty value must remain empty.

Install these program files, keeping ordinary files root-owned and readable and scripts executable:

| Build/source | Device path |
| --- | --- |
| `build/calling/libpi2call_hci.so` | `/system/lib64/libpi2call_hci.so` |
| `build/calling/libpi2call_resample.so` | `/data/local/tmp/pi2call/libpi2call_resample.so` |
| `build/calling/call-audio.jar` | `/data/local/tmp/pi2call/call-audio.jar` |
| `build/calling/calls.apk` | `/system/app/Pi2Call/Calls.apk` |
| `build/handshake-probe` | `/data/local/tmp/pi5-protocol/bin/handshake-probe` |
| `src/bridge/aibox-handshake.sh` | `/vendor/bin/aibox-handshake.sh` |
| `src/android/pi2call.rc` | `/vendor/etc/init/pi2call.rc` |
| `src/android/pi2call-setup.sh` | `/vendor/bin/pi2call-setup.sh` |
| `src/android/pi2call-audio.sh` | `/vendor/bin/pi2call-audio.sh` |
| `src/android/pi2call-rollback.sh` | `/data/local/tmp/pi2call/rollback.sh` |

Generate `/data/local/tmp/pi2call/target.sha256` from the exact installed Bluetooth HAL, SDK `libSdBridge.so` and `libAirPlay.so`, and system `/system/lib64/libspeexresampler.so`, using their absolute device paths. Startup checks these hashes and reverts to stock Bluetooth on a mismatch. Use mode 700 for the rollback script/directory and 600 for its records. Restore file contexts and read-only system/vendor mounts after installation. The HAL preload belongs in `/system/lib64` because its APEX linker namespace excludes `/vendor/lib64`. The Java JNI library belongs under `/data` because the app_process classloader excludes `/vendor/lib64`.

Enable `persist.vendor.pi2call.enabled=1` and reboot to register the new init services and system app. Setup refreshes Android's cached Bluetooth profile list without clearing pairings. It restores stock Bluetooth if either service exits or the startup readiness check fails. Stock Bluetooth's own controller startup is not modified.

## Checks and car trial

Host checks cover HCI initialization passthrough, selected-phone routing, transparent enhanced-SCO setup, PCM bounds/silence, mSBC wideband tone roundtrip, fragmented H2 frames, loss/duplicates/resynchronization, peer validation, call/media switching and recovery from rejected or malformed microphone streams. Run `CallDemandTest` with assertions for handset return and app-only demand. The same native checks are built as `build/calling/test-call-hci-android` for the Pi. Existing geometry, touch, NMEA, GPS and first-boot checks remain applicable.

`tests/CallPlaybackTest.java` checks generated stereo tones at 16, 44.1 and 48 kHz through the helper's actual voice capture policy on the Pi. It checks the 16 kHz mono PCM clock, tone frequency, discontinuities and concurrent 48 kHz stereo REMOTE_SUBMIX clock without opening a microphone or saving audio. Stop the live helper while running this test so only one policy is registered.

On the Pi, stop the running helper before running a second audio-policy registration. Run the helper with `--resampler-check` for system resampling and `--mic-check` for generated PCM injection into Android MIC and VOICE_COMMUNICATION readers, plus voice playback capture. These checks use synthetic samples, create no recording file and do not request the vehicle microphone. `--status` reports the live helper state. Clear `LD_PRELOAD` and `LD_LIBRARY_PATH` and set `CLASSPATH=/data/local/tmp/pi2call/call-audio.jar` when running `app_process / org.bench.pi2call.CallAudio`.

`build/bridge/test-call-sdk`, built by `build_bridge.sh`, reproduces the SDK wrapper's unit error and verifies corrected reads byte-for-byte using generated PCM in its real jitter buffer. Run it with the private ARM32 linker and `LD_LIBRARY_PATH` pointing only to the pinned protocol library directory; clear `LD_PRELOAD`. It opens neither a CarPlay session nor a microphone.

In the car, open Calls and select the paired phone if it has not reconnected. Confirm `CarPlay connected`, then make a non-emergency test call. Check the wideband/narrowband label, both speech directions and clarity, echo, mute, switching to the handset and back, and hang-up followed by music. Then exercise an app voice channel after the call, including its microphone and received voice. Also check an incoming call, reconnection and PC recovery before treating the feature as qualified.

Helper logs are `/data/local/tmp/pi2call/audio.log` and `startup.log`; native controller logs use the `Pi2CallHci` logcat tag. The current boot's `pi5-handshake` wrapper log reports telephony setup, first microphone PCM and playback/microphone packet counters. `Call audio active` means the stream setup completed, not that intelligible vehicle speech has been verified.

Full rollback, after confirming the intended Pi:

```sh
adb shell su 0 sh /data/local/tmp/pi2call/rollback.sh
adb reboot
```

This restores the saved bridge executable/wrapper and original HFP properties, removes the calling additions and retains the rollback records. It does not restore or overwrite the working PiP, keyboard, launcher, capture, SDK or audio-policy components. Keep logs, screenshots, phone data and rollback records out of release userdata; create release userdata from the program-only allowlist only after vehicle validation.
