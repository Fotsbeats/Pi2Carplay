# AOSP Car Dialer on the reference Pi image

The Phone app is adapted from Google's AOSP Car Dialer prebuilt, with exact inputs recorded in `third_party/dependency-revisions.json`. This is a non-Automotive Android integration, not an Android Automotive OS port. The existing Pi launcher, framework, kernel, SDK and working duplex audio binaries are retained.

Android's installed `com.android.bluetooth.hfpclient.HfpClientConnectionService` already registers the paired phone as an enabled Telecom phone account. Car Dialer uses that account for dialing, answering, hanging up, hold and DTMF. The separate root audio helper continues to provide CarPlay PCM, mSBC/CVSD transport and app microphone injection. It now also respects Android's system microphone mute, so Telecom's mute control silences the forwarded PCM. The original Calls app remains available as a diagnostic fallback.

## Focused app adaptations

- The Pi has no CarService or VHAL. Car and CarPackageManager injection return null, and the Automotive projection-status listener is not started. This avoids importing an Automotive framework into another OS. Dialpad restriction initialization handles an absent vehicle service.
- Car UI's existing no-CarService fallback uses a real `CarUxRestrictions` Parcelable. The upstream APK includes an SDK stub that throws; the build replaces only that class family with the pinned Apache-2.0 implementation, removing source-retained platform annotations for the public SDK build. No vehicle driving-state signal is available on this image.
- On canvases shorter than 600dp, the app selects its existing alternate dialpad layout, with Call beside the keypad. This prevents the first row being obscured behind the toolbar. Larger canvases retain the upstream type-down layout.
- This image has no cellular dialer role. `car-dialer-setup.sh` explicitly selects the app as Telecom's system and default UI. The calling startup script reapplies this after each boot; a UI setup error does not disable the working audio services. No global role-qualification bypass or fabricated hardware feature is used.
- Hidden APIs are exempted only for `com.android.car.dialer`. Its privileged permissions are narrowly listed. Runtime defaults cover Bluetooth, phone controls, contacts, call history and notifications; SMS permission is not granted. Phonebook-client support is enabled only while this dialer is installed. The paired phone controls access to its contacts/history.
- While its Phone app is installed, the helper reconnects PBAP for the currently connected hands-free phone if Android's phonebook connection policy is allowed. Retries are 30 seconds apart, and Android's denied policy is respected. Phonebook failure does not disable call audio. This also retries after a user enables iPhone Sync Contacts or after a Bluetooth service restart.
- The Pi advertises A/V hands-free (`bluetooth.device.class_of_device=90,4,8`), preserving the original service bits. The stock image advertises smartphone (`90,2,12`); iPhone pairing may then omit contact-sharing controls and PBAP SDP discovery can fail before any download. After changing the class, a cached iPhone bond may need to be forgotten and paired again before enabling Sync Contacts. Phone-side approval remains required. The original class property is saved for rollback.
- The launcher icon retains the AOSP handset artwork with a rounded-square blue background. The builder compiles only this vector and substitutes its pinned APK resource entry; it does not replace the launcher or alter other app icons.

## Phone appearance and ongoing calls

`src/calling-ui/PiPhoneUi.java` gives the upstream views dark surfaces, rounded keypad buttons, a selected-tab highlight, clearer caller typography and labeled in-call controls. Existing view IDs, click handlers and Telecom ownership are retained. The 104-pixel rail, keyboard/PiP framework fixes and audio transport are unchanged.

The upstream app retains its ongoing-call notification. `PiCallBar` adds a floating window while a managed call exists and Phone is in the background. It shows the caller, elapsed time, mute/unmute and End; tapping the information area returns to the full call screen. It hides while Phone is foreground or the keyguard is locked, and removes its window/listeners at call end or service destruction. The bar stays to the right of the rail, uses a compact arrangement on narrow canvases, and retains the notification fallback if minimum control sizes cannot fit. Position is fixed near the top of the display.

The caller label uses the same account-scoped, synced in-memory phonebook as the full app. Saved contacts show their names; unsaved contacts show the formatted number. Restricted or unavailable numbers stay private/unknown. Lookup uses the existing cache and updates as the cache loads; it does not perform a database query on each timer tick or log caller data.

The APK declares `SYSTEM_ALERT_WINDOW`. After installing this opt-in call-bar build, authorize only Phone with `adb shell su 0 cmd appops set com.android.car.dialer SYSTEM_ALERT_WINDOW allow`, or enable Phone under Android's display-over-other-apps settings. Grant this once at installation; do not override a later user denial on every boot. Without overlay authorization, the existing notification/full-screen UI still works. The rollback restores the app-op default and removes the app update.

Lifecycle hooks preserve the original smali parameter before upstream methods reuse it. `scripts/car_dialer_manifest.py` inserts the one manifest permission while preserving existing string/resource indices, rather than reassigning resource IDs. `tests/test_car_dialer_manifest.py` covers both string encodings, insertion order, stable indices, duplicates and malformed input.

## Build

Use the JDK/Android SDK setup in `BUILDING.md`, set `DEX_TOOLS` to a directory with compatible `smali.jar` and `baksmali.jar`, and run:

```sh
python3 scripts/build_car_dialer.py --keystore /path/to/your/private-key.p12
```

`PI2CALL_KEY_PASS` supplies the key password. The builder fetches pinned upstream inputs, rejects hash mismatches, preserves resources and bundled notices, patches only the described methods and signs `build/car-dialer/CarDialerApp.apk`. Keep generated artifacts and keys outside Git. This is an APK adaptation, not a full source build of the upstream dialer or OS.

## Install and rollback

Confirm the intended Pi and save exact working audio/setup hashes before installing. Keep a separate verified working-audio snapshot and the original full calling rollback. Save the current `/vendor/bin/pi2call-setup.sh`, `/data/local/tmp/pi2call/call-audio.jar` and the exact `bluetooth.profile.pbap.client.enabled` value in `/data/local/tmp/pi2call/dialer-rollback/` as `pi2call-setup.sh`, `call-audio.jar` and `pbap-property`. Record the setup and helper hashes in `setup.sha256` using relative filenames.

Install the APK under `/system/priv-app/Pi2CarDialer/CarDialerApp.apk`. Install `car-dialer-permissions.xml` under `/system/etc/permissions/`, `car-dialer-hiddenapi.xml` under `/system/etc/sysconfig/`, and `car-dialer-default-permissions.xml` under `/system/etc/default-permissions/`, each named `pi2call-car-dialer.xml`. Install `car-dialer-setup.sh` under `/vendor/bin/`, alongside the updated calling setup script. Restore ownership, modes, contexts and read-only mounts.

A first install needs a reboot for system-app privileges. On an existing initialized userdata image, grant the runtime permissions listed in the defaults XML once with `pm grant`; new default-permission XML files are not necessarily applied retroactively. Do not repeatedly override user permission decisions on boot. Fresh release userdata must contain only the program allowlist, never synced contacts, call history, Bluetooth profiles or test logs.

Also save the original `bluetooth.device.class_of_device` value as `class-property` in the dialer rollback directory before applying hands-free classification. Subsequent APK updates signed with the same key can use normal package installation, preserving system-app privileges and avoiding a system-partition rewrite. `pm path com.android.car.dialer` then points to its updated APK under `/data/app/`. The rollback removes that system-app update before removing the base APK.

Keep `car-dialer-rollback.sh` at `/data/local/tmp/pi2call/dialer-rollback.sh`, mode 700. After confirming the intended device, run:

```sh
adb shell su 0 sh /data/local/tmp/pi2call/dialer-rollback.sh
adb reboot
```

This removes only the new Phone app/configuration and restores the saved audio helper, startup script and phonebook property. Working native audio binaries and the diagnostic Calls app remain. To remove all experimental calling afterward, use the original calling rollback documented in `CALLING.md`.

## Validation

Compile `tests/CarDialerCompatTest.java` with the existing SDK/D8 toolchain and run its JAR with the adapted APK in `CLASSPATH`. Pass `--installed` to check the installed package resources and exported, permission-protected in-call service registration. It checks the real Parcelable, no-CarService fallback and nullable dependency injection; it places no call.

With Phone open and an empty dialpad, run `tests/test_car_dialer_ui.py` with explicit `ADB` and `ANDROID_SERIAL`. It checks every key and Call button against the window/toolbar bounds, enters three test digits and clears them without calling.

`CarDialerIconTest.java` renders the installed package icon and checks the new square shoulders and transparent rounded corners. It can save a generated icon preview; it captures no screen or contact data.

`CarCallerInfoTest.java` checks saved-name lookup against imported contacts and verifies unsaved-number, private and unavailable-number fallbacks. It prints only check counts. Its root `app_process` runner uses the external-provider API (as the `adb content` command does), because that process has no registered application thread. The production app uses its normal content-provider lifecycle.

On the reference device, app launch, default/system UI selection, paired-phone detection, Bluetooth contacts/history download, populated Contacts UI, compact keypad fit/input, compatibility checks, rounded-square icon rendering and in-call service initialization pass. Hands-free classification exposed the iPhone Sync Contacts control after rediscovery. Real outgoing and incoming calls through this UI, notification presentation, contacts search and broader boot/reconnection behavior remain vehicle-test items. The preceding audio-only candidate passed speech-quality, handset-return and Discord microphone trials; that does not by itself qualify this new UI.
